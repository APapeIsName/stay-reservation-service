package com.stay.domain.reservation

/**
 * 예약 상태.
 *  - PENDING 은 결제 연동 대비 자리만 예약 (ADR-002) — 본 라운드 생성 경로 없음 (confirm 은 즉시 CONFIRMED)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.3 Reservation.confirm (RSV-01)
 *  - ADR-002 (생성 = 즉시 CONFIRMED)
 */
enum class ReservationStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    CHECKED_OUT,
    CANCELLED,
}
