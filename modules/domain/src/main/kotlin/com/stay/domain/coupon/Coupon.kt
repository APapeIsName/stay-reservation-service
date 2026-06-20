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
 *  - 락 없음 — 읽기 위주·어드민 수정만 (@Version 없음, ADR-004)
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.1 Coupon (CPN-01~11)
 *  - .claude/rules/18-domain-modeling.md §5 (Aggregate 간 ID 참조)
 */
@Entity
@Table(name = "coupon")
class Coupon(
    @Column(name = "name", nullable = false, length = 100)
    val name: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    val type: CouponType,
    @Column(name = "value", nullable = false)
    val value: Long,
    @Column(name = "min_order_amount")
    val minOrderAmount: Long?,
    @Column(name = "expired_at", nullable = false)
    val expiredAt: LocalDateTime,
) : AuditedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun calculateDiscount(preDiscountAmount: Long): Long {
        if (minOrderAmount != null && preDiscountAmount < minOrderAmount) {
            throw CoreException(ErrorType.BAD_REQUEST, "최소 결제 금액을 충족하지 않습니다.")
        }
        return when (type) {
            CouponType.FIXED -> min(value, preDiscountAmount)
            CouponType.RATE -> preDiscountAmount * value / 100
        }
    }
}
