package com.stay.domain.property

import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.JoinColumn
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 예약 시점에 취소 정책을 복제 보존하는 스냅샷 VO — 원본 CancellationPolicy 가 이후 변경되어도
 * 예약의 환불 계산은 예약 당시 규칙을 유지한다 (시점 일관성). 환불 계산의 단일 소유자.
 *  - 규칙: ① "N일 전" = 달력일 차이 (취소 시각 무관)
 *         ② 일수 차 >= daysBeforeCheckIn 인 규칙 중 daysBeforeCheckIn 최대 규칙 적용 (경계일 당일은 높은 환불율 포함)
 *         ③ 환불액 = totalPrice × refundRate / 100, 끝전 내림
 *         ④ 매칭 규칙 없으면 0원 — 체크인 이후 취소 (음수 일수) 포함
 *         ⑤ rules 비정렬 입력 허용 — 매칭이 정렬에 의존하지 않음
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.3 CancellationPolicySnapshot VO
 *  - docs/round-3/03-questions.md Q2 (환불 의미론 확정)
 *  - LLD §4.3 시점 일관성 (원본 정책 변경과 라이프사이클 분리)
 */
@Embeddable
data class CancellationPolicySnapshot(
    @ElementCollection
    @CollectionTable(name = "reservation_refund_rule", joinColumns = [JoinColumn(name = "reservation_id")])
    val rules: List<RefundRule>,
) {

    fun refundAmount(totalPrice: Long, cancelledAt: LocalDateTime, checkIn: LocalDate): Long {
        val daysBefore = ChronoUnit.DAYS.between(cancelledAt.toLocalDate(), checkIn)
        val matched = rules
            .filter { daysBefore >= it.daysBeforeCheckIn }
            .maxByOrNull { it.daysBeforeCheckIn }
            ?: return 0L
        return totalPrice * matched.refundRate / 100
    }
}
