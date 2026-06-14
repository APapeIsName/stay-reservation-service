package com.stay.domain.user

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 로그인 식별자 VO.
 *  - 규칙: ^[A-Za-z0-9]{4,20}$ (영문 대소문자 + 숫자, 4~20자)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §2
 *  - .claude/rules/06-validation-via-domain-vo.md, 12-user-field-policy.md
 */
@Embeddable
data class LoginId(val value: String) {
    init {
        if (!PATTERN.matches(value)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "로그인 ID 형식이 올바르지 않습니다.",
            )
        }
    }

    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9]{4,20}$")
    }
}
