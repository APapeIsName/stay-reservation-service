package com.stay.domain.property

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.JoinColumn

/**
 * RoomType 의 침대 구성 VO (BedEntry 합성).
 *  - 규칙: entries 빈 목록 불가 (E.2 잠정 가정 G-9)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.1 BedConfiguration VO
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class BedConfiguration(
    @ElementCollection
    @CollectionTable(name = "bed_entry", joinColumns = [JoinColumn(name = "room_type_id")])
    val entries: List<BedEntry>,
) {
    init {
        if (entries.isEmpty()) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "침대 구성 형식이 올바르지 않습니다.",
            )
        }
    }
}
