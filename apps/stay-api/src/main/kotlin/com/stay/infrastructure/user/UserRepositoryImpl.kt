package com.stay.infrastructure.user

import com.stay.domain.user.LoginId
import com.stay.domain.user.User
import com.stay.domain.user.UserRepository
import org.springframework.stereotype.Component

/**
 * 도메인 `UserRepository` 의 인프라 구현. Spring Data JPA 위에서 동작.
 *
 * 단순 위임이지만 의도가 있는 분리:
 *  - 도메인은 `UserRepository` (포트) 만 알고, Spring Data·JPA·MySQL 같은 인프라 세부는 무관
 *  - 향후 캐싱 / 다른 영속 추가 시 본 클래스에서만 변경 (도메인·application 무영향)
 *  - 도메인 → infra 단방향 의존 유지
 */
@Component
class UserRepositoryImpl(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {

    override fun existsByLoginId(loginId: LoginId): Boolean =
        jpaRepository.existsByLoginId(loginId)

    override fun save(user: User): User =
        jpaRepository.save(user)
}
