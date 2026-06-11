package com.stay.domain.property

import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.JoinColumn
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 숙소의 현재 취소 정책 VO (RefundRule 합성) — 취소 시점 기준 환불액 계산 (Q2 확정 환불 의미론).
 *  - 환불 계산은 CancellationPolicySnapshot 이 단일 소유 — 본 클래스는 snapshot() 변환 후 위임해
 *    두 타입의 의미론 동일성을 구성으로 보장 (D-B3 중복 회피)
 *  - snapshot(): 예약 시점 복제 보존용 변환 — rules 방어 복사로 원본과 라이프사이클 분리
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.1 CancellationPolicy VO
 *  - docs/round-3/03-questions.md Q2 (환불 의미론 확정)
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class CancellationPolicy(
    @ElementCollection
    @CollectionTable(name = "refund_rule", joinColumns = [JoinColumn(name = "property_id")])
    val rules: List<RefundRule>,
) {

    fun snapshot(): CancellationPolicySnapshot = CancellationPolicySnapshot(rules.toList())

    fun refundAmount(totalPrice: Long, cancelledAt: LocalDateTime, checkIn: LocalDate): Long =
        snapshot().refundAmount(totalPrice, cancelledAt, checkIn)
}
