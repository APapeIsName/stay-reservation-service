package com.stay.domain.user

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.5 BirthDate VO
 * 규칙: yyyy-MM-dd 포맷 / 미래 불가 / 만 14세 이상 (today 외부 주입)
 */
class BirthDateTest {

    companion object {
        // 결정적 기준일 — 모든 BirthDate 검증의 today
        private val TODAY: LocalDate = LocalDate.of(2026, 5, 21)

        // TODAY 로부터 정확히 14년 전 같은 날 — 만 14세 도달 경계
        private const val AGE14_BOUNDARY: String = "2012-05-21"
    }

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("BD-01: 형식 OK + 만 14세 이상이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenValidAndOver14() {
            val sut = BirthDate("1995-03-15", TODAY)
            assertThat(sut.value).isEqualTo(LocalDate.of(1995, 3, 15))
        }

        @DisplayName("BD-02: 만 14세 도달일 경계는 정상 생성된다")
        @Test
        fun valueIsAccepted_whenExactlyAge14() {
            val sut = BirthDate(AGE14_BOUNDARY, TODAY)
            assertThat(sut.value).isEqualTo(LocalDate.of(2012, 5, 21))
        }
    }

    @Nested
    @DisplayName("실패 — 연령·시점 위반")
    inner class InvalidAgeOrTime {

        @DisplayName("BD-03: 만 14세 -1일 (도달일 직전) 이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenOneDayBeforeAge14() {
            assertBadRequest { BirthDate("2012-05-22", TODAY) }
        }

        @DisplayName("BD-04: 미래 날짜이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenFuture() {
            assertBadRequest { BirthDate("2026-05-22", TODAY) }
        }
    }

    @Nested
    @DisplayName("실패 — 포맷 위반")
    inner class InvalidFormat {

        @DisplayName("BD-05~08: 포맷이 어긋나면 BAD_REQUEST")
        @ParameterizedTest(name = "input=\"{0}\"")
        @ValueSource(
            strings = [
                "1995/03/15", // BD-05: 슬래시 구분자
                "1995-13-01", // BD-06: 월 범위 초과
                "1995-02-30", // BD-07: 실제 없는 날짜
                "19950315", // BD-08: 하이픈 없음
            ],
        )
        fun throwsBadRequest_whenInvalidFormat(input: String) {
            assertBadRequest { BirthDate(input, TODAY) }
        }
    }
}
