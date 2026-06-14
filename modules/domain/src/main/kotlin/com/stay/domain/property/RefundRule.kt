package com.stay.domain.property

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 취소 정책 규칙 1건 VO (CancellationPolicy 내부 요소) — "체크인 N일 전까지 취소하면 M% 환불".
 *  - 규칙: daysBeforeCheckIn >= 0, refundRate 0~100 (Q2 / E.2 잠정 가정 G-4)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *  - 영속: @ElementCollection 요소 — refund_rule (현재 정책) / reservation_refund_rule (스냅샷) 양쪽에서 재사용
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.1 RefundRule VO / docs/design/04-erd.md §2.4·2.10
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class RefundRule(
    @Column(name = "days_before_check_in", nullable = false)
    val daysBeforeCheckIn: Int,
    @Column(name = "refund_rate", nullable = false)
    val refundRate: Int,
) {
    init {
        if (daysBeforeCheckIn < 0) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "취소 기준 일수 형식이 올바르지 않습니다.",
            )
        }
        if (refundRate !in 0..100) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "환불율 형식이 올바르지 않습니다.",
            )
        }
    }
}
