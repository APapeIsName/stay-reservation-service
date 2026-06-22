package com.stay.interfaces.api.v1.coupon

import com.stay.application.coupon.CouponInfo
import com.stay.application.coupon.MyCouponInfo
import java.time.LocalDateTime

/**
 * 쿠폰 V1 API DTO container.
 *  - Response 는 application Info 에서 `from` 정적 팩토리로 변환 (rule 20)
 *  - 내 쿠폰 status 는 AVAILABLE/USED/EXPIRED 파생 문자열 (D-C3)
 */
class CouponV1Dto private constructor() {

    data class IssueResponse(
        val couponIssueId: Long,
        val couponId: Long,
        val name: String,
        val type: String,
        val value: Long,
        val status: String,
        val issuedAt: LocalDateTime,
    ) {
        companion object {
            fun from(info: CouponInfo): IssueResponse =
                IssueResponse(
                    couponIssueId = info.couponIssueId,
                    couponId = info.couponId,
                    name = info.name,
                    type = info.type,
                    value = info.value,
                    status = info.status,
                    issuedAt = info.issuedAt,
                )
        }
    }

    data class MyCouponResponse(
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
            fun from(info: MyCouponInfo): MyCouponResponse =
                MyCouponResponse(
                    couponIssueId = info.couponIssueId,
                    name = info.name,
                    type = info.type,
                    value = info.value,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                    status = info.status,
                    usedAt = info.usedAt,
                )
        }
    }
}
