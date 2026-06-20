package com.stay.domain.coupon

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 카탈로그: docs/round-4/02-tdd-plan.md  B.1 Coupon (CPN-01~11)
 * 규칙 (ADR-004 / Q5):
 *   - FIXED: min(value, preDiscountAmount) — 할인이 결제액 초과 불가
 *   - RATE : floor(preDiscountAmount × value / 100) — 끝전 내림 (C-1)
 *   - minOrderAmount 미달 시 CoreException(BAD_REQUEST), 경계 당값은 충족 (>=, C-2)
 *   - 만료(expiredAt)로 인한 거절은 calculateDiscount 가 아니라 CouponIssue 책임 (B.2)
 */
@Tag("unit")
class CouponTest {

    private fun coupon(
        type: CouponType,
        value: Long,
        minOrderAmount: Long? = null,
        expiredAt: LocalDateTime = LocalDateTime.parse("2026-12-31T23:59:00"),
    ) = Coupon(
        name = "테스트 쿠폰",
        type = type,
        value = value,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
    )

    @Nested
    @DisplayName("정액 할인 — FIXED (value = 할인 원)")
    inner class Fixed {

        @DisplayName("CPN-01: FIXED 5000원, 주문 30000원 → 할인 5000")
        @Test
        fun fixedDiscount() {
            assertThat(coupon(CouponType.FIXED, 5000L).calculateDiscount(30000L)).isEqualTo(5000L)
        }

        @DisplayName("CPN-02: 할인액(50000)이 주문액(30000)보다 크면 주문액으로 클램프 → 30000")
        @Test
        fun clampsToOrderAmount() {
            assertThat(coupon(CouponType.FIXED, 50000L).calculateDiscount(30000L)).isEqualTo(30000L)
        }

        @DisplayName("CPN-03: 할인액 == 주문액(30000) → 30000 (전액 할인 경계)")
        @Test
        fun fullDiscountBoundary() {
            assertThat(coupon(CouponType.FIXED, 30000L).calculateDiscount(30000L)).isEqualTo(30000L)
        }
    }

    @Nested
    @DisplayName("정률 할인 — RATE (value = 퍼센트), 끝전 내림")
    inner class Rate {

        @DisplayName("CPN-04: RATE 10%, 주문 30000 → 3000")
        @Test
        fun rateDiscount() {
            assertThat(coupon(CouponType.RATE, 10L).calculateDiscount(30000L)).isEqualTo(3000L)
        }

        @DisplayName("CPN-05: RATE 10%, 주문 99999 → 9999 (끝전 내림)")
        @Test
        fun floorsFraction() {
            assertThat(coupon(CouponType.RATE, 10L).calculateDiscount(99999L)).isEqualTo(9999L)
        }

        @DisplayName("CPN-06: RATE 100%, 주문 30000 → 30000 (전액 할인 상한)")
        @Test
        fun fullRate() {
            assertThat(coupon(CouponType.RATE, 100L).calculateDiscount(30000L)).isEqualTo(30000L)
        }

        @DisplayName("CPN-07: RATE 33%, 주문 10000 → 3300 (정확히 떨어짐)")
        @Test
        fun exactRate() {
            assertThat(coupon(CouponType.RATE, 33L).calculateDiscount(10000L)).isEqualTo(3300L)
        }

        @DisplayName("CPN-10: RATE 10%, 최소금액 없음, 주문 0 → 0")
        @Test
        fun zeroOrder() {
            assertThat(coupon(CouponType.RATE, 10L, minOrderAmount = null).calculateDiscount(0L)).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("최소 결제금액 조건 — minOrderAmount")
    inner class MinOrder {

        @DisplayName("CPN-08: 최소금액 50000, 주문 49999 (1원 미달) → BAD_REQUEST")
        @Test
        fun rejectsBelowMin() {
            assertBadRequest {
                coupon(CouponType.FIXED, 5000L, minOrderAmount = 50000L).calculateDiscount(49999L)
            }
        }

        @DisplayName("CPN-09: 최소금액 50000, 주문 50000 (경계 당값 충족) → 5000")
        @Test
        fun acceptsAtBoundary() {
            assertThat(
                coupon(CouponType.FIXED, 5000L, minOrderAmount = 50000L).calculateDiscount(50000L),
            ).isEqualTo(5000L)
        }
    }

    @Nested
    @DisplayName("쿠폰 타입 — CouponType")
    inner class Type {

        @DisplayName("CPN-11: CouponType 은 FIXED, RATE 2종")
        @Test
        fun twoTypes() {
            assertThat(CouponType.values()).containsExactly(CouponType.FIXED, CouponType.RATE)
        }
    }
}
