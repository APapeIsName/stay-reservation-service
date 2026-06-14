package com.stay.domain.user

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import org.junit.jupiter.api.Tag

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.8 User.signUp 합성 테스트
 *
 * 비고: 각 VO 의 검증은 개별 테스트(B.1~B.7) 에서 망라. 본 합성 테스트는
 *  - 정상 입력으로 User 인스턴스가 올바르게 합성되는지
 *  - 한 필드 위반 시 정확한 도메인 예외가 (해당 VO 에서) throw 되는지
 * 만 sanity check.
 */
@Tag("slow-unit")
class UserTest {

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 5, 21)

        // 정상 baseline — 각 실패 케이스는 한 필드만 교체
        private const val VALID_LOGIN_ID = "alen2026"
        private const val VALID_PASSWORD = "P@ssw0rd"
        private const val VALID_NAME = "공명선"
        private const val VALID_BIRTH = "1995-03-15"
        private const val VALID_EMAIL = "alen@example.com"
        private const val VALID_PHONE = "010-1234-5678"
    }

    @Nested
    @DisplayName("정상 합성")
    inner class Valid {

        @DisplayName("USR-01: 모든 필드 정상이면 User 인스턴스가 생성된다 (id 미부여, 각 VO 보유, password.matches=true)")
        @Test
        fun userIsCreated_whenAllFieldsValid() {
            val sut = User.signUp(
                loginId = VALID_LOGIN_ID,
                rawPassword = VALID_PASSWORD,
                name = VALID_NAME,
                birthDate = VALID_BIRTH,
                email = VALID_EMAIL,
                phoneNumber = VALID_PHONE,
                today = TODAY,
            )

            assertThat(sut.id).isZero()
            assertThat(sut.loginId.value).isEqualTo(VALID_LOGIN_ID)
            assertThat(sut.name.value).isEqualTo(VALID_NAME)
            assertThat(sut.birthDate.value).isEqualTo(LocalDate.of(1995, 3, 15))
            assertThat(sut.email.value).isEqualTo(VALID_EMAIL)
            assertThat(sut.phoneNumber.value).isEqualTo(VALID_PHONE)
            assertThat(sut.password.matches(RawPassword(VALID_PASSWORD))).isTrue()
        }
    }

    @Nested
    @DisplayName("실패 — 한 필드 불량 시 도메인 예외 전파")
    inner class InvalidFieldPropagation {

        @DisplayName("USR-02: loginId 불량이면 BAD_REQUEST (LoginId VO 에서 throw)")
        @Test
        fun throwsBadRequest_whenLoginIdInvalid() {
            assertBadRequest {
                User.signUp(
                    // 3자, 길이 위반
                    loginId = "abc",
                    rawPassword = VALID_PASSWORD,
                    name = VALID_NAME,
                    birthDate = VALID_BIRTH,
                    email = VALID_EMAIL,
                    phoneNumber = VALID_PHONE,
                    today = TODAY,
                )
            }
        }

        @DisplayName("USR-03: 비밀번호가 birthDate substring 포함이면 BAD_REQUEST (Password.encrypt 에서 throw)")
        @Test
        fun throwsBadRequest_whenPasswordContainsBirthDate() {
            assertBadRequest {
                User.signUp(
                    loginId = VALID_LOGIN_ID,
                    // birthDate(1995-03-15) substring 포함
                    rawPassword = "a19950315b!!",
                    name = VALID_NAME,
                    birthDate = VALID_BIRTH,
                    email = VALID_EMAIL,
                    phoneNumber = VALID_PHONE,
                    today = TODAY,
                )
            }
        }

        @DisplayName("USR-04: birthDate 만 14세 미만이면 BAD_REQUEST (BirthDate VO 에서 throw)")
        @Test
        fun throwsBadRequest_whenBirthDateUnderAge14() {
            assertBadRequest {
                User.signUp(
                    loginId = VALID_LOGIN_ID,
                    rawPassword = VALID_PASSWORD,
                    name = VALID_NAME,
                    // 만 14세 -1일
                    birthDate = "2012-05-22",
                    email = VALID_EMAIL,
                    phoneNumber = VALID_PHONE,
                    today = TODAY,
                )
            }
        }
    }
}
