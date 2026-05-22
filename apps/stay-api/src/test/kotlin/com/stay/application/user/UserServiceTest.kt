package com.stay.application.user

import com.stay.domain.user.User
import com.stay.domain.user.UserRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.10 UserService (unit-level)
 *
 * 본 테스트는 `UserService.signUp` 의 오케스트레이션 흐름을 mock repository 로 검증한다.
 * 실제 DB 영속·동시성·트랜잭션 롤백은 Step 6 통합 테스트(Testcontainers) 에서.
 */
class UserServiceTest {

    companion object {
        private val TODAY: LocalDate = LocalDate.of(2026, 5, 21)
        private val FIXED_CLOCK: Clock = Clock.fixed(
            TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(),
            ZoneId.of("Asia/Seoul"),
        )

        private fun validCommand(loginId: String = "alen2026"): SignupCommand =
            SignupCommand(
                loginId = loginId,
                rawPassword = "P@ssw0rd",
                name = "공명선",
                birthDate = "1995-03-15",
                email = "alen@example.com",
                phoneNumber = "010-1234-5678",
            )
    }

    private fun newService(repository: UserRepository): UserService =
        UserService(userRepository = repository, clock = FIXED_CLOCK)

    @Nested
    @DisplayName("정상 가입")
    inner class Valid {

        @DisplayName("FAC-U-01: 중복 없으면 save 호출 + UserInfo 반환")
        @Test
        fun returnsUserInfo_whenValid() {
            val repository: UserRepository = mock()
            whenever(repository.existsByLoginId(any())).thenReturn(false)
            // save 가 호출되면 입력받은 user 를 그대로 반환 (실제 영속처럼)
            whenever(repository.save(any())).thenAnswer { invocation -> invocation.arguments[0] as User }

            val sut = newService(repository)
            val info = sut.signUp(validCommand())

            assertThat(info.loginId).isEqualTo("alen2026")
            assertThat(info.name).isEqualTo("공명선")
            assertThat(info.birthDate).isEqualTo(LocalDate.of(1995, 3, 15))
            assertThat(info.email).isEqualTo("alen@example.com")
            assertThat(info.phoneNumber).isEqualTo("010-1234-5678")
            verify(repository).save(any())
        }
    }

    @Nested
    @DisplayName("실패 — 중복 loginId 선검사")
    inner class DuplicateLoginId {

        @DisplayName("FAC-U-02: existsByLoginId=true 이면 CONFLICT, save 미호출")
        @Test
        fun throwsConflict_andDoesNotSave_whenLoginIdExists() {
            val repository: UserRepository = mock()
            whenever(repository.existsByLoginId(any())).thenReturn(true)

            val sut = newService(repository)
            val thrown = assertThrows<CoreException> { sut.signUp(validCommand()) }

            assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
            verify(repository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("실패 — 도메인 검증 위반 전파")
    inner class DomainValidationPropagation {

        @DisplayName("FAC-U-03: password 가 birthDate 포함이면 BAD_REQUEST, save 미호출")
        @Test
        fun throwsBadRequest_andDoesNotSave_whenPasswordContainsBirthDate() {
            val repository: UserRepository = mock()
            whenever(repository.existsByLoginId(any())).thenReturn(false)

            val sut = newService(repository)
            val invalidCommand = validCommand().copy(rawPassword = "a19950315b!!")
            val thrown = assertThrows<CoreException> { sut.signUp(invalidCommand) }

            assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            verify(repository, never()).save(any())
        }
    }
}
