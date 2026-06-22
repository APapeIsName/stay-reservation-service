package com.stay.interfaces.api.admin.v1.coupon

import com.stay.application.coupon.CouponDetailInfo
import com.stay.application.coupon.CouponIssueAdminInfo
import com.stay.application.coupon.CouponTemplateCommand
import com.stay.domain.coupon.CouponType
import java.time.LocalDateTime

/**
 * 어드민 쿠폰 V1 API DTO container.
 *  - Create/UpdateRequest 는 raw 타입 보관 → toCommand 변환 (rule 20)
 *  - Response 는 application Info 에서 `from` 정적 팩토리로 변환
 *  - type 은 enum 직렬화 (CouponType) — 잘못된 값은 ApiControllerAdvice 가 BAD_REQUEST 매핑
 */
class CouponAdminV1Dto private constructor() {

    data class CreateRequest(
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    ) {
        fun toCommand(): CouponTemplateCommand =
            CouponTemplateCommand(
                name = name,
                type = type,
                value = value,
                minOrderAmount = minOrderAmount,
                expiredAt = expiredAt,
            )
    }

    data class UpdateRequest(
        val name: String,
        val type: CouponType,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    ) {
        fun toCommand(): CouponTemplateCommand =
            CouponTemplateCommand(
                name = name,
                type = type,
                value = value,
                minOrderAmount = minOrderAmount,
                expiredAt = expiredAt,
            )
    }

    data class CouponResponse(
        val couponId: Long,
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long?,
        val expiredAt: LocalDateTime,
    ) {
        companion object {
            fun from(info: CouponDetailInfo): CouponResponse =
                CouponResponse(
                    couponId = info.couponId,
                    name = info.name,
                    type = info.type,
                    value = info.value,
                    minOrderAmount = info.minOrderAmount,
                    expiredAt = info.expiredAt,
                )
        }
    }

    data class IssueResponse(
        val couponIssueId: Long,
        val couponId: Long,
        val userId: Long,
        val status: String,
        val issuedAt: LocalDateTime,
        val usedAt: LocalDateTime?,
    ) {
        companion object {
            fun from(info: CouponIssueAdminInfo): IssueResponse =
                IssueResponse(
                    couponIssueId = info.couponIssueId,
                    couponId = info.couponId,
                    userId = info.userId,
                    status = info.status,
                    issuedAt = info.issuedAt,
                    usedAt = info.usedAt,
                )
        }
    }
}
