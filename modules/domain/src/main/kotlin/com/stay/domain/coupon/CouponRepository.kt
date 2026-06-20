package com.stay.domain.coupon

/**
 * Coupon 템플릿의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층 책임 (DIP — rule 19). 발급 유스케이스가 템플릿 존재를
 * 확인하기 위한 id 단건 조회만 노출 — 어드민 CRUD 는 다음 사이클.
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.4 CouponService.issue (CSVC-01~06)
 *  - .claude/rules/19-layered-architecture-dip.md (port 는 domain, adapter 는 infrastructure)
 */
interface CouponRepository {

    /**
     * 쿠폰 템플릿 단건 조회. 미존재 시 null.
     */
    fun findById(id: Long): Coupon?
}
