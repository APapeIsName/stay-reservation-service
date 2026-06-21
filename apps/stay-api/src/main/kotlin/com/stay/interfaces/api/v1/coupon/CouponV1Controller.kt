package com.stay.interfaces.api.v1.coupon

import com.stay.application.coupon.CouponService
import com.stay.application.coupon.IssueCommand
import com.stay.interfaces.api.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 쿠폰 V1 API Controller. 얇은 계층 — 헤더 userId + 입력 변환 + Service 호출 + 응답 매핑.
 * 예외는 ApiControllerAdvice 가 일관 처리 (미존재 NOT_FOUND 등).
 */
@RestController
@RequestMapping("/api/v1")
class CouponV1Controller(
    private val couponService: CouponService,
) : CouponV1ApiSpec {

    @PostMapping("/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.CREATED)
    override fun issue(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.IssueResponse> {
        val info = couponService.issue(IssueCommand(couponId = couponId, userId = userId))
        return ApiResponse.success(CouponV1Dto.IssueResponse.from(info))
    }

    @GetMapping("/users/me/coupons")
    override fun myCoupons(
        @RequestHeader("X-USER-ID") userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<CouponV1Dto.MyCouponResponse>> {
        val infos = couponService.myCoupons(userId, page, size)
        return ApiResponse.success(infos.map { CouponV1Dto.MyCouponResponse.from(it) })
    }
}
