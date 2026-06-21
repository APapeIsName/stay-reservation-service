package com.stay.domain.coupon

/**
 * Coupon 템플릿의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층 책임 (DIP — rule 19). 발급 유스케이스의 id 단건 조회와
 * 어드민 CRUD(save·findAll·deleteById)를 노출한다.
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.4 CouponService.issue (CSVC-01~06)
 *  - .claude/rules/19-layered-architecture-dip.md (port 는 domain, adapter 는 infrastructure)
 */
interface CouponRepository {

    /**
     * 쿠폰 템플릿 영속. 저장된 Coupon 반환 (등록·수정 공용).
     */
    fun save(coupon: Coupon): Coupon

    /**
     * 쿠폰 템플릿 단건 조회. 미존재 시 null.
     */
    fun findById(id: Long): Coupon?

    /**
     * 쿠폰 템플릿 페이지 단위 조회 (어드민 목록).
     */
    fun findAll(page: Int, size: Int): List<Coupon>

    /**
     * 쿠폰 템플릿 단건 삭제 (어드민). 미존재 id 는 멱등.
     */
    fun deleteById(id: Long)
}
