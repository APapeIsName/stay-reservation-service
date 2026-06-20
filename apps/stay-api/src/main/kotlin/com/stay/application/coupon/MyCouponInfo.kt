package com.stay.application.coupon

import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponIssue
import com.stay.domain.coupon.CouponIssueStatus
import java.time.LocalDateTime

/**
 * 내 쿠폰 목록 출력 DTO.
 *  - 도메인 객체(Coupon·CouponIssue) 미노출 — 원시 필드만 평탄화 (rule 20)
 *  - status 는 저장 상태(AVAILABLE·USED)에 만료 파생을 더해 3종으로 노출:
 *    USED 우선 → AVAILABLE 이지만 만료(!isUsable) 면 EXPIRED → else AVAILABLE
 *    (EXPIRED 는 저장하지 않고 expiredAt 비교로 파생, CIS-11 정합)
 */
data class MyCouponInfo(
    val couponIssueId: Long,
    val name: String,
    val type: String,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: LocalDateTime,
    val status: String,
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun from(issue: CouponIssue, coupon: Coupon, now: LocalDateTime): MyCouponInfo {
            val status = when {
                issue.status == CouponIssueStatus.USED -> "USED"
                !issue.isUsable(now, coupon.expiredAt) -> "EXPIRED"
                else -> "AVAILABLE"
            }
            return MyCouponInfo(
                couponIssueId = issue.id,
                name = coupon.name,
                type = coupon.type.name,
                value = coupon.value,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
                status = status,
                usedAt = issue.usedAt,
            )
        }
    }
}
