package com.stay.application.coupon

import com.stay.domain.coupon.Coupon
import java.time.LocalDateTime

/**
 * 쿠폰 템플릿 상세 출력 DTO (어드민 CRUD 결과).
 *  - 도메인 객체(Coupon) 미노출 — 원시 필드만 평탄화 (rule 20)
 *  - type 은 enum.name 으로 직렬화
 */
data class CouponDetailInfo(
    val couponId: Long,
    val name: String,
    val type: String,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: LocalDateTime,
) {
    companion object {
        fun from(coupon: Coupon): CouponDetailInfo =
            CouponDetailInfo(
                couponId = coupon.id,
                name = coupon.name,
                type = coupon.type.name,
                value = coupon.value,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
            )
    }
}
