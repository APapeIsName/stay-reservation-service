package com.stay.interfaces.api.v1.reservation

import com.stay.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * 예약 V1 API OpenAPI 스펙. Controller 가 구현.
 * 보존 골격 패턴 — OpenAPI 어노테이션을 interface 에 분리해 Controller 가 비즈니스 흐름만 가지도록.
 */
@Tag(name = "Reservation V1 API", description = "예약 V1 API")
interface ReservationV1ApiSpec {

    @Operation(
        summary = "예약 생성",
        description = "객실 타입을 기간·인원 조건으로 예약한다. 성공 시 201 Created + 예약 정보 반환. " +
            "사용자 식별은 X-USER-ID 헤더 (인증 미도입 잠정).",
    )
    fun reserve(
        userId: Long,
        request: ReservationV1Dto.ReserveRequest,
    ): ApiResponse<ReservationV1Dto.ReserveResponse>

    @Operation(
        summary = "예약 취소",
        description = "본인 예약을 취소하고 환불 금액·취소 시각을 반환한다. 사용자 식별은 X-USER-ID 헤더 (인증 미도입 잠정).",
    )
    fun cancel(
        userId: Long,
        reservationId: Long,
    ): ApiResponse<ReservationV1Dto.CancelResponse>
}
