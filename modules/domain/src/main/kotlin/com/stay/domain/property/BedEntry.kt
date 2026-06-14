package com.stay.domain.property

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

/**
 * 침대 1종 구성 VO (BedConfiguration 내부 요소).
 *  - 규칙: count >= 1 (E.2 잠정 가정 G-9)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.1 BedEntry VO
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class BedEntry(
    @Enumerated(EnumType.STRING)
    @Column(name = "bed_type", nullable = false, length = 20)
    val type: BedType,
    @Column(name = "count", nullable = false)
    val count: Int,
) {
    init {
        if (count < 1) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "침대 개수 형식이 올바르지 않습니다.",
            )
        }
    }
}
