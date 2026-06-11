package com.stay.domain.reservation

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.3 DateRange VO
 * 규칙: checkIn < checkOut (0박·역전 불가 — E.2 잠정 가정 #1), stayDates() 는 체크아웃 미포함
 */
@Tag("unit")
class DateRangeTest {

    @Nested
    @DisplayName("정상 케이스 — 생성과 파생 계산")
    inner class Valid {

        @DisplayName("RNG-01: 2박 기간이면 정상 생성되고 nights() == 2")
        @Test
        fun valueIsAccepted_whenTwoNights() {
            val sut = DateRange(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-03"))
            assertThat(sut.checkIn).isEqualTo(LocalDate.parse("2026-07-01"))
            assertThat(sut.checkOut).isEqualTo(LocalDate.parse("2026-07-03"))
            assertThat(sut.nights()).isEqualTo(2)
        }

        @DisplayName("RNG-02: 1박 (경계 하한) 이면 정상 생성되고 nights() == 1")
        @Test
        fun valueIsAccepted_whenOneNightLowerBound() {
            val sut = DateRange(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-02"))
            assertThat(sut.nights()).isEqualTo(1)
        }

        @DisplayName("RNG-03: stayDates() 는 체크아웃 날짜를 포함하지 않는다")
        @Test
        fun stayDatesExcludesCheckOut() {
            val sut = DateRange(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-03"))
            assertThat(sut.stayDates()).containsExactly(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-02"),
            )
            assertThat(sut.stayDates()).hasSize(sut.nights())
        }

        @DisplayName("RNG-04: 월 경계를 걸치는 3박도 일자 나열과 박수가 정확하다")
        @Test
        fun stayDatesCrossesMonthBoundary() {
            val sut = DateRange(LocalDate.parse("2026-07-30"), LocalDate.parse("2026-08-02"))
            assertThat(sut.stayDates()).containsExactly(
                LocalDate.parse("2026-07-30"),
                LocalDate.parse("2026-07-31"),
                LocalDate.parse("2026-08-01"),
            )
            assertThat(sut.nights()).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("실패 — 기간 위반")
    inner class Invalid {

        @DisplayName("RNG-05: 체크인과 체크아웃이 같은 날짜 (0박) 면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenSameDate() {
            assertBadRequest {
                DateRange(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-01"))
            }
        }

        @DisplayName("RNG-06: 체크아웃이 체크인보다 앞서면 (역전) BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenReversed() {
            assertBadRequest {
                DateRange(LocalDate.parse("2026-07-03"), LocalDate.parse("2026-07-01"))
            }
        }
    }
}
