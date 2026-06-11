package com.stay.domain.reservation

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate

/**
 * 일자별 요금 1건 VO (PriceSnapshot 내부 요소).
 *  - 검증 없는 값 보유 VO — DailyRoom 에서 복사되는 단위
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.3 DailyPriceEntry VO
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class DailyPriceEntry(
    @Column(name = "date", nullable = false)
    val date: LocalDate,
    @Column(name = "price_per_night", nullable = false)
    val pricePerNight: Long,
)
