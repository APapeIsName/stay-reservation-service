package com.stay.infrastructure.coupon

import com.stay.domain.coupon.CouponIssue
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA 진입점 — 쿠폰 발급분.
 * `@Version` (CouponIssue.version) 으로 단일사용 낙관락 — 동일 발급분 동시 markUsed 시
 * 한 트랜잭션만 커밋하고 나머지는 OptimisticLockingFailureException (ADR-004 §2).
 */
interface CouponIssueJpaRepository : JpaRepository<CouponIssue, Long> {
    fun findByUserId(userId: Long): List<CouponIssue>

    fun findByCouponId(couponId: Long, pageable: Pageable): List<CouponIssue>
}
