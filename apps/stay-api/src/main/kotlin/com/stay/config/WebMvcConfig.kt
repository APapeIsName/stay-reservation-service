package com.stay.config

import com.stay.interfaces.api.admin.LdapAuthInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Web MVC 구성 — 어드민 경로(api-admin 하위 전체)에 LDAP 인증 인터셉터를 등록한다.
 *
 * 어드민 슬라이스만 게이트를 거치므로 패턴을 좁혀 일반 API(api 하위)에는 영향이 없다.
 */
@Configuration
class WebMvcConfig(
    private val ldapAuthInterceptor: LdapAuthInterceptor,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(ldapAuthInterceptor).addPathPatterns("/api-admin/**")
    }
}
