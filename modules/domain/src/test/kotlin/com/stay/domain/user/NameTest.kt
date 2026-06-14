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
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.4 Name VO
 * 규칙: ^[가-힣]{1,10}$  (한글 1~10자)
 */
@Tag("unit")
class NameTest {

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("NAM-01: 한글 이름이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenKorean() {
            val input = "공명선"
            val sut = Name(input)
            assertThat(sut.value).isEqualTo(input)
        }

        @DisplayName("NAM-02: 길이가 1자 (하한 경계) 이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenLengthIsLowerBound() {
            val input = "가"
            val sut = Name(input)
            assertThat(sut.value).isEqualTo(input)
        }

        @DisplayName("NAM-03: 길이가 10자 (상한 경계) 이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenLengthIsUpperBound() {
            val input = "가".repeat(10)
            val sut = Name(input)
            assertThat(sut.value).isEqualTo(input)
        }
    }

    @Nested
    @DisplayName("실패 — 길이 제약 위반")
    inner class InvalidLength {

        @DisplayName("NAM-04: 빈 문자열이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenEmpty() {
            assertBadRequest { Name("") }
        }

        @DisplayName("NAM-05: 11자 (상한 초과) 이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenLengthIsAboveUpperBound() {
            assertBadRequest { Name("가".repeat(11)) }
        }
    }

    @Nested
    @DisplayName("실패 — 한글 외 문자 포함")
    inner class InvalidCharset {

        @DisplayName("NAM-06~08: 한글 외 문자가 포함되면 BAD_REQUEST")
        @ParameterizedTest(name = "input=\"{0}\"")
        @ValueSource(
            strings = [
                "John", // NAM-06: 영문
                "공 명선", // NAM-07: 공백
                "공명선1", // NAM-08: 숫자
            ],
        )
        fun throwsBadRequest_whenContainsNonKoreanChar(input: String) {
            assertBadRequest { Name(input) }
        }
    }
}
