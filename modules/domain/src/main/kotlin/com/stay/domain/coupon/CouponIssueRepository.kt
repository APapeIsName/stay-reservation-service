package com.stay.domain.coupon

/**
 * CouponIssue 발급분의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층 책임 (DIP — rule 19). 발급 유스케이스가 발급분 1건을
 * 저장하기 위한 save 만 노출 — myCoupons 조회는 다음 사이클.
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.4 CouponService.issue (CSVC-01~06)
 *  - .claude/rules/19-layered-architecture-dip.md (port 는 domain, adapter 는 infrastructure)
 */
interface CouponIssueRepository {

    /**
     * 발급분 영속. 저장된 CouponIssue 반환.
     */
    fun save(couponIssue: CouponIssue): CouponIssue
}
