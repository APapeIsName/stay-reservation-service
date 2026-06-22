package com.stay.infrastructure.coupon

import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

/**
 * 도메인 `CouponRepository` 의 인프라 구현 (DIP — rule 19).
 * 쿠폰 템플릿은 읽기 위주·어드민 수정만이라 락 없음 (@Version 없음, ADR-004).
 */
@Component
class CouponRepositoryImpl(
    private val jpaRepository: CouponJpaRepository,
) : CouponRepository {

    override fun save(coupon: Coupon): Coupon =
        jpaRepository.save(coupon)

    override fun findById(id: Long): Coupon? =
        jpaRepository.findById(id).orElse(null)

    override fun findAll(page: Int, size: Int): List<Coupon> =
        jpaRepository.findAll(PageRequest.of(page, size)).content

    override fun deleteById(id: Long) =
        jpaRepository.deleteById(id)
}
