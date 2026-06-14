package com.stay.interfaces.api.v1.wishlist

import com.stay.application.wishlist.WishlistService
import com.stay.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 찜 V1 API Controller. 얇은 계층 — 헤더/경로 변수 추출 + Service 호출 + 응답 매핑.
 *
 * 흐름:
 *  - add/remove: X-USER-ID 헤더 + propertyId → WishlistService → 응답 데이터 없음 (success envelope 만)
 *  - 미존재 숙소 NOT_FOUND 는 Service 위임 — 예외는 ApiControllerAdvice 가 일관 처리
 *  - 인증 미도입 — X-USER-ID 헤더가 사용자 식별 잠정 컨벤션
 */
@RestController
@RequestMapping("/api/v1/properties/{propertyId}/wishes")
class WishlistV1Controller(
    private val wishlistService: WishlistService,
) : WishlistV1ApiSpec {

    @PostMapping
    override fun add(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable propertyId: Long,
    ): ApiResponse<Any> {
        wishlistService.add(userId, propertyId)
        return ApiResponse.success()
    }

    @DeleteMapping
    override fun remove(
        @RequestHeader("X-USER-ID") userId: Long,
        @PathVariable propertyId: Long,
    ): ApiResponse<Any> {
        wishlistService.remove(userId, propertyId)
        return ApiResponse.success()
    }
}
