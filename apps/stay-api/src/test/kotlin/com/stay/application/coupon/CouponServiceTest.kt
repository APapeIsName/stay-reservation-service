package com.stay.application.coupon

import com.stay.application.support.FakeCouponIssueRepository
import com.stay.application.support.FakeCouponRepository
import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponIssue
import com.stay.domain.coupon.CouponType
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 카탈로그: docs/round-4/02-tdd-plan.md  B.4 CouponService.issue (CSVC-01~06)
 * 규칙: 발급 = Coupon 존재 확인(NOT_FOUND) → CouponIssue.issue → save → CouponInfo.
 *   specGap 확정: C-6 복수 발급 허용 / C-7 발급 시점 만료 검사 안 함(사용 시 거절).
 *   Fake: seed(id, coupon) 로 알려진 id 적재 (FakeReservationRepository 동형, 도메인 id 0L).
 */
@Tag("slow-unit")
class CouponServiceTest {

    companion object {
        private val NOW: LocalDateTime = LocalDateTime.parse("2026-06-17T10:00:00")
        private val FIXED_CLOCK: Clock = Clock.fixed(
            NOW.atZone(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul"),
        )

        private fun coupon(
            name: String = "여름 5천원",
            type: CouponType = CouponType.FIXED,
            value: Long = 5000L,
            minOrderAmount: Long? = null,
            expiredAt: LocalDateTime = LocalDateTime.parse("2026-12-31T23:59:00"),
        ) = Coupon(name = name, type = type, value = value, minOrderAmount = minOrderAmount, expiredAt = expiredAt)
    }

    private fun service(
        couponRepository: FakeCouponRepository,
        couponIssueRepository: FakeCouponIssueRepository,
    ) = CouponService(couponRepository, couponIssueRepository, FIXED_CLOCK)

    @Nested
    @DisplayName("발급 — issue")
    inner class Issue {

        @DisplayName("CSVC-01: 존재하는 쿠폰 발급 → CouponInfo 반환 + 발급분 1건 저장")
        @Test
        fun issuesCoupon() {
            val couponRepo = FakeCouponRepository().apply { seed(10L, coupon(name = "여름 5천원")) }
            val issueRepo = FakeCouponIssueRepository()

            val info = service(couponRepo, issueRepo).issue(IssueCommand(couponId = 10L, userId = 1L))

            assertThat(info.couponId).isEqualTo(10L)
            assertThat(info.name).isEqualTo("여름 5천원")
            assertThat(info.status).isEqualTo("AVAILABLE")
            assertThat(info.issuedAt).isEqualTo(NOW)
            assertThat(issueRepo.count).isEqualTo(1)
        }

        @DisplayName("CSVC-02: 미존재 쿠폰 발급 → NOT_FOUND, 저장 0건")
        @Test
        fun rejectsUnknownCoupon() {
            val issueRepo = FakeCouponIssueRepository()

            val thrown = assertThrows<CoreException> {
                service(FakeCouponRepository(), issueRepo).issue(IssueCommand(couponId = 999L, userId = 1L))
            }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(issueRepo.count).isEqualTo(0)
        }

        @DisplayName("CSVC-03: 같은 유저 같은 템플릿 2회 발급 → 2건 (복수 허용, C-6)")
        @Test
        fun allowsMultipleIssues() {
            val couponRepo = FakeCouponRepository().apply { seed(10L, coupon()) }
            val issueRepo = FakeCouponIssueRepository()
            val sut = service(couponRepo, issueRepo)

            sut.issue(IssueCommand(couponId = 10L, userId = 1L))
            sut.issue(IssueCommand(couponId = 10L, userId = 1L))

            assertThat(issueRepo.count).isEqualTo(2)
        }

        @DisplayName("CSVC-04: 만료된 쿠폰 발급 → 허용 (발급 시 만료검사 안 함, C-7)")
        @Test
        fun allowsExpiredCouponIssue() {
            val couponRepo = FakeCouponRepository().apply {
                seed(10L, coupon(expiredAt = LocalDateTime.parse("2026-06-16T23:59:00")))
            }
            val issueRepo = FakeCouponIssueRepository()

            val info = service(couponRepo, issueRepo).issue(IssueCommand(couponId = 10L, userId = 1L))

            assertThat(info.couponId).isEqualTo(10L)
            assertThat(issueRepo.count).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("DTO — IssueCommand / CouponInfo")
    inner class Dto {

        @DisplayName("CSVC-05: IssueCommand 가 couponId·userId 를 보유한다")
        @Test
        fun issueCommandHoldsValues() {
            val command = IssueCommand(couponId = 10L, userId = 1L)
            assertThat(command.couponId).isEqualTo(10L)
            assertThat(command.userId).isEqualTo(1L)
        }

        @DisplayName("CSVC-06: CouponInfo.from 은 도메인 객체 미노출·원시 필드만 (rule 20)")
        @Test
        fun couponInfoFlattens() {
            val info = CouponInfo.from(
                CouponIssue.issue(couponId = 10L, userId = 1L, now = NOW),
                coupon(type = CouponType.FIXED, value = 5000L),
            )
            assertThat(info.type).isEqualTo("FIXED")
            assertThat(info.value).isEqualTo(5000L)
            assertThat(info.couponId).isEqualTo(10L)
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록 — myCoupons (상태 파생)")
    inner class MyCoupons {

        private val notExpired = LocalDateTime.parse("2026-12-31T23:59:00")
        private val alreadyExpired = LocalDateTime.parse("2026-06-16T23:59:00")

        private fun availableIssue(couponId: Long, userId: Long = 1L) =
            CouponIssue.issue(couponId, userId, NOW)

        private fun usedIssue(couponId: Long, userId: Long = 1L, usedAt: LocalDateTime = NOW) =
            availableIssue(couponId, userId).apply { markUsed(usedAt, notExpired) }

        @DisplayName("CSVC-07: AVAILABLE·USED·만료 3건 → status 파생 [AVAILABLE, USED, EXPIRED]")
        @Test
        fun derivesStatuses() {
            val couponRepo = FakeCouponRepository().apply {
                seed(1L, coupon(name = "C1", expiredAt = notExpired))
                seed(2L, coupon(name = "C2", expiredAt = notExpired))
                seed(3L, coupon(name = "C3", expiredAt = alreadyExpired))
            }
            val issueRepo = FakeCouponIssueRepository().apply {
                save(availableIssue(1L))
                save(usedIssue(2L))
                save(availableIssue(3L))
            }

            val result = service(couponRepo, issueRepo).myCoupons(userId = 1L, page = 0, size = 10)

            assertThat(result.map { it.status }).containsExactly("AVAILABLE", "USED", "EXPIRED")
        }

        @DisplayName("CSVC-08: 발급분 없는 유저 → 빈 목록")
        @Test
        fun emptyForUserWithNone() {
            val result = service(FakeCouponRepository(), FakeCouponIssueRepository())
                .myCoupons(userId = 2L, page = 0, size = 10)

            assertThat(result).isEmpty()
        }

        @DisplayName("CSVC-09: AVAILABLE·만료 → EXPIRED 파생 (저장은 AVAILABLE, CIS-11 정합)")
        @Test
        fun expiredDerivation() {
            val couponRepo = FakeCouponRepository().apply { seed(3L, coupon(expiredAt = alreadyExpired)) }
            val issueRepo = FakeCouponIssueRepository().apply { save(availableIssue(3L)) }

            val result = service(couponRepo, issueRepo).myCoupons(userId = 1L, page = 0, size = 10)

            assertThat(result.single().status).isEqualTo("EXPIRED")
        }

        @DisplayName("CSVC-10: 5건 size=2 → page0 2 / page1 2 / page2 1 (마지막 초과 빈 목록)")
        @Test
        fun paginates() {
            val couponRepo = FakeCouponRepository().apply { seed(1L, coupon()) }
            val issueRepo = FakeCouponIssueRepository().apply { repeat(5) { save(availableIssue(1L)) } }
            val sut = service(couponRepo, issueRepo)

            assertThat(sut.myCoupons(1L, page = 0, size = 2)).hasSize(2)
            assertThat(sut.myCoupons(1L, page = 1, size = 2)).hasSize(2)
            assertThat(sut.myCoupons(1L, page = 2, size = 2)).hasSize(1)
            assertThat(sut.myCoupons(1L, page = 3, size = 2)).isEmpty()
        }

        @DisplayName("CSVC-11: USED 는 만료 여부 무관 USED (USED 우선)")
        @Test
        fun usedTakesPrecedence() {
            val couponRepo = FakeCouponRepository().apply { seed(2L, coupon(expiredAt = alreadyExpired)) }
            val issueRepo = FakeCouponIssueRepository().apply {
                save(usedIssue(2L, usedAt = LocalDateTime.parse("2026-05-01T10:00:00")))
            }

            val result = service(couponRepo, issueRepo).myCoupons(userId = 1L, page = 0, size = 10)

            assertThat(result.single().status).isEqualTo("USED")
        }
    }
}
