package com.stay.domain.reservation

import java.time.LocalDateTime

/**
 * 취소 결과 VO — Reservation.cancel 이 반환하는 값 보유 객체 (환불액 + 취소 시각).
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.3 Reservation.cancel (RSV-06)
 *  - LLD §7.2 (CancellationResult)
 */
data class CancellationResult(
    val refundAmount: Long,
    val cancelledAt: LocalDateTime,
)
