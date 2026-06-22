package com.stay.support.error

/**
 * 도메인 에러 종류.
 *
 * HTTP status 는 stay-api 의 `ApiControllerAdvice` 가 statusCode 로 매핑한다.
 * 도메인 계층(modules/domain)이 spring-web (HttpStatus) 에 의존하지 않도록 Int 로 보관.
 */
enum class ErrorType(val statusCode: Int, val code: String, val message: String) {
    /** 범용 에러 */
    INTERNAL_ERROR(500, "Internal Server Error", "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(400, "Bad Request", "잘못된 요청입니다."),
    UNAUTHORIZED(401, "Unauthorized", "인증이 필요합니다."),
    NOT_FOUND(404, "Not Found", "존재하지 않는 요청입니다."),
    FORBIDDEN(403, "Forbidden", "접근 권한이 없습니다."),
    CONFLICT(409, "Conflict", "이미 존재하는 리소스입니다."),
}
