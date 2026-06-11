package com.stay.domain.property

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.1 Address VO
 * 규칙: detailAddress 공백 불가, zipCode ^\d{5}$ (E.2 잠정 가정 G-9)
 */
@Tag("unit")
class AddressTest {

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("ADDR-01: 상세주소와 5자리 우편번호면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenValidAddressAndZipCode() {
            val sut = Address("테헤란로 152 강남파이낸스센터", "06236")
            assertThat(sut.detailAddress).isEqualTo("테헤란로 152 강남파이낸스센터")
            assertThat(sut.zipCode).isEqualTo("06236")
        }

        @DisplayName("ADDR-02: 동일 값으로 생성한 두 인스턴스는 동등하다")
        @Test
        fun instancesAreEqual_whenSameValues() {
            val a = Address("테헤란로 152", "06236")
            val b = Address("테헤란로 152", "06236")
            assertThat(a).isEqualTo(b)
        }
    }

    @Nested
    @DisplayName("실패 — 형식 위반")
    inner class Invalid {

        @DisplayName("ADDR-03: 빈 상세주소면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenDetailAddressIsBlank() {
            assertBadRequest { Address("", "06236") }
        }

        @DisplayName("ADDR-04~05: 우편번호가 5자리 숫자가 아니면 BAD_REQUEST")
        @ParameterizedTest(name = "zipCode=\"{0}\"")
        @ValueSource(
            strings = [
                "0623", // ADDR-04: 4자리
                "0623A", // ADDR-05: 비숫자 포함
            ],
        )
        fun throwsBadRequest_whenZipCodeIsInvalid(zipCode: String) {
            assertBadRequest { Address("테헤란로 152", zipCode) }
        }
    }
}
