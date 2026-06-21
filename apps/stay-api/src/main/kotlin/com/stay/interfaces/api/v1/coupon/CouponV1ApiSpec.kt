package com.stay.interfaces.api.v1.coupon

import com.stay.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * 쿠폰 V1 API OpenAPI 스펙. Controller 가 구현.
 * 사용자 식별은 X-USER-ID 헤더 (인증 미도입 잠정 — 어드민 LDAP 인증과 구분).
 */
@Tag(name = "Coupon V1 API", description = "쿠폰 발급·조회 V1 API")
interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급 요청",
        description = "쿠폰 템플릿을 발급받아 본인 소유 발급분(AVAILABLE)을 생성한다. 성공 시 201 Created.",
    )
    fun issue(
        userId: Long,
        couponId: Long,
    ): ApiResponse<CouponV1Dto.IssueResponse>

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "내 발급 쿠폰을 사용 가능(AVAILABLE)/사용 완료(USED)/만료(EXPIRED) 상태와 함께 페이지 단위로 조회한다.",
    )
    fun myCoupons(
        userId: Long,
        page: Int,
        size: Int,
    ): ApiResponse<List<CouponV1Dto.MyCouponResponse>>
}
