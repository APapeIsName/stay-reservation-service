package com.stay.interfaces.api.v1.reservation

import com.stay.application.reservation.CancellationInfo
import com.stay.application.reservation.ReservationInfo
import com.stay.application.reservation.ReserveCommand
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 예약 V1 API DTO container.
 *  - Request 는 raw 타입 보관 → toCommand 변환 (rule 20) — 검증은 도메인 VO 가 단일 진입점 (rule 06)
 *  - Response 는 application Info 에서 `from` 정적 팩토리로 변환. status 는 enum name 문자열로 직렬화
 */
class ReservationV1Dto private constructor() {

    data class ReserveRequest(
        val propertyId: Long,
        val roomTypeId: Long,
        val checkIn: LocalDate,
        val checkOut: LocalDate,
        val guestCount: Int,
        val guestName: String,
        val guestPhone: String,
    ) {
        fun toCommand(): ReserveCommand =
            ReserveCommand(
                propertyId = propertyId,
                roomTypeId = roomTypeId,
                checkIn = checkIn,
                checkOut = checkOut,
                guestCount = guestCount,
                guestName = guestName,
                guestPhone = guestPhone,
            )
    }

    data class ReserveResponse(
        val reservationId: Long,
        val status: String,
        val totalPrice: Long,
    ) {
        companion object {
            fun from(info: ReservationInfo): ReserveResponse =
                ReserveResponse(
                    reservationId = info.reservationId,
                    status = info.status.name,
                    totalPrice = info.totalPrice,
                )
        }
    }

    data class CancelResponse(
        val refundAmount: Long,
        val cancelledAt: LocalDateTime,
        val status: String,
    ) {
        companion object {
            fun from(info: CancellationInfo): CancelResponse =
                CancelResponse(
                    refundAmount = info.refundAmount,
                    cancelledAt = info.cancelledAt,
                    status = info.status.name,
                )
        }
    }
}
