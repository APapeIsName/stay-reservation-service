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
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.6 Email VO
 * 규칙: ^[A-Za-z0-9._+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$
 * 정책: TLD 필수 (alen@example 거부)
 */
@Tag("unit")
class EmailTest {

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("EM-01: 표준 형식이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenStandardFormat() {
            val input = "alen@example.com"
            val sut = Email(input)
            assertThat(sut.value).isEqualTo(input)
        }

        @DisplayName("EM-02: 서브도메인·플러스태그·다단 TLD 도 정상 생성된다")
        @Test
        fun valueIsAccepted_whenSubdomainAndTags() {
            val input = "alen+tag@sub.example.co.kr"
            val sut = Email(input)
            assertThat(sut.value).isEqualTo(input)
        }
    }

    @Nested
    @DisplayName("실패 — 형식 위반")
    inner class InvalidFormat {

        @DisplayName("EM-03~07: 형식이 어긋나면 BAD_REQUEST")
        @ParameterizedTest(name = "input=\"{0}\"")
        @ValueSource(
            strings = [
                "alen", // EM-03: 도메인 없음 (@ 없음)
                "alen@", // EM-04: 도메인 빈값
                "@example.com", // EM-05: 로컬 빈값
                "alen@example", // EM-06: TLD 없음
                "", // EM-07: 빈 문자열
            ],
        )
        fun throwsBadRequest_whenInvalidFormat(input: String) {
            assertBadRequest { Email(input) }
        }
    }
}
