package com.stay.domain.wishlist

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

/**
 * 찜 조인 엔티티 `Wishlist` 의 복합 자연키 VO — (userId, propertyId).
 *  - 규칙: 값 동등성 (동일 userId + propertyId 면 같은 키)
 *  - 검증 없음: ID 값 검증 명세 없음 — 참조 무결성은 application/DB 책임
 *  - 영속: @EmbeddedId (E2 잠정, ERD §2.11) — Serializable 은 JPA 복합키 요구사항
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.4 WishlistId VO
 *  - LLD §5 <<VO (복합 PK)>> / docs/design/04-erd.md §2.11
 */
@Embeddable
data class WishlistId(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "property_id", nullable = false)
    val propertyId: Long,
) : Serializable
