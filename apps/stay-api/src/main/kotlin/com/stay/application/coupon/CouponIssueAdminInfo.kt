package com.stay.application.coupon

import com.stay.domain.coupon.CouponIssue
import java.time.LocalDateTime

/**
 * 쿠폰 발급내역 출력 DTO (어드민 — 템플릿별 발급분 조회).
 *  - 도메인 객체(CouponIssue) 미노출 — 원시 필드만 평탄화 (rule 20)
 *  - status 는 발급분 저장 상태(AVAILABLE·USED) 그대로 — 만료 파생은 사용자용 MyCouponInfo 책임,
 *    어드민 발급내역은 저장 사실을 그대로 노출한다
 */
data class CouponIssueAdminInfo(
    val couponIssueId: Long,
    val couponId: Long,
    val userId: Long,
    val status: String,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun from(issue: CouponIssue): CouponIssueAdminInfo =
            CouponIssueAdminInfo(
                couponIssueId = issue.id,
                couponId = issue.couponId,
                userId = issue.userId,
                status = issue.status.name,
                issuedAt = issue.issuedAt,
                usedAt = issue.usedAt,
            )
    }
}
