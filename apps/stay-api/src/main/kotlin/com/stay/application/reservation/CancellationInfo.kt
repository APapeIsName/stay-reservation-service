package com.stay.application.reservation

import com.stay.domain.reservation.ReservationStatus
import java.time.LocalDateTime

/**
 * 취소 결과 출력 DTO.
 *  - 클라이언트가 즉시 필요한 최소 필드 (refundAmount·cancelledAt·status) 만 노출
 */
data class CancellationInfo(
    val refundAmount: Long,
    val cancelledAt: LocalDateTime,
    val status: ReservationStatus,
)
