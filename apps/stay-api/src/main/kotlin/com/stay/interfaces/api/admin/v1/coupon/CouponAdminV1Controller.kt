package com.stay.interfaces.api.admin.v1.coupon

import com.stay.application.coupon.CouponAdminService
import com.stay.interfaces.api.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 어드민 쿠폰 V1 API Controller. 얇은 계층 — 입력 변환 + Service 호출 + 응답 매핑.
 * 접근 제어는 LdapAuthInterceptor 가 api-admin 하위 진입 전 수행 (헤더 누락 시 401).
 * 예외는 ApiControllerAdvice 가 일관 처리 (미존재 NOT_FOUND 등).
 */
@RestController
@RequestMapping("/api-admin/v1/coupons")
class CouponAdminV1Controller(
    private val couponAdminService: CouponAdminService,
) : CouponAdminV1ApiSpec {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun create(
        @RequestBody request: CouponAdminV1Dto.CreateRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        val info = couponAdminService.register(request.toCommand())
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info))
    }

    @GetMapping
    override fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<CouponAdminV1Dto.CouponResponse>> {
        val infos = couponAdminService.list(page, size)
        return ApiResponse.success(infos.map { CouponAdminV1Dto.CouponResponse.from(it) })
    }

    @GetMapping("/{couponId}")
    override fun get(
        @PathVariable couponId: Long,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        val info = couponAdminService.get(couponId)
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info))
    }

    @PutMapping("/{couponId}")
    override fun update(
        @PathVariable couponId: Long,
        @RequestBody request: CouponAdminV1Dto.UpdateRequest,
    ): ApiResponse<CouponAdminV1Dto.CouponResponse> {
        val info = couponAdminService.update(couponId, request.toCommand())
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info))
    }

    @DeleteMapping("/{couponId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    override fun delete(
        @PathVariable couponId: Long,
    ) {
        couponAdminService.delete(couponId)
    }

    @GetMapping("/{couponId}/issues")
    override fun listIssues(
        @PathVariable couponId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<List<CouponAdminV1Dto.IssueResponse>> {
        val infos = couponAdminService.listIssues(couponId, page, size)
        return ApiResponse.success(infos.map { CouponAdminV1Dto.IssueResponse.from(it) })
    }
}
