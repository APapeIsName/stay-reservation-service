package com.stay.application.reservation

import com.stay.application.support.DomainFixtures
import com.stay.domain.dailyroom.DailyRoomId
import com.stay.domain.property.BedConfiguration
import com.stay.domain.property.BedEntry
import com.stay.domain.property.BedType
import com.stay.domain.property.RefundRule
import com.stay.domain.property.ViewType
import com.stay.domain.reservation.DailyPriceEntry
import com.stay.domain.reservation.ReservationStatus
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
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * 예약 생성·취소 유스케이스 영속 검증 — 스냅샷 2종 (reservation_price_entry · reservation_refund_rule) 보존,
 * daily_room reserved_rooms 차감/복원, 재고 부족 시 저장 0 (선검증이 차감·저장보다 먼저).
 *
 *  - 시드 id 는 JPA IDENTITY 부여 — register/addRoomType 후 save 반환 객체에서 회수
 *  - 환불액은 실행 시점(실 Clock) 의존이라 단언하지 않음 — 상태 전이·재고 복원만 검증
 *  - 스냅샷 컬렉션은 LAZY — 재조회 단언은 TransactionTemplate 경계 안에서 수행
 *
 * 환경 주의: Testcontainers + Docker. 로컬 Docker Desktop 29.x 호환 이슈로 실행이 실패할 수 있음
 * (memory: testcontainers-docker-desktop-incompat). 환경 정상화 시점에 자동 검증 가능.
 */
@Tag("integration")
@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class ReservationServiceIntegrationTest
    @Autowired
    constructor(
        private val sut: ReservationService,
        private val propertyJpaRepository: PropertyJpaRepository,
        private val dailyRoomJpaRepository: DailyRoomJpaRepository,
        private val reservationJpaRepository: ReservationJpaRepository,
        private val transactionTemplate: TransactionTemplate,
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

        private fun seedDailyRooms(roomTypeId: Long, totalRooms: Int = 5, reservedRooms: Int = 0) {
            dailyRoomJpaRepository.saveAll(
                listOf(
                    DomainFixtures.dailyRoom(
                        roomTypeId = roomTypeId,
                        date = "2026-07-01",
                        totalRooms = totalRooms,
                        reservedRooms = reservedRooms,
                        pricePerNight = 100000L,
                    ),
                    DomainFixtures.dailyRoom(
                        roomTypeId = roomTypeId,
                        date = "2026-07-02",
                        totalRooms = totalRooms,
                        reservedRooms = reservedRooms,
                        pricePerNight = 120000L,
                    ),
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

        @Nested
        @DisplayName("예약 생성 — reserve 영속")
        inner class Reserve {

            @DisplayName("INT-RSV-01: reserve 시 reservation 1건 + 요금·취소정책 스냅샷 보존 + reserved_rooms 차감 영속")
            @Test
            fun savesReservation_withSnapshots_andConsumesInventory() {
                val (propertyId, roomTypeId) = seedPropertyWithRoomType()
                seedDailyRooms(roomTypeId)

                val info = sut.reserve(userId = 1L, command = reserveCommand(propertyId, roomTypeId))

                assertThat(info.reservationId).isPositive()
                assertThat(info.status).isEqualTo(ReservationStatus.CONFIRMED)
                assertThat(info.totalPrice).isEqualTo(220000L)
                assertThat(reservationJpaRepository.count()).isEqualTo(1L)
                transactionTemplate.executeWithoutResult {
                    val found = reservationJpaRepository.findById(info.reservationId).orElseThrow()
                    assertThat(found.priceSnapshot.entries).containsExactly(
                        DailyPriceEntry(LocalDate.parse("2026-07-01"), 100000L),
                        DailyPriceEntry(LocalDate.parse("2026-07-02"), 120000L),
                    )
                    assertThat(found.cancellationPolicySnapshot.rules)
                        .containsExactlyInAnyOrder(RefundRule(7, 100), RefundRule(3, 50), RefundRule(0, 0))
                }
                assertThat(reservedRoomsOf(roomTypeId, "2026-07-01")).isEqualTo(1)
                assertThat(reservedRoomsOf(roomTypeId, "2026-07-02")).isEqualTo(1)
            }
        }

        @Nested
        @DisplayName("예약 취소 — cancel 영속")
        inner class Cancel {

            @DisplayName("INT-RSV-02: cancel 시 status CANCELLED 영속 + 전 일자 reserved_rooms 복원")
            @Test
            fun persistsCancelledStatus_andReleasesInventory() {
                val (propertyId, roomTypeId) = seedPropertyWithRoomType()
                seedDailyRooms(roomTypeId)
                val info = sut.reserve(1L, reserveCommand(propertyId, roomTypeId))

                val cancellation = sut.cancel(userId = 1L, reservationId = info.reservationId)

                assertThat(cancellation.status).isEqualTo(ReservationStatus.CANCELLED)
                val found = reservationJpaRepository.findById(info.reservationId).orElseThrow()
                assertThat(found.status).isEqualTo(ReservationStatus.CANCELLED)
                assertThat(reservedRoomsOf(roomTypeId, "2026-07-01")).isZero()
                assertThat(reservedRoomsOf(roomTypeId, "2026-07-02")).isZero()
            }
        }

        @Nested
        @DisplayName("실패 — 재고 부족 시 저장 0")
        inner class InsufficientInventory {

            @DisplayName("INT-RSV-03: 만실 일자 포함 reserve 는 CONFLICT — reservation 0건, 재고 불변")
            @Test
            fun throwsConflict_andPersistsNothing_whenSoldOut() {
                val (propertyId, roomTypeId) = seedPropertyWithRoomType()
                seedDailyRooms(roomTypeId, totalRooms = 1, reservedRooms = 1)

                val thrown =
                    assertThrows<CoreException> {
                        sut.reserve(1L, reserveCommand(propertyId, roomTypeId))
                    }

                assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
                assertThat(reservationJpaRepository.count()).isZero()
                assertThat(reservedRoomsOf(roomTypeId, "2026-07-01")).isEqualTo(1)
                assertThat(reservedRoomsOf(roomTypeId, "2026-07-02")).isEqualTo(1)
            }
        }
    }
