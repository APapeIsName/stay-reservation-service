package com.stay.domain.wishlist

/**
 * Wishlist 의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층 책임 (DIP — rule 19). 복합 PK (userId, propertyId) 기준으로
 * 존재 확인·저장·삭제만 노출 — 등록/취소 멱등 가드는 Application 책임 (Q5).
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.5 WishlistService (WSVC-01~07)
 *  - .claude/rules/19-layered-architecture-dip.md (port 는 domain, adapter 는 infrastructure)
 */
interface WishlistRepository {

    /**
     * (userId, propertyId) 찜 존재 여부. 등록/취소 멱등 선검사용.
     */
    fun existsByUserIdAndPropertyId(userId: Long, propertyId: Long): Boolean

    /**
     * 찜 영속. 저장된 Wishlist 반환.
     */
    fun save(wishlist: Wishlist): Wishlist

    /**
     * (userId, propertyId) 찜 삭제. 미존재 시 no-op.
     */
    fun deleteByUserIdAndPropertyId(userId: Long, propertyId: Long)
}
