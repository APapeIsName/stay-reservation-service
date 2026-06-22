package com.stay.infrastructure.coupon

import com.stay.domain.coupon.Coupon
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA 진입점 — 쿠폰 템플릿. 구현은 Spring Data 가 런타임 생성.
 */
interface CouponJpaRepository : JpaRepository<Coupon, Long>
