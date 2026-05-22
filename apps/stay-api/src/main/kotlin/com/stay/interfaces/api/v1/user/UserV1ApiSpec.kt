package com.stay.interfaces.api.v1.user

import com.stay.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

/**
 * 회원 V1 API OpenAPI 스펙. Controller 가 구현.
 * 보존 골격 패턴 — OpenAPI 어노테이션을 interface 에 분리해 Controller 가 비즈니스 흐름만 가지도록.
 */
@Tag(name = "User V1 API", description = "회원 V1 API")
interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "신규 회원을 등록한다. 성공 시 201 Created + 가입된 회원 정보 반환.",
    )
    fun signup(request: UserV1Dto.SignupRequest): ApiResponse<UserV1Dto.SignupResponse>
}
