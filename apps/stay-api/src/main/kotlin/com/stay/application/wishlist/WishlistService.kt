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
 * 규칙 (Q5): 중복 등록·미존재 취소 = 멱등 no-op. exists 선검사가 incrementWish/decrementWish
 * 호출을 가드하여 wishCount 이중 증감·음수 진입을 원천 차단한다.
 *
 * add 흐름:
 *  1. Property 존재 선검증 → 없으면 NOT_FOUND
 *  2. 이미 찜이면 멱등 return (카운터 불변)
 *  3. Wishlist 저장 (createdAt 은 Clock 주입 — rule 08)
 *  4. property.incrementWish() 위임 + 저장
 *
 * remove 흐름 (add 와 대칭):
 *  1. Property 존재 선검증 → 없으면 NOT_FOUND
 *  2. 찜 없으면 멱등 return (카운터 불변)
 *  3. 찜 삭제
 *  4. property.decrementWish() 위임 + 저장
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.5 WishlistService (WSVC-01~07)
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
        val property = propertyRepository.findById(propertyId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "숙소를 찾을 수 없습니다.")
        if (wishlistRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            return
        }
        wishlistRepository.save(Wishlist(userId, propertyId, LocalDateTime.now(clock)))
        property.incrementWish()
        propertyRepository.save(property)
    }

    @Transactional
    fun remove(userId: Long, propertyId: Long) {
        val property = propertyRepository.findById(propertyId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "숙소를 찾을 수 없습니다.")
        if (!wishlistRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            return
        }
        wishlistRepository.deleteByUserIdAndPropertyId(userId, propertyId)
        property.decrementWish()
        propertyRepository.save(property)
    }
}
