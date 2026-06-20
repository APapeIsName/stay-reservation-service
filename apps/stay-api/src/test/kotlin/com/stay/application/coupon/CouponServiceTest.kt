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
}
