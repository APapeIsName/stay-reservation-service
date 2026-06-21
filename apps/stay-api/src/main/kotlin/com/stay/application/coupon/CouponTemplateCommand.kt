package com.stay.application.coupon

import com.stay.domain.coupon.CouponType
import java.time.LocalDateTime

/**
 * 쿠폰 템플릿 등록·수정 유스케이스 입력 DTO (어드민).
 *  - register/update 가 공유하는 단일 입력 모델 — 수정 가능 필드 전체 보유
 *
 * 웹 무지 — 원시 타입·도메인 enum 만 보유 (rule 20).
 */
data class CouponTemplateCommand(
    val name: String,
    val type: CouponType,
    val value: Long,
    val minOrderAmount: Long?,
    val expiredAt: LocalDateTime,
)
