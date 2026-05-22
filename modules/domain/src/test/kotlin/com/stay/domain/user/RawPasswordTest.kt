package com.stay.domain.user

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.2 RawPassword VO
 * 규칙: 8~16자 / 영문 대소문자 + 숫자 + 허용 특수문자 charset
 *       조합 강제 없음 (스펙 직역) — .claude/rules/11-password-policy.md
 *       생년월일 substring 검사는 Password VO (Cycle 7) 의 책임
 */
class RawPasswordTest {

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("RPW-01: 8자 (하한 경계) + 허용 charset 이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenLengthIsLowerBound() {
            val input = "P@ssw0rd"
            val sut = RawPassword(input)
            assertThat(sut.value).isEqualTo(input)
        }

        @DisplayName("RPW-02: 16자 (상한 경계) + 허용 charset 이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenLengthIsUpperBound() {
            val input = "P@ssw0rd!23456ab"
            val sut = RawPassword(input)
            assertThat(sut.value).isEqualTo(input)
        }

        @DisplayName("RPW-08: 영문 소문자만이어도 (조합 강제 없음) 정상 생성된다")
        @Test
        fun valueIsAccepted_whenSingleCharsetClass() {
            val input = "abcdefgh"
            val sut = RawPassword(input)
            assertThat(sut.value).isEqualTo(input)
        }
    }

    @Nested
    @DisplayName("실패 — 길이 제약 위반")
    inner class InvalidLength {

        @DisplayName("RPW-03: 7자 (하한 미만) 이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenLengthIsBelowLowerBound() {
            assertBadRequest { RawPassword("P@ssw0r") }
        }

        @DisplayName("RPW-04: 17자 (상한 초과) 이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenLengthIsAboveUpperBound() {
            assertBadRequest { RawPassword("a".repeat(17)) }
        }
    }

    @Nested
    @DisplayName("실패 — 허용 charset 외 문자 포함")
    inner class InvalidCharset {

        @DisplayName("RPW-05~07: 비허용 문자가 포함되면 BAD_REQUEST")
        @ParameterizedTest(name = "input=\"{0}\"")
        @ValueSource(
            strings = [
                "비밀번호12!@", // RPW-05: 한글
                "P@ss w0rd", // RPW-06: 공백
                "P`ssw0rd", // RPW-07: 백틱 (비허용 특수문자)
            ],
        )
        fun throwsBadRequest_whenContainsDisallowedChar(input: String) {
            assertBadRequest { RawPassword(input) }
        }
    }
}
