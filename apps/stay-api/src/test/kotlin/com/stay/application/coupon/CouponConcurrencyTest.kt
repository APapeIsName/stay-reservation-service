package com.stay.application.coupon

import com.stay.application.reservation.ReservationService
import com.stay.application.reservation.ReserveCommand
import com.stay.application.support.DomainFixtures
import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponIssue
import com.stay.domain.coupon.CouponIssueStatus
import com.stay.domain.coupon.CouponType
import com.stay.domain.property.BedConfiguration
import com.stay.domain.property.BedEntry
import com.stay.domain.property.BedType
import com.stay.domain.property.ViewType
import com.stay.infrastructure.coupon.CouponIssueJpaRepository
import com.stay.infrastructure.coupon.CouponJpaRepository
import com.stay.infrastructure.dailyroom.DailyRoomJpaRepository
import com.stay.infrastructure.property.PropertyJpaRepository
import com.stay.infrastructure.reservation.ReservationJpaRepository
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
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 동시성 — 쿠폰 단일사용 (낙관락 @Version, ADR-004 §2 / B.5 CC-08).
 * 같은 발급 쿠폰으로 여러 예약이 동시에 들어와도, CouponIssue.version 낙관락이 단 한 트랜잭션만
 * markUsed 를 커밋시키고 나머지는 OptimisticLockingFailureException(동시) 또는 CONFLICT(순차)로 실패한다.
 * 재고는 충분히 두어 쿠폰 락만 단독 검증한다.
 *
 * 환경 주의: Testcontainers + Docker. 로컬 Docker Desktop 29.x 호환 이슈로 실행이 차단될 수 있음
 * (memory: testcontainers-docker-desktop-incompat). 컴파일·코드 검증까지 — 실 동시성은 CI/Docker.
 */
@Tag("integration")
@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class CouponConcurrencyTest
    @Autowired
    constructor(
        private val sut: ReservationService,
        private val propertyJpaRepository: PropertyJpaRepository,
        private val dailyRoomJpaRepository: DailyRoomJpaRepository,
        private val reservationJpaRepository: ReservationJpaRepository,
        private val couponJpaRepository: CouponJpaRepository,
        private val couponIssueJpaRepository: CouponIssueJpaRepository,
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

        private fun reserveCommand(propertyId: Long, roomTypeId: Long, couponId: Long) =
            ReserveCommand(
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                checkIn = LocalDate.parse("2026-07-01"),
                checkOut = LocalDate.parse("2026-07-03"),
                guestCount = 2,
                guestName = "공명선",
                guestPhone = "010-1234-5678",
                couponId = couponId,
            )

        @DisplayName("CC-08: 같은 발급 쿠폰으로 2건 동시 예약 → 쿠폰 1회만 USED (낙관락 @Version)")
        @Test
        fun couponUsedOnce_underConcurrency() {
            val (propertyId, roomTypeId) = seedPropertyWithRoomType()
            seedDailyRooms(roomTypeId, totalRooms = 10)
            val coupon = couponJpaRepository.save(
                Coupon(
                    name = "여름 5만원",
                    type = CouponType.FIXED,
                    value = 50000L,
                    minOrderAmount = null,
                    expiredAt = LocalDateTime.parse("2030-12-31T23:59:00"),
                ),
            )
            val issueId = couponIssueJpaRepository.save(
                CouponIssue.issue(coupon.id, 1L, LocalDateTime.parse("2026-06-17T10:00:00")),
            ).id

            val threads = 2
            val latch = CountDownLatch(threads)
            val executor = Executors.newFixedThreadPool(threads)
            val success = AtomicInteger(0)
            val failure = AtomicInteger(0)

            repeat(threads) {
                executor.submit {
                    try {
                        sut.reserve(userId = 1L, command = reserveCommand(propertyId, roomTypeId, couponId = issueId))
                        success.incrementAndGet()
                    } catch (e: Exception) {
                        failure.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
            executor.shutdown()

            assertThat(success.get()).isEqualTo(1)
            assertThat(failure.get()).isEqualTo(1)
            assertThat(couponIssueJpaRepository.findById(issueId).get().status).isEqualTo(CouponIssueStatus.USED)
            assertThat(reservationJpaRepository.count()).isEqualTo(1)
        }
    }
