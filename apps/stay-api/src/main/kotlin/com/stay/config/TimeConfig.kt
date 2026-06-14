package com.stay.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Clock 빈 — `UserService` 등 시간 의존 컴포넌트의 `LocalDate.now(clock)` 호출 대상.
 *
 * 테스트에선 `Clock.fixed(...)` 로 교체하여 시간 의존성 결정성 확보 (rule 08).
 */
@Configuration
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemDefaultZone()
}
