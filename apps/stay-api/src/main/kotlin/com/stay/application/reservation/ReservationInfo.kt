package com.stay.application.reservation

import com.stay.domain.reservation.Reservation
import com.stay.domain.reservation.ReservationStatus

/**
 * 예약 결과 출력 DTO.
 *  - 클라이언트가 즉시 필요한 최소 필드 + 쿠폰 적용 금액 분해 (할인 전·할인액·최종 결제액, Round 4)
 *  - totalPrice == finalPrice (할인 후 최종 결제액). 발제: "스냅샷에 할인 전/할인액/최종액 모두 포함"
 */
data class ReservationInfo(
    val reservationId: Long,
    val status: ReservationStatus,
    val couponId: Long?,
    val priceBeforeDiscount: Long,
    val discountAmount: Long,
    val totalPrice: Long,
) {
    companion object {
        fun from(reservation: Reservation): ReservationInfo =
            ReservationInfo(
                reservationId = reservation.id,
                status = reservation.status,
                couponId = reservation.couponId,
                priceBeforeDiscount = reservation.priceSnapshot.priceBeforeDiscount,
                discountAmount = reservation.priceSnapshot.discountAmount,
                totalPrice = reservation.totalPrice,
            )
    }
}
