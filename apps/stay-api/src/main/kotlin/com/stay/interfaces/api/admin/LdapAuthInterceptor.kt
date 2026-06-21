package com.stay.interfaces.api.admin

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 어드민 LDAP 인증 인터셉터 (형식적 스텁).
 *
 * api-admin 하위 경로 진입 전 `X-Stay-Ldap` 헤더 존재만 검증한다 — 헤더가 없거나 공백이면
 * CoreException(UNAUTHORIZED) 을 throw 하고, ApiControllerAdvice 가 401 로 매핑한다 (rule 09).
 *
 * 실제 LDAP 디렉터리 연동(바인드·그룹 검사)은 과투자라 도입하지 않는다 — 어드민 경로가
 * 인증 게이트를 거친다는 사실만 인터셉터 등록으로 명시하고, 헤더 존재로 게이트를 대체한다.
 * 연동 강화가 필요해지면 이 클래스 안에서 헤더 값 검증을 실제 LDAP 호출로 교체한다.
 */
@Component
class LdapAuthInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val ldap = request.getHeader(LDAP_HEADER)
        if (ldap.isNullOrBlank()) {
            throw CoreException(ErrorType.UNAUTHORIZED, "LDAP 인증이 필요합니다.")
        }
        return true
    }

    companion object {
        const val LDAP_HEADER = "X-Stay-Ldap"
    }
}
