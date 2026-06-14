package com.stay.domain.reservation

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderBy

/**
 * 예약 시점의 일자별 요금 스냅샷 VO (DailyPriceEntry 합성).
 *  - 규칙: entries 빈 목록 불가 (E.2 잠정 가정 #5)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.3 PriceSnapshot VO
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class PriceSnapshot(
    @ElementCollection
    @CollectionTable(name = "reservation_price_entry", joinColumns = [JoinColumn(name = "reservation_id")])
    @OrderBy("date ASC")
    val entries: List<DailyPriceEntry>,
) {
    init {
        if (entries.isEmpty()) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "요금 스냅샷 형식이 올바르지 않습니다.",
            )
        }
    }

    fun totalPrice(): Long = entries.sumOf { it.pricePerNight }
}
