package com.stay.domain.coupon

/**
 * CouponIssue 발급분의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층 책임 (DIP — rule 19). 발급 유스케이스의 save,
 * myCoupons 조회 유스케이스의 findByUserId 를 노출한다.
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.4 CouponService.issue (CSVC-01~06), myCoupons (CSVC-07~11)
 *  - .claude/rules/19-layered-architecture-dip.md (port 는 domain, adapter 는 infrastructure)
 */
interface CouponIssueRepository {

    /**
     * 발급분 영속. 저장된 CouponIssue 반환.
     */
    fun save(couponIssue: CouponIssue): CouponIssue

    /**
     * 유저의 발급분 전체 조회 (발급순). 미발급 유저는 빈 목록.
     */
    fun findByUserId(userId: Long): List<CouponIssue>
}
