package com.stay.domain.user

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 회원 이름 VO.
 *  - 규칙: ^[가-힣]{1,10}$ (한글 1~10자)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §2
 *  - .claude/rules/06-validation-via-domain-vo.md, 12-user-field-policy.md
 */
@Embeddable
data class Name(val value: String) {
    init {
        if (!PATTERN.matches(value)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "이름 형식이 올바르지 않습니다.",
            )
        }
    }

    companion object {
        private val PATTERN = Regex("^[가-힣]{1,10}$")
    }
}
