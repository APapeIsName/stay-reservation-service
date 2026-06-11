package com.stay.domain.property

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 숙소 주소 VO.
 *  - 규칙: detailAddress 공백 불가, zipCode ^\d{5}$ (E.2 잠정 가정 G-9)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.1 Address VO
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class Address(
    @Column(name = "detail_address", nullable = false, length = 200)
    val detailAddress: String,
    @Column(name = "zip_code", nullable = false, length = 10)
    val zipCode: String,
) {
    init {
        if (detailAddress.isBlank()) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "상세주소 형식이 올바르지 않습니다.",
            )
        }
        if (!ZIP_CODE_PATTERN.matches(zipCode)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "우편번호 형식이 올바르지 않습니다.",
            )
        }
    }

    companion object {
        private val ZIP_CODE_PATTERN = Regex("^\\d{5}$")
    }
}
