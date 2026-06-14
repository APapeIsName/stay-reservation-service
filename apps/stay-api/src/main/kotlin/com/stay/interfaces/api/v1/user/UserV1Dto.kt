package com.stay.interfaces.api.v1.user

import com.stay.application.user.SignupCommand
import com.stay.application.user.UserInfo
import java.time.LocalDate

/**
 * 회원 V1 API DTO container.
 *  - Request 는 raw String 보관 — 검증은 도메인 VO 가 단일 진입점 (rule 06)
 *  - Response 의 password 미포함 (rule 11) / 마스킹 미적용 (rule 13)
 */
class UserV1Dto private constructor() {

    data class SignupRequest(
        val loginId: String,
        val password: String,
        val name: String,
        val birthDate: String,
        val email: String,
        val phoneNumber: String,
    ) {
        fun toCommand(): SignupCommand =
            SignupCommand(
                loginId = loginId,
                rawPassword = password,
                name = name,
                birthDate = birthDate,
                email = email,
                phoneNumber = phoneNumber,
            )
    }

    data class SignupResponse(
        val userId: Long,
        val loginId: String,
        val name: String,
        val birthDate: LocalDate,
        val email: String,
        val phoneNumber: String,
    ) {
        companion object {
            fun from(info: UserInfo): SignupResponse =
                SignupResponse(
                    userId = info.id,
                    loginId = info.loginId,
                    name = info.name,
                    birthDate = info.birthDate,
                    email = info.email,
                    phoneNumber = info.phoneNumber,
                )
        }
    }
}
