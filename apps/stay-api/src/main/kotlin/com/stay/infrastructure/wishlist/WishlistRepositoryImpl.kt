package com.stay.infrastructure.wishlist

import com.stay.domain.wishlist.Wishlist
import com.stay.domain.wishlist.WishlistId
import com.stay.domain.wishlist.WishlistRepository
import org.springframework.stereotype.Component

/**
 * 도메인 `WishlistRepository` 의 인프라 구현 (DIP — rule 19).
 * 복합 PK (userId, propertyId) 연산을 WishlistId 기반 JPA 표준 연산으로 변환.
 */
@Component
class WishlistRepositoryImpl(
    private val jpaRepository: WishlistJpaRepository,
) : WishlistRepository {

    override fun existsByUserIdAndPropertyId(userId: Long, propertyId: Long): Boolean =
        jpaRepository.existsById(WishlistId(userId, propertyId))

    override fun save(wishlist: Wishlist): Wishlist =
        jpaRepository.save(wishlist)

    override fun deleteByUserIdAndPropertyId(userId: Long, propertyId: Long) {
        jpaRepository.deleteById(WishlistId(userId, propertyId))
    }
}
