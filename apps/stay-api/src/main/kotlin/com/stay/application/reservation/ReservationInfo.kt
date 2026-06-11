package com.stay.application.reservation

import com.stay.domain.reservation.Reservation
import com.stay.domain.reservation.ReservationStatus

/**
 * 예약 결과 출력 DTO.
 *  - 클라이언트가 즉시 필요한 최소 필드 (id·status·totalPrice) 만 노출
 */
data class ReservationInfo(
    val reservationId: Long,
    val status: ReservationStatus,
    val totalPrice: Long,
) {
    companion object {
        fun from(reservation: Reservation): ReservationInfo =
            ReservationInfo(
                reservationId = reservation.id,
                status = reservation.status,
                totalPrice = reservation.totalPrice,
            )
    }
}
