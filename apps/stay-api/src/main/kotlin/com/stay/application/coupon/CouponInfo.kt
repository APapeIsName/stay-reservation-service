package com.stay.application.coupon

import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponIssue
import java.time.LocalDateTime

/**
 * 쿠폰 발급 결과 출력 DTO.
 *  - 도메인 객체(Coupon·CouponIssue) 미노출 — 원시 필드만 평탄화 (rule 20)
 *  - type·status 는 enum.name 으로 직렬화
 */
data class CouponInfo(
    val couponIssueId: Long,
    val couponId: Long,
    val name: String,
    val type: String,
    val value: Long,
    val status: String,
    val issuedAt: LocalDateTime,
) {
    companion object {
        fun from(issue: CouponIssue, coupon: Coupon): CouponInfo =
            CouponInfo(
                couponIssueId = issue.id,
                couponId = issue.couponId,
                name = coupon.name,
                type = coupon.type.name,
                value = coupon.value,
                status = issue.status.name,
                issuedAt = issue.issuedAt,
            )
    }
}
