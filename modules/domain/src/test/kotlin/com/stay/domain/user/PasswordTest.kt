package com.stay.domain.user

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.3 Password VO
 * 규칙:
 *  - encrypt 시 (1) raw 평문에 birthDate.toYyyyMMdd() substring 포함되면 거부, (2) BCrypt 해시화
 *  - matches 는 raw 와 hashedValue 의 BCrypt 매칭
 *  - 같은 raw·birthDate 라도 매 encrypt 결과 hashedValue 다름 (salt 무작위)
 */
class PasswordTest {

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 5, 21)
        private val BIRTH_DATE: BirthDate = BirthDate("1995-03-15", TODAY)
    }

    @Nested
    @DisplayName("정상 encrypt + matches")
    inner class Valid {

        @DisplayName("PW-01: 정상 raw + birthDate 이면 encrypt 성공, matches(raw)=true")
        @Test
        fun encryptAndMatches_whenValid() {
            val raw = RawPassword("P@ssw0rd")

            val sut = Password.encrypt(raw, BIRTH_DATE)

            assertThat(sut.matches(raw)).isTrue()
        }

        @DisplayName("PW-02: 다른 raw 와 비교하면 matches=false")
        @Test
        fun matches_returnsFalse_whenDifferentRaw() {
            val raw = RawPassword("P@ssw0rd")
            val different = RawPassword("Diff3rent!")
            val sut = Password.encrypt(raw, BIRTH_DATE)

            assertThat(sut.matches(different)).isFalse()
        }

        @DisplayName("PW-05: YYMMDD (6자리) 만 포함, YYYYMMDD 는 없으면 정상 (스펙: YYYYMMDD 만 차단)")
        @Test
        fun encryptSucceeds_whenContainsYymmddButNotYyyymmdd() {
            // birthDate=1995-03-15 → YYYYMMDD=19950315. "950315" (YYMMDD) 만 포함은 검사 대상 아님
            val raw = RawPassword("950315ab!")

            val sut = Password.encrypt(raw, BIRTH_DATE)

            assertThat(sut.matches(raw)).isTrue()
        }

        @DisplayName("PW-06: 같은 raw·birthDate 로 두 번 encrypt 하면 hashedValue 가 다르다 (salt 무작위)")
        @Test
        fun hashedValuesDiffer_whenEncryptedTwice() {
            val raw = RawPassword("P@ssw0rd")

            val first = Password.encrypt(raw, BIRTH_DATE)
            val second = Password.encrypt(raw, BIRTH_DATE)

            assertThat(first.hashedValue).isNotEqualTo(second.hashedValue)
            // 그러나 둘 다 같은 raw 에 대해 matches=true
            assertThat(first.matches(raw)).isTrue()
            assertThat(second.matches(raw)).isTrue()
        }
    }

    @Nested
    @DisplayName("실패 encrypt — 비밀번호에 YYYYMMDD substring 포함")
    inner class InvalidBirthDateSubstring {

        @DisplayName("PW-03: 앞쪽에 YYYYMMDD 포함이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenContainsYyyymmddAtStart() {
            assertBadRequest { Password.encrypt(RawPassword("19950315xyz!"), BIRTH_DATE) }
        }

        @DisplayName("PW-04: 중간에 YYYYMMDD 포함이면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenContainsYyyymmddInMiddle() {
            assertBadRequest { Password.encrypt(RawPassword("a19950315b!!"), BIRTH_DATE) }
        }
    }
}
