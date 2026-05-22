package com.stay.domain.user

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Embeddable

/**
 * 휴대폰 번호 VO.
 *  - 규칙: ^010-\d{4}-\d{4}$  (예약 SMS 발송용 형식 엄수)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §2
 *  - .claude/rules/06-validation-via-domain-vo.md, 12-user-field-policy.md
 */
@Embeddable
data class PhoneNumber(val value: String) {
    init {
        if (!PATTERN.matches(value)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "휴대폰 번호 형식이 올바르지 않습니다.",
            )
        }
    }

    companion object {
        private val PATTERN = Regex("^010-\\d{4}-\\d{4}$")
    }
}
