package com.stay.interfaces.api.v1.user

import com.stay.application.user.UserService
import com.stay.interfaces.api.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 회원 V1 API Controller. 얇은 계층 — 입력 변환 + Service 호출 + 응답 매핑.
 *
 * 흐름:
 *  - SignupRequest → SignupCommand → UserService.signUp → UserInfo → SignupResponse
 *  - 예외는 ApiControllerAdvice 가 일관 처리 (CoreException → HTTP status + ApiResponse envelope)
 */
@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userService: UserService,
) : UserV1ApiSpec {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    override fun signup(
        @RequestBody request: UserV1Dto.SignupRequest,
    ): ApiResponse<UserV1Dto.SignupResponse> {
        val info = userService.signUp(request.toCommand())
        return ApiResponse.success(UserV1Dto.SignupResponse.from(info))
    }
}
