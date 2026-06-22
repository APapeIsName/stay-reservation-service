package com.stay.domain.coupon

/**
 * 발급분(CouponIssue)의 저장 상태 — AVAILABLE, USED 2종만 영속한다.
 * EXPIRED 는 expiredAt 과 now 비교로 매번 파생되는 상태라 저장하지 않는다 (D-C3, CIS-11).
 */
enum class CouponIssueStatus {
    AVAILABLE,
    USED,
}
