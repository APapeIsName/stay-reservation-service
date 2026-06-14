package com.stay.domain.property

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.1 CancellationPolicy (CP-06~12·15~16)
 * 규칙 (Q2 확정 환불 의미론 — docs/round-3/03-questions.md):
 *  ① "N일 전" = 달력일 차이 (취소 시각 무관)   ② 경계일 당일은 높은 환불율에 포함
 *  ③ 끝전 내림   ④ 매칭 규칙 없으면 0원   ⑤ rules 비정렬 입력 허용 (매칭이 정렬 무관)
 */
@Tag("unit")
class CancellationPolicyTest {

    private val standardPolicy = CancellationPolicy(
        listOf(RefundRule(7, 100), RefundRule(3, 50), RefundRule(0, 0)),
    )
    private val checkIn = LocalDate.parse("2026-07-10")

    @Nested
    @DisplayName("환불율 구간 매칭")
    inner class TierMatching {

        @DisplayName("CP-06: 10일 전 취소면 100% 환불 (300000)")
        @Test
        fun refundsFull_whenCancelledTenDaysBefore() {
            val cancelledAt = LocalDateTime.parse("2026-06-30T10:00")
            assertThat(standardPolicy.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(300000L)
        }

        @DisplayName("CP-08: 6일 전 (7일 미만 첫날) 취소면 50% 구간 진입 (150000)")
        @Test
        fun refundsHalf_whenCancelledSixDaysBefore() {
            val cancelledAt = LocalDateTime.parse("2026-07-04T10:00")
            assertThat(standardPolicy.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(150000L)
        }

        @DisplayName("CP-10: 1일 전 취소면 0% 구간 (0)")
        @Test
        fun refundsNothing_whenCancelledOneDayBefore() {
            val cancelledAt = LocalDateTime.parse("2026-07-09T10:00")
            assertThat(standardPolicy.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(0L)
        }

        @DisplayName("CP-11: 체크인 당일 아침 취소면 0일 규칙 적용 (0)")
        @Test
        fun refundsNothing_whenCancelledOnCheckInDay() {
            val cancelledAt = LocalDateTime.parse("2026-07-10T08:00")
            assertThat(standardPolicy.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("경계일 처리 — 경계일 당일은 높은 환불율에 포함 (Q2-②)")
    inner class BoundaryDay {

        @DisplayName("CP-07: 정확히 7일 전 취소면 100% 환불 (경계일 포함)")
        @Test
        fun refundsFull_whenCancelledExactlySevenDaysBefore() {
            val cancelledAt = LocalDateTime.parse("2026-07-03T10:00")
            assertThat(standardPolicy.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(300000L)
        }

        @DisplayName("CP-09: 정확히 3일 전 밤 23:59 취소도 50% — 달력일 차이 기준, 시각 무관 (Q2-①)")
        @Test
        fun refundsHalf_whenCancelledLateNightThreeDaysBefore() {
            val cancelledAt = LocalDateTime.parse("2026-07-07T23:59")
            assertThat(standardPolicy.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(150000L)
        }
    }

    @Nested
    @DisplayName("끝전·매칭 부재·비정렬 (Q2-③④⑤)")
    inner class EdgeSemantics {

        @DisplayName("CP-12: 99999 의 50% 는 49999 — 끝전 내림")
        @Test
        fun floorsRemainder_whenAmountIsOdd() {
            val policy = CancellationPolicy(listOf(RefundRule(3, 50)))
            val cancelledAt = LocalDateTime.parse("2026-07-05T10:00")
            assertThat(policy.refundAmount(99999L, cancelledAt, checkIn)).isEqualTo(49999L)
        }

        @DisplayName("CP-15: 어떤 규칙에도 매칭되지 않으면 0원")
        @Test
        fun refundsNothing_whenNoRuleMatches() {
            val policy = CancellationPolicy(listOf(RefundRule(7, 100), RefundRule(3, 50)))
            val cancelledAt = LocalDateTime.parse("2026-07-09T10:00")
            assertThat(policy.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(0L)
        }

        @DisplayName("CP-16: rules 비정렬 입력도 daysBeforeCheckIn 기준으로 동일 매칭")
        @Test
        fun matchesByDays_whenRulesAreUnordered() {
            val policy = CancellationPolicy(
                listOf(RefundRule(0, 0), RefundRule(7, 100), RefundRule(3, 50)),
            )
            val cancelledAt = LocalDateTime.parse("2026-06-30T10:00")
            assertThat(policy.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(300000L)
        }
    }
}
