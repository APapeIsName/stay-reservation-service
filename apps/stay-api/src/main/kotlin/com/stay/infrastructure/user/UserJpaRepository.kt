package com.stay.infrastructure.user

import com.stay.domain.user.LoginId
import com.stay.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA 진입점. 구현은 Spring Data 가 런타임 생성.
 *
 * 도메인 인터페이스 `com.stay.domain.user.UserRepository` 가 아니라 별도로 둔다 — 도메인은
 * Spring Data 같은 인프라 기술에 무관해야 함. 어댑터(`UserRepositoryImpl`) 가 둘을 연결.
 *
 * `existsByLoginId(LoginId): Boolean` — Spring Data 의 method name derivation.
 * LoginId 는 @Embeddable 이므로 내부 'value' 필드 매칭으로 자동 변환됨.
 */
interface UserJpaRepository : JpaRepository<User, Long> {
    fun existsByLoginId(loginId: LoginId): Boolean
}
