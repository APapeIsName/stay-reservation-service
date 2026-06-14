package com.stay.domain.reservation

import com.stay.domain.user.Name
import com.stay.domain.user.PhoneNumber
import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.3 GuestInfo VO
 * 규칙: guestCount >= 1 (E.2 잠정 가정 #4). 이름·전화 형식은 R1 Name·PhoneNumber VO 재사용 (S-9 잠정)
 */
@Tag("unit")
class GuestInfoTest {

    @Nested
    @DisplayName("정상 케이스")
    inner class Valid {

        @DisplayName("GI-01: 투숙자명·연락처·인원이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenAllFieldsValid() {
            val sut = GuestInfo(Name("공명선"), PhoneNumber("010-1234-5678"), 2)
            assertThat(sut.guestCount).isEqualTo(2)
            assertThat(sut.guestName.value).isEqualTo("공명선")
        }

        @DisplayName("GI-02: 인원 1명 (하한 경계) 이면 정상 생성된다")
        @Test
        fun valueIsAccepted_whenGuestCountIsLowerBound() {
            val sut = GuestInfo(Name("공명선"), PhoneNumber("010-1234-5678"), 1)
            assertThat(sut.guestCount).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("실패 — 인원 위반")
    inner class Invalid {

        @DisplayName("GI-03~04: 인원이 1 미만이면 BAD_REQUEST")
        @ParameterizedTest(name = "guestCount={0}")
        @ValueSource(
            ints = [
                0, // GI-03: 0명
                -1, // GI-04: 음수
            ],
        )
        fun throwsBadRequest_whenGuestCountIsBelowOne(guestCount: Int) {
            assertBadRequest {
                GuestInfo(Name("공명선"), PhoneNumber("010-1234-5678"), guestCount)
            }
        }
    }
}
