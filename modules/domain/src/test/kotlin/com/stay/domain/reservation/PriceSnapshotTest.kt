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
}
