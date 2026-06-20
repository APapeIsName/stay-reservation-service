package com.stay.domain.coupon

/**
 * 쿠폰 할인 방식 — 할인 계산 분기 기준 (CPN-11).
 *  - FIXED: 정액 — value 는 할인 원
 *  - RATE : 정률 — value 는 퍼센트 정수
 *
 * 만료·사용 등 상태값은 CouponType 이 아니라 CouponIssueStatus 소관 (혼동 가드).
 *
 * Refs: docs/round-4/02-tdd-plan.md B.1 (CPN-11)
 */
enum class CouponType {
    FIXED,
    RATE,
}
