package com.stay.domain.reservation

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderBy
import jakarta.persistence.Transient

/**
 * 예약 시점의 일자별 요금 스냅샷 VO (DailyPriceEntry 합성) + 쿠폰 할인 3분해 (Round 4).
 *  - 규칙: entries 빈 목록 불가 (E.2 잠정 가정 #5) → 위반 시 CoreException(BAD_REQUEST)
 *  - priceBeforeDiscount: Σ entries.pricePerNight (할인 전, 파생 — 저장 X, @Transient)
 *  - discountAmount: 적용된 할인액 (저장 — 쿠폰 미적용 시 0)
 *  - finalPrice: max(0, before − discount) (최종 결제액, 파생 — 저장 X). 0 floor 로 음수 방지
 *  - totalPrice(): finalPrice 와 동일 — Reservation.totalPrice 단일 출처 (Round 3 회귀: 미적용 시 == Σ entries)
 *  - before/final 을 컬럼으로 두지 않아 중복 저장 회피 — entries + discountAmount 만 영속, 나머지는 파생
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.3 PriceSnapshot 3분해 (RSV2-01~06)
 *  - docs/design/03-class-diagram.md §4 / .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class PriceSnapshot(
    @ElementCollection
    @CollectionTable(name = "reservation_price_entry", joinColumns = [JoinColumn(name = "reservation_id")])
    @OrderBy("date ASC")
    val entries: List<DailyPriceEntry>,
    @Column(name = "discount_amount", nullable = false)
    val discountAmount: Long = 0L,
) {
    init {
        if (entries.isEmpty()) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "요금 스냅샷 형식이 올바르지 않습니다.",
            )
        }
    }

    @get:Transient
    val priceBeforeDiscount: Long
        get() = entries.sumOf { it.pricePerNight }

    @get:Transient
    val finalPrice: Long
        get() = maxOf(0L, priceBeforeDiscount - discountAmount)

    fun totalPrice(): Long = finalPrice

    companion object {
        fun of(entries: List<DailyPriceEntry>, discountAmount: Long = 0L): PriceSnapshot =
            PriceSnapshot(entries, discountAmount)
    }
}
