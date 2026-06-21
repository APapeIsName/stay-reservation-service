package com.stay.interfaces.api.admin.v1.coupon

import com.stay.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * 어드민 쿠폰 V1 API OpenAPI 스펙. Controller 가 구현.
 * 접근 제어는 X-Stay-Ldap 헤더 기반 LdapAuthInterceptor 가 진입 전 수행 (없으면 401).
 */
@Tag(name = "Coupon Admin V1 API", description = "쿠폰 템플릿 CRUD·발급내역 어드민 V1 API")
interface CouponAdminV1ApiSpec {

    @Operation(
        summary = "쿠폰 템플릿 등록",
        description = "쿠폰 템플릿을 신규 등록한다. 성공 시 201 Created.",
    )
    fun create(
        request: CouponAdminV1Dto.CreateRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 템플릿 목록 조회",
        description = "등록된 쿠폰 템플릿을 페이지 단위로 조회한다.",
    )
    fun list(
        page: Int,
        size: Int,
    ): ApiResponse<List<CouponAdminV1Dto.CouponResponse>>

    @Operation(
        summary = "쿠폰 템플릿 단건 조회",
        description = "쿠폰 템플릿 1건을 조회한다. 미존재 시 404.",
    )
    fun get(
        couponId: Long,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 템플릿 수정",
        description = "쿠폰 템플릿의 수정 가능 필드를 일괄 갱신한다. 미존재 시 404.",
    )
    fun update(
        couponId: Long,
        request: CouponAdminV1Dto.UpdateRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse>

    @Operation(
        summary = "쿠폰 템플릿 삭제",
        description = "쿠폰 템플릿 1건을 삭제한다. 성공 시 204 No Content.",
    )
    fun delete(
        couponId: Long,
    )

    @Operation(
        summary = "쿠폰 발급내역 조회",
        description = "특정 템플릿(couponId)의 발급분을 페이지 단위로 조회한다.",
    )
    fun listIssues(
        couponId: Long,
        page: Int,
        size: Int,
    ): ApiResponse<List<CouponAdminV1Dto.IssueResponse>>
}
