package com.stay.application.coupon

import com.stay.domain.coupon.CouponIssue
import com.stay.domain.coupon.CouponIssueRepository
import com.stay.domain.coupon.CouponRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 쿠폰 발급 유스케이스 오케스트레이션.
 *
 * 흐름:
 *  1. couponId 로 템플릿 존재 확인 → 미존재 시 NOT_FOUND
 *  2. `CouponIssue.issue(...)` 으로 발급분 합성 (발급 시점은 Clock 외부 주입)
 *  3. `CouponIssueRepository.save` 영속
 *  4. `CouponInfo` 매핑 후 반환
 *
 * 발급 시 만료 검사는 하지 않는다 (C-7 — 만료 거절은 사용 시점 CouponIssue.markUsed 책임).
 *
 * myCoupons 흐름:
 *  1. userId 로 발급분 전체 조회 (발급순) → page·size 페이지네이션 (drop/take, PropertyService 동형)
 *  2. 발급분별 템플릿 조회 → 미존재 시 NOT_FOUND
 *  3. `MyCouponInfo.from(issue, coupon, now)` 매핑 — status 는 now·expiredAt 비교로 EXPIRED 파생
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.4 CouponService.issue (CSVC-01~06), myCoupons (CSVC-07~11)
 *  - .claude/rules/08-static-factory-and-clock-injection.md (Clock 외부 주입)
 *  - .claude/rules/19-layered-architecture-dip.md (port 주입, 얇은 오케스트레이션)
 */
@Component
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
    private val clock: Clock,
) {
    @Transactional
    fun issue(command: IssueCommand): CouponInfo {
        val coupon = couponRepository.findById(command.couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        val issue = CouponIssue.issue(command.couponId, command.userId, LocalDateTime.now(clock))
        return CouponInfo.from(couponIssueRepository.save(issue), coupon)
    }

    @Transactional(readOnly = true)
    fun myCoupons(userId: Long, page: Int, size: Int): List<MyCouponInfo> {
        val now = LocalDateTime.now(clock)
        return couponIssueRepository.findByUserId(userId)
            .drop(page * size)
            .take(size)
            .map { issue ->
                val coupon = couponRepository.findById(issue.couponId)
                    ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
                MyCouponInfo.from(issue, coupon, now)
            }
    }
}
