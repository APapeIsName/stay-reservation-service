package com.stay.application.reservation

import com.stay.application.support.DomainFixtures
import com.stay.domain.dailyroom.DailyRoomId
import com.stay.domain.property.BedConfiguration
import com.stay.domain.property.BedEntry
import com.stay.domain.property.BedType
import com.stay.domain.property.ViewType
import com.stay.infrastructure.dailyroom.DailyRoomJpaRepository
import com.stay.infrastructure.property.PropertyJpaRepository
import com.stay.infrastructure.reservation.ReservationJpaRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import com.stay.testcontainers.MySqlTestContainersConfig
import com.stay.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 동시성 — 더블부킹 방지 (재고 비관락, ADR-004 §1 / B.5 CC-01~02).
 * 같은 객실·일자에 다수 예약이 동시에 들어와도 `findForReserve`(`SELECT ... FOR UPDATE` + 날짜 ASC)가
 * 트랜잭션을 직렬화해 잔여 수량만큼만 성공시키고 나머지는 CONFLICT 로 거절한다.
 *
 * 환경 주의: Testcontainers + Docker. 로컬 Docker Desktop 29.x 호환 이슈로 실행이 차단될 수 있음
 * (memory: testcontainers-docker-desktop-incompat). 컴파일·코드 검증까지 — 실 동시성 검증은 CI/Docker 환경.
 */
@Tag("integration")
@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class ReservationConcurrencyTest
    @Autowired
    constructor(
        private val sut: ReservationService,
        private val propertyJpaRepository: PropertyJpaRepository,
        private val dailyRoomJpaRepository: DailyRoomJpaRepository,
        private val reservationJpaRepository: ReservationJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {

        @AfterEach
        fun cleanUp() {
            databaseCleanUp.truncateAllTables()
        }

        private fun seedPropertyWithRoomType(): Pair<Long, Long> {
            val property = DomainFixtures.newProperty()
            property.addRoomType(
                name = "디럭스 더블",
                standardGuestCount = 2,
                maxGuestCount = 2,
                bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1))),
                sizeSqm = 26,
                viewType = ViewType.OCEAN,
            )
            val saved = propertyJpaRepository.save(property)
            return saved.id to saved.roomTypes.single().id
        }

        private fun seedDailyRooms(roomTypeId: Long, totalRooms: Int) {
            dailyRoomJpaRepository.saveAll(
                listOf(
                    DomainFixtures.dailyRoom(roomTypeId, "2026-07-01", totalRooms = totalRooms, pricePerNight = 100000L),
                    DomainFixtures.dailyRoom(roomTypeId, "2026-07-02", totalRooms = totalRooms, pricePerNight = 120000L),
                ),
            )
        }

        private fun reserveCommand(propertyId: Long, roomTypeId: Long) =
            ReserveCommand(
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                checkIn = LocalDate.parse("2026-07-01"),
                checkOut = LocalDate.parse("2026-07-03"),
                guestCount = 2,
                guestName = "공명선",
                guestPhone = "010-1234-5678",
            )

        private fun reservedRoomsOf(roomTypeId: Long, date: String): Int =
            dailyRoomJpaRepository
                .findById(DailyRoomId(roomTypeId, LocalDate.parse(date)))
                .orElseThrow()
                .reservedRooms

        @DisplayName("CC-01: 같은 객실·일자에 10명 동시 예약 → 정확히 1건만 성공 (더블부킹 0)")
        @Test
        fun preventsDoubleBooking_underConcurrency() {
            val (propertyId, roomTypeId) = seedPropertyWithRoomType()
            seedDailyRooms(roomTypeId, totalRooms = 1)

            val threads = 10
            val latch = CountDownLatch(threads)
            val executor = Executors.newFixedThreadPool(threads)
            val success = AtomicInteger(0)
            val conflict = AtomicInteger(0)

            repeat(threads) {
                executor.submit {
                    try {
                        sut.reserve(userId = 1L, command = reserveCommand(propertyId, roomTypeId))
                        success.incrementAndGet()
                    } catch (e: CoreException) {
                        if (e.errorType == ErrorType.CONFLICT) {
                            conflict.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
            executor.shutdown()

            assertThat(success.get()).isEqualTo(1)
            assertThat(conflict.get()).isEqualTo(threads - 1)
            assertThat(reservedRoomsOf(roomTypeId, "2026-07-01")).isEqualTo(1)
            assertThat(reservedRoomsOf(roomTypeId, "2026-07-02")).isEqualTo(1)
            assertThat(reservationJpaRepository.count()).isEqualTo(1)
        }
    }
