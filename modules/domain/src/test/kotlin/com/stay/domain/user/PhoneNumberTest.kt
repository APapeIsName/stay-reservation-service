package com.stay.domain.user

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.7 PhoneNumber VO
 * 규칙: ^010-\d{4}-\d{4}$
 */
class PhoneNumberTest {

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("PH-01: 010-XXXX-XXXX 형식이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenValidFormat() {
            val input = "010-1234-5678"
            val sut = PhoneNumber(input)
            assertThat(sut.value).isEqualTo(input)
        }
    }

    @Nested
    @DisplayName("실패 — 형식 위반")
    inner class InvalidFormat {

        @DisplayName("PH-02~07: 형식이 어긋나면 BAD_REQUEST")
        @ParameterizedTest(name = "input=\"{0}\"")
        @ValueSource(
            strings = [
                "011-1234-5678", // PH-02: 010 아님
                "010-123-4567", // PH-03: 자릿수 부족
                "010-12345-678", // PH-04: 분할 다름
                "01012345678", // PH-05: 하이픈 없음
                "010-1234-567a", // PH-06: 비숫자
                "", // PH-07: 빈 문자열
            ],
        )
        fun throwsBadRequest_whenInvalidFormat(input: String) {
            assertBadRequest { PhoneNumber(input) }
        }
    }
}
