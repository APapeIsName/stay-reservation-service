package com.stay.domain.user

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Tag

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.1 LoginId VO
 * 규칙: ^[A-Za-z0-9]{4,20}$  (docs/round-1/01-signup-requirements.md §2)
 */
@Tag("unit")
class LoginIdTest {

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("LID-01: 영문 + 숫자 조합이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenAlphanumeric() {
            val input = "alen2026"
            val sut = LoginId(input)
            assertThat(sut.value).isEqualTo(input)
        }

        @DisplayName("LID-02: 길이가 4자 (하한 경계) 이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenLengthIsLowerBound() {
            val input = "ABC1"
            val sut = LoginId(input)
            assertThat(sut.value).isEqualTo(input)
        }

        @DisplayName("LID-03: 길이가 20자 (상한 경계) 이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenLengthIsUpperBound() {
            val input = "a".repeat(20)
            val sut = LoginId(input)
            assertThat(sut.value).isEqualTo(input)
        }
    }

    @Nested
    @DisplayName("실패 — 길이 제약 위반")
    inner class InvalidLength {

        @DisplayName("LID-04: 3자 (하한 미만) 이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenLengthIsBelowLowerBound() {
            assertBadRequest { LoginId("abc") }
        }

        @DisplayName("LID-05: 21자 (상한 초과) 이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenLengthIsAboveUpperBound() {
            assertBadRequest { LoginId("a".repeat(21)) }
        }

        @DisplayName("LID-06: 빈 문자열이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenEmpty() {
            assertBadRequest { LoginId("") }
        }
    }

    @Nested
    @DisplayName("실패 — 허용 charset 외 문자 포함")
    inner class InvalidCharset {

        @DisplayName("LID-07~10: 영문 대소문자 + 숫자 외 문자가 포함되면 BAD_REQUEST")
        @ParameterizedTest(name = "input=\"{0}\"")
        @ValueSource(
            strings = [
                "abc-1234", // LID-07: 하이픈
                "abc 1234", // LID-08: 공백
                "abc한글1", // LID-09: 한글
                "abc@123", // LID-10: 특수문자
            ],
        )
        fun throwsBadRequest_whenContainsDisallowedChar(input: String) {
            assertBadRequest { LoginId(input) }
        }
    }
}
