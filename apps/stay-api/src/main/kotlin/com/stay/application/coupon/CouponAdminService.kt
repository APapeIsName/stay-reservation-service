package com.stay.application.coupon

import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponIssueRepository
import com.stay.domain.coupon.CouponRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 쿠폰 템플릿 어드민 CRUD + 발급내역 조회 유스케이스 오케스트레이션.
 *
 * 흐름 (얇은 오케스트레이션 — 규칙은 도메인 위임, rule 19):
 *  - register: Coupon 생성자로 합성 → save → CouponDetailInfo
 *  - update: findById → 미존재 NOT_FOUND → coupon.update(...) 위임 (Tell Don't Ask) → save → CouponDetailInfo
 *  - delete: deleteById (멱등)
 *  - list: findAll(page, size) → CouponDetailInfo 매핑
 *  - get: findById → 미존재 NOT_FOUND → CouponDetailInfo
 *  - listIssues: findByCouponId(couponId, page, size) → CouponIssueAdminInfo 매핑
 *
 * 어드민 접근 제어(LDAP)는 인터셉터(LdapAuthInterceptor)가 진입 전 차단 — Service 는 비즈니스 흐름만 책임.
 *
 * Refs:
 *  - .claude/rules/19-layered-architecture-dip.md (port 주입, Service 는 얇게)
 *  - .claude/rules/20-package-and-dto-strategy.md (Command 입력 / Info 출력 분리)
 */
@Component
class CouponAdminService(
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
) {
    @Transactional
    fun register(command: CouponTemplateCommand): CouponDetailInfo {
        val coupon = Coupon(
            name = command.name,
            type = command.type,
            value = command.value,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return CouponDetailInfo.from(couponRepository.save(coupon))
    }

    @Transactional
    fun update(couponId: Long, command: CouponTemplateCommand): CouponDetailInfo {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        coupon.update(
            name = command.name,
            type = command.type,
            value = command.value,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return CouponDetailInfo.from(couponRepository.save(coupon))
    }

    @Transactional
    fun delete(couponId: Long) {
        couponRepository.deleteById(couponId)
    }

    @Transactional(readOnly = true)
    fun list(page: Int, size: Int): List<CouponDetailInfo> =
        couponRepository.findAll(page, size).map { CouponDetailInfo.from(it) }

    @Transactional(readOnly = true)
    fun get(couponId: Long): CouponDetailInfo {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        return CouponDetailInfo.from(coupon)
    }

    @Transactional(readOnly = true)
    fun listIssues(couponId: Long, page: Int, size: Int): List<CouponIssueAdminInfo> =
        couponIssueRepository.findByCouponId(couponId, page, size).map { CouponIssueAdminInfo.from(it) }
}
