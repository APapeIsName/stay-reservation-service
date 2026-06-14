package com.stay.application.user

import com.stay.domain.user.User
import java.time.LocalDate

/**
 * 가입 결과 출력 DTO.
 *  - password 미포함 (rule 11)
 *  - 가입 응답에는 마스킹 미적용 (rule 13 — 본인이 방금 입력한 데이터)
 */
data class UserInfo(
    val id: Long,
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
    val phoneNumber: String,
) {
    companion object {
        fun from(user: User): UserInfo =
            UserInfo(
                id = user.id,
                loginId = user.loginId.value,
                name = user.name.value,
                birthDate = user.birthDate.value,
                email = user.email.value,
                phoneNumber = user.phoneNumber.value,
            )
    }
}
