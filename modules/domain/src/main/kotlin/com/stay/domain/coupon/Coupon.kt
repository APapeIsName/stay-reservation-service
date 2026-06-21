package com.stay.domain.coupon

import com.stay.domain.AuditedEntity
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import kotlin.math.min

/**
 * 쿠폰 템플릿 — Aggregate Root. 어드민 CRUD 대상이며 발급분(CouponIssue)은 couponId 로 ID 참조만 한다.
 *  - calculateDiscount: 할인 금액 계산. FIXED `min(value, pre)` / RATE `floor(pre × value / 100)`
 *  - minOrderAmount 미달(`pre < minOrderAmount`) 시 BAD_REQUEST — 경계 당값은 충족(>=)
 *  - 만료(expiredAt)로 인한 거절은 calculateDiscount 가 아니라 CouponIssue 책임 (금액 계산만 담당)
 *  - update: 어드민 PUT 수정 — name·type·value·minOrderAmount·expiredAt 일괄 갱신 (Property.updateInfo 동형, Tell Don't Ask).
 *    이미 발급된 분(CouponIssue)은 couponId 참조만 하므로 템플릿 갱신은 발급분 불변 — 시점 스냅샷이 필요하면 발급분 쪽 책임
 *  - 락 없음 — 읽기 위주·어드민 수정만 (@Version 없음, ADR-004)
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.1 Coupon (CPN-01~11)
 *  - .claude/rules/18-domain-modeling.md §5 (Aggregate 간 ID 참조)
 */
@Entity
@Table(name = "coupon")
class Coupon(
    name: String,
    type: CouponType,
    value: Long,
    minOrderAmount: Long?,
    expiredAt: LocalDateTime,
) : AuditedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: CouponType = type
        private set

    @Column(name = "value", nullable = false)
    var value: Long = value
        private set

    @Column(name = "min_order_amount")
    var minOrderAmount: Long? = minOrderAmount
        private set

    @Column(name = "expired_at", nullable = false)
    var expiredAt: LocalDateTime = expiredAt
        private set

    /** 어드민 수정 — 수정 가능 필드 전체를 일괄 갱신한다 (Property.updateInfo 동형). */
    fun update(
        name: String,
        type: CouponType,
        value: Long,
        minOrderAmount: Long?,
        expiredAt: LocalDateTime,
    ) {
        this.name = name
        this.type = type
        this.value = value
        this.minOrderAmount = minOrderAmount
        this.expiredAt = expiredAt
    }

    fun calculateDiscount(preDiscountAmount: Long): Long {
        val minOrderAmount = this.minOrderAmount
        if (minOrderAmount != null && preDiscountAmount < minOrderAmount) {
            throw CoreException(ErrorType.BAD_REQUEST, "최소 결제 금액을 충족하지 않습니다.")
        }
        return when (type) {
            CouponType.FIXED -> min(value, preDiscountAmount)
            CouponType.RATE -> preDiscountAmount * value / 100
        }
    }
}
