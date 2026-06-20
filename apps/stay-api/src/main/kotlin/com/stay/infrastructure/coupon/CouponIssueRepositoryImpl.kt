package com.stay.infrastructure.coupon

import com.stay.domain.coupon.CouponIssue
import com.stay.domain.coupon.CouponIssueRepository
import org.springframework.stereotype.Component

/**
 * 도메인 `CouponIssueRepository` 의 인프라 구현 (DIP — rule 19).
 * markUsed 변이는 트랜잭션 내 dirty checking 으로 영속 — @Version 충돌 시 OptimisticLockingFailureException.
 */
@Component
class CouponIssueRepositoryImpl(
    private val jpaRepository: CouponIssueJpaRepository,
) : CouponIssueRepository {

    override fun save(couponIssue: CouponIssue): CouponIssue =
        jpaRepository.save(couponIssue)

    override fun findByUserId(userId: Long): List<CouponIssue> =
        jpaRepository.findByUserId(userId)

    override fun findById(id: Long): CouponIssue? =
        jpaRepository.findById(id).orElse(null)
}
