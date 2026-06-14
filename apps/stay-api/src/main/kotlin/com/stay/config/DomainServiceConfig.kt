package com.stay.config

import com.stay.domain.dailyroom.StayAvailabilityService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Domain Service 빈 등록.
 *
 * `StayAvailabilityService` 는 modules/domain 의 무상태 클래스 — 도메인이 Spring 에 의존하지 않도록
 * (@Component 미부착) 조립은 application 쪽 구성이 담당한다 (rule 19 의존 방향).
 */
@Configuration
class DomainServiceConfig {

    @Bean
    fun stayAvailabilityService(): StayAvailabilityService = StayAvailabilityService()
}
