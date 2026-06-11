package com.stay.interfaces.api.v1.reservation

import com.stay.application.reservation.ReservationService
import com.stay.interfaces.api.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 예약 V1 API Controller. 얇은 계층 — 입력 변환 + Service 호출 + 응답 매핑.
 *
 * 흐름:
 *  - reserve: ReserveRequest → ReserveCommand → ReservationService.reserve → ReservationInfo → ReserveResponse
 *  - cancel: reservationId → ReservationService.cancel → CancellationInfo → CancelResponse
 *  - 기간·인원·소유 검증은 도메인/Service 위임 (rule 06) — 예외는 ApiControllerAdvice 가 일관 처리
 *  - 인증 미도입 — X-USER-ID 헤더가 사용자 식별 잠정 컨벤션
 */
@RestController
@RequestMapping("/api/v1/reservations")
class ReservationV1Controller(
    private val reservationService: ReservationService,
) : ReservationV1ApiSpec {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun reserve(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestBody request: ReservationV1Dto.ReserveRequest,
    ): ApiResponse<ReservationV1Dto.ReserveResponse> {
        val info = reservationService.reserve(userId, request.toCommand())
        return ApiResponse.success(ReservationV1Dto.ReserveResponse.from(info))
    }

    @PostMapping("/{reservationId}/cancel")
    override fun cancel(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable reservationId: Long,
    ): ApiResponse<ReservationV1Dto.CancelResponse> {
        val info = reservationService.cancel(userId, reservationId)
        return ApiResponse.success(ReservationV1Dto.CancelResponse.from(info))
    }
}
