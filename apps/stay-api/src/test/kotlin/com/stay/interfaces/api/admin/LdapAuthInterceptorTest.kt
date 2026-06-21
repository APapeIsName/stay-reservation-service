package com.stay.interfaces.api.admin

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * 어드민 LDAP 인터셉터 테스트.
 * 규칙: X-Stay-Ldap 헤더가 없거나 공백이면 CoreException(UNAUTHORIZED), 있으면 true (통과).
 * MockHttpServletRequest 로 헤더 유무만 검증 — 실제 LDAP 연동은 스텁 (헤더 존재 게이트).
 */
@Tag("unit")
class LdapAuthInterceptorTest {

    private val interceptor = LdapAuthInterceptor()
    private val handler = Any()

    @DisplayName("LDAP-01: X-Stay-Ldap 헤더 없으면 UNAUTHORIZED")
    @Test
    fun rejectsMissingHeader() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val thrown = assertThrows<CoreException> {
            interceptor.preHandle(request, response, handler)
        }
        assertThat(thrown.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
    }

    @DisplayName("LDAP-02: X-Stay-Ldap 헤더가 공백이면 UNAUTHORIZED")
    @Test
    fun rejectsBlankHeader() {
        val request = MockHttpServletRequest().apply {
            addHeader(LdapAuthInterceptor.LDAP_HEADER, "   ")
        }
        val response = MockHttpServletResponse()

        val thrown = assertThrows<CoreException> {
            interceptor.preHandle(request, response, handler)
        }
        assertThat(thrown.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
    }

    @DisplayName("LDAP-03: X-Stay-Ldap 헤더 있으면 통과(true)")
    @Test
    fun allowsWithHeader() {
        val request = MockHttpServletRequest().apply {
            addHeader(LdapAuthInterceptor.LDAP_HEADER, "admin-user")
        }
        val response = MockHttpServletResponse()

        assertThat(interceptor.preHandle(request, response, handler)).isTrue()
    }
}
