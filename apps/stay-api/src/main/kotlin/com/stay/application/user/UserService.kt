package com.stay.application.user

import com.stay.domain.user.LoginId
import com.stay.domain.user.User
import com.stay.domain.user.UserRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * 회원가입 유스케이스 오케스트레이션.
 *
 * 흐름:
 *  1. loginId 중복 선검사 → CONFLICT
 *  2. `User.signUp(...)` 으로 도메인 검증 + Aggregate 합성 (VO 위반 → BAD_REQUEST)
 *  3. `UserRepository.save` 영속
 *  4. `UserInfo` 매핑 후 반환
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §1, §3
 *  - .claude/rules/08-static-factory-and-clock-injection.md (Clock 외부 주입)
 *  - .claude/rules/10-uniqueness-app-and-db.md (어플 선검사)
 */
@Component
class UserService(
    private val userRepository: UserRepository,
    private val clock: Clock,
) {
    @Transactional
    fun signUp(command: SignupCommand): UserInfo {
        val loginIdVo = LoginId(command.loginId)
        if (userRepository.existsByLoginId(loginIdVo)) {
            throw CoreException(
                ErrorType.CONFLICT,
                "이미 사용 중인 로그인 ID 입니다.",
            )
        }
        val user = User.signUp(
            loginId = command.loginId,
            rawPassword = command.rawPassword,
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
            phoneNumber = command.phoneNumber,
            today = LocalDate.now(clock),
        )
        val saved = userRepository.save(user)
        return UserInfo.from(saved)
    }
}
