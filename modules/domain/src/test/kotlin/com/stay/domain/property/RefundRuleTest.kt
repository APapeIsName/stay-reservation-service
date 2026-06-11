package com.stay.domain.property

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.1 RefundRule VO
 * 규칙: daysBeforeCheckIn >= 0, refundRate 0~100 (Q2 / E.2 잠정 가정 G-4)
 */
@Tag("unit")
class RefundRuleTest {

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("CP-01: 7일 전 100% (상한 경계) 면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenRefundRateIsUpperBound() {
            val sut = RefundRule(7, 100)
            assertThat(sut.daysBeforeCheckIn).isEqualTo(7)
            assertThat(sut.refundRate).isEqualTo(100)
        }

        @DisplayName("CP-02: 0일 전 0% (둘 다 하한 경계) 면 정상 생성된다 — 당일 0% 규칙 표현")
        @Test
        fun valueIsAccepted_whenBothAreLowerBound() {
            val sut = RefundRule(0, 0)
            assertThat(sut.daysBeforeCheckIn).isEqualTo(0)
            assertThat(sut.refundRate).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("실패 — 값 범위 위반")
    inner class Invalid {

        @DisplayName("CP-03~05: 일수 음수 또는 환불율 0~100 밖이면 BAD_REQUEST")
        @ParameterizedTest(name = "daysBeforeCheckIn={0}, refundRate={1}")
        @CsvSource(
            // CP-03: 음수 일수
            "-1, 50",
            // CP-04: 환불율 상한 초과
            "7, 101",
            // CP-05: 음수 환불율
            "7, -1",
        )
        fun throwsBadRequest_whenValueIsOutOfRange(daysBeforeCheckIn: Int, refundRate: Int) {
            assertBadRequest { RefundRule(daysBeforeCheckIn, refundRate) }
        }
    }
}
