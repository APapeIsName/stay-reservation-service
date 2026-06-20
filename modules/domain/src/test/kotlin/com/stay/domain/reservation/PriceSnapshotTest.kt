package com.stay.domain.reservation

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.3 PriceSnapshot VO (+ DailyPriceEntry)
 * 규칙: entries 빈 목록 불가 (E.2 잠정 가정 #5), totalPrice() == 일자별 요금 합
 */
@Tag("unit")
class PriceSnapshotTest {

    @Nested
    @DisplayName("정상 케이스 — 값 보유와 합산")
    inner class Valid {

        @DisplayName("PS-01: DailyPriceEntry 는 일자와 요금을 보유한다")
        @Test
        fun entryHoldsDateAndPrice() {
            val sut = DailyPriceEntry(LocalDate.parse("2026-07-01"), 100000L)
            assertThat(sut.date).isEqualTo(LocalDate.parse("2026-07-01"))
            assertThat(sut.pricePerNight).isEqualTo(100000L)
        }

        @DisplayName("PS-02: 2박 요금이 합산된다")
        @Test
        fun totalPriceSumsTwoNights() {
            val sut = PriceSnapshot(
                listOf(
                    DailyPriceEntry(LocalDate.parse("2026-07-01"), 100000L),
                    DailyPriceEntry(LocalDate.parse("2026-07-02"), 200000L),
                ),
            )
            assertThat(sut.totalPrice()).isEqualTo(300000L)
        }

        @DisplayName("PS-03: 단건 (1박) 도 합산이 정확하다")
        @Test
        fun totalPriceSumsSingleNight() {
            val sut = PriceSnapshot(listOf(DailyPriceEntry(LocalDate.parse("2026-07-01"), 150000L)))
            assertThat(sut.totalPrice()).isEqualTo(150000L)
        }
    }

    @Nested
    @DisplayName("실패 — 빈 스냅샷")
    inner class Invalid {

        @DisplayName("PS-04: 빈 entries 면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenEntriesIsEmpty() {
            assertBadRequest { PriceSnapshot(emptyList()) }
        }
    }

    @Nested
    @DisplayName("할인 3분해 — Round 4 (of 팩토리)")
    inner class Discount {

        private val twoNights = listOf(
            DailyPriceEntry(LocalDate.parse("2026-07-01"), 100000L),
            DailyPriceEntry(LocalDate.parse("2026-07-02"), 200000L),
        )

        @DisplayName("RSV2-01: of(entries, 0) — 미적용 → before 300000 / discount 0 / final 300000")
        @Test
        fun noDiscount() {
            val sut = PriceSnapshot.of(twoNights, 0L)
            assertThat(sut.priceBeforeDiscount).isEqualTo(300000L)
            assertThat(sut.discountAmount).isEqualTo(0L)
            assertThat(sut.finalPrice).isEqualTo(300000L)
            assertThat(sut.totalPrice()).isEqualTo(300000L)
        }

        @DisplayName("RSV2-02: of(entries, 50000) → final 250000 / totalPrice 250000")
        @Test
        fun withDiscount() {
            val sut = PriceSnapshot.of(twoNights, 50000L)
            assertThat(sut.priceBeforeDiscount).isEqualTo(300000L)
            assertThat(sut.discountAmount).isEqualTo(50000L)
            assertThat(sut.finalPrice).isEqualTo(250000L)
            assertThat(sut.totalPrice()).isEqualTo(250000L)
        }

        @DisplayName("RSV2-03: 할인 == 주문액 (전액 할인) → final 0")
        @Test
        fun fullDiscount() {
            val oneNight = listOf(DailyPriceEntry(LocalDate.parse("2026-07-01"), 100000L))
            val sut = PriceSnapshot.of(oneNight, 100000L)
            assertThat(sut.finalPrice).isEqualTo(0L)
            assertThat(sut.priceBeforeDiscount).isEqualTo(100000L)
            assertThat(sut.discountAmount).isEqualTo(100000L)
        }

        @DisplayName("RSV2-04: 할인 > 주문액 (방어적) → final 0 floor")
        @Test
        fun overDiscountFloorsToZero() {
            val oneNight = listOf(DailyPriceEntry(LocalDate.parse("2026-07-01"), 100000L))
            assertThat(PriceSnapshot.of(oneNight, 150000L).finalPrice).isEqualTo(0L)
        }

        @DisplayName("RSV2-05: of(emptyList(), 0) → BAD_REQUEST (빈 스냅샷)")
        @Test
        fun emptyRejected() {
            assertBadRequest { PriceSnapshot.of(emptyList(), 0L) }
        }

        @DisplayName("RSV2-06: 기존 1-인자 생성자 회귀 — totalPrice() == 합산 (3분해가 기존 의미 불변)")
        @Test
        fun legacyConstructorRegression() {
            val sut = PriceSnapshot(twoNights)
            assertThat(sut.totalPrice()).isEqualTo(300000L)
            assertThat(sut.discountAmount).isEqualTo(0L)
        }
    }
}
