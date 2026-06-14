package com.stay.infrastructure.wishlist

import com.stay.domain.wishlist.Wishlist
import com.stay.domain.wishlist.WishlistId
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA 진입점 — 복합 자연키 (@EmbeddedId WishlistId).
 */
interface WishlistJpaRepository : JpaRepository<Wishlist, WishlistId>
