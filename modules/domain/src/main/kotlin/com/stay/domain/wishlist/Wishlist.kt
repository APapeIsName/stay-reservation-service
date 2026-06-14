package com.stay.domain.wishlist

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 찜 조인 엔티티 — 유저·숙소 (userId, propertyId) 관계 사실 + 생성 시각.
 *  - 규칙: 도메인 메서드 없음 — 등록/취소 흐름·wishCount 연동은 Application (WSVC-) 책임
 *  - createdAt 외부 주입 (rule 08 — 도메인 내 LocalDateTime.now() 호출 금지)
 *  - 정적 팩토리 없음: 생성 경로가 하나뿐 (잠정 결정)
 *  - 영속: @EmbeddedId WishlistId 보유, userId/propertyId 는 파생 getter (도메인 시그니처 불변 — Q8)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.4 Wishlist (WL-04)
 *  - LLD §5 Wishlist *-- WishlistId / docs/design/04-erd.md §2.11
 */
@Entity
@Table(name = "wishlist")
class Wishlist(
    userId: Long,
    propertyId: Long,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime,
) {
    @EmbeddedId
    val id: WishlistId = WishlistId(userId, propertyId)

    val userId: Long
        get() = id.userId

    val propertyId: Long
        get() = id.propertyId
}
