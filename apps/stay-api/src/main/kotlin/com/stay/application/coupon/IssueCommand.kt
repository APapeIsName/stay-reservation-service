package com.stay.application.coupon

/**
 * 쿠폰 발급 유스케이스 입력 DTO.
 *  - couponId: 발급 대상 템플릿 식별자
 *  - userId: 발급받는 사용자 식별자
 *
 * 웹 무지 — 원시 타입만 보유 (rule 20).
 */
data class IssueCommand(
    val couponId: Long,
    val userId: Long,
)
