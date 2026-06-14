package com.stay.interfaces.api.v1.wishlist

import com.stay.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * 찜 V1 API OpenAPI 스펙. Controller 가 구현.
 * 보존 골격 패턴 — OpenAPI 어노테이션을 interface 에 분리해 Controller 가 비즈니스 흐름만 가지도록.
 */
@Tag(name = "Wishlist V1 API", description = "찜 V1 API")
interface WishlistV1ApiSpec {

    @Operation(
        summary = "찜 등록",
        description = "숙소를 찜 목록에 등록한다. 이미 찜한 숙소면 멱등 no-op. 사용자 식별은 X-USER-ID 헤더 (인증 미도입 잠정).",
    )
    fun add(userId: Long, propertyId: Long): ApiResponse<Any>

    @Operation(
        summary = "찜 취소",
        description = "숙소 찜을 취소한다. 찜하지 않은 숙소면 멱등 no-op. 사용자 식별은 X-USER-ID 헤더 (인증 미도입 잠정).",
    )
    fun remove(userId: Long, propertyId: Long): ApiResponse<Any>
}
