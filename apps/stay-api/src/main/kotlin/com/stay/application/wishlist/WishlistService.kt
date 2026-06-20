package com.stay.application.wishlist

import com.stay.domain.property.PropertyRepository
import com.stay.domain.wishlist.Wishlist
import com.stay.domain.wishlist.WishlistRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 찜 등록·취소 유스케이스 오케스트레이션.
 *
 * 규칙 (Q5): 중복 등록·미존재 취소 = 멱등 no-op. exists 선검사가 카운터 증감 호출을 가드한다.
 * 동시성 (Round 4, ADR-004 §3): wishCount 는 도메인 객체 read-modify-write 대신 **원자적 UPDATE**
 * (`incrementWishCount`/`decrementWishCount`) 로 위임 — 상대 증가라 동시 찜의 lost update 가 원천 불가.
 * 같은 유저 더블파이어 중복은 wishlist 복합 PK (user_id, property_id) 유니크가 DB 에서 차단 (rule 10).
 *
 * add 흐름:
 *  1. Property 존재 선검증 → 없으면 NOT_FOUND
 *  2. 이미 찜이면 멱등 return (카운터 불변)
 *  3. Wishlist 저장 (createdAt 은 Clock 주입 — rule 08)
 *  4. wishCount 원자적 증가 (incrementWishCount)
 *
 * remove 흐름 (add 와 대칭):
 *  1. Property 존재 선검증 → 없으면 NOT_FOUND
 *  2. 찜 없으면 멱등 return (카운터 불변)
 *  3. 찜 삭제
 *  4. wishCount 원자적 감소 (decrementWishCount, wish_count > 0 가드)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.5 WishlistService (WSVC-01~07) / docs/adr/ADR-004-lock-strategy.md §3
 *  - .claude/rules/08-static-factory-and-clock-injection.md (Clock 외부 주입)
 *  - .claude/rules/19-layered-architecture-dip.md (port 주입, Service 는 얇게)
 */
@Component
class WishlistService(
    private val wishlistRepository: WishlistRepository,
    private val propertyRepository: PropertyRepository,
    private val clock: Clock,
) {
    @Transactional
    fun add(userId: Long, propertyId: Long) {
        propertyRepository.findById(propertyId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "숙소를 찾을 수 없습니다.")
        if (wishlistRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            return
        }
        wishlistRepository.save(Wishlist(userId, propertyId, LocalDateTime.now(clock)))
        propertyRepository.incrementWishCount(propertyId)
    }

    @Transactional
    fun remove(userId: Long, propertyId: Long) {
        propertyRepository.findById(propertyId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "숙소를 찾을 수 없습니다.")
        if (!wishlistRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            return
        }
        wishlistRepository.deleteByUserIdAndPropertyId(userId, propertyId)
        propertyRepository.decrementWishCount(propertyId)
    }
}
