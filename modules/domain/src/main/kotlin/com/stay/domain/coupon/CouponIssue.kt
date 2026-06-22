package com.stay.domain.coupon

import com.stay.domain.AuditedEntity
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.LocalDateTime

/**
 * 쿠폰 발급분 — Aggregate Root. 사용자에게 발급된 1건의 쿠폰을 추적한다.
 * 생성 진입점은 private 생성자 + 정적 팩토리 issue(...) 뿐 (Reservation.confirm 패턴, rule 08).
 *  - 타 Aggregate(Coupon)는 couponId 로 ID 참조만 한다 (객체 참조 금지, rule 18 §5)
 *  - status·usedAt 의 변이는 markUsed 경유만 — var + private set
 *  - markUsed: 가드 ① USED → CONFLICT(usedAt 보존) ② 만료(now > expiredAt) → BAD_REQUEST(AVAILABLE 유지)
 *    ③ 통과 시 USED + usedAt=now. 가드가 변이보다 먼저 (Reservation.cancel 동형)
 *  - isUsable: AVAILABLE && now <= expiredAt — 만료는 expiredAt 초과부터 (경계 포함)
 *  - @Version 은 발급분에만 — 사용 처리(markUsed)의 낙관락 단위 (ADR-004 §2)
 *
 * Refs:
 *  - docs/round-4/02-tdd-plan.md B.2 CouponIssue (CIS-01~13)
 *  - .claude/rules/08-static-factory-and-clock-injection.md, 18-domain-modeling.md
 */
@Entity
@Table(name = "coupon_issue")
class CouponIssue private constructor(
    @Column(name = "coupon_id", nullable = false)
    val couponId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "issued_at", nullable = false)
    val issuedAt: LocalDateTime,
    status: CouponIssueStatus,
    usedAt: LocalDateTime?,
) : AuditedEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: CouponIssueStatus = status
        private set

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = usedAt
        private set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0L
        private set

    /** 발급분 소유자 판단. */
    fun belongsTo(userId: Long): Boolean = this.userId == userId

    /** 사용 가능 여부 — AVAILABLE 이고 만료(expiredAt) 를 초과하지 않았을 때. 상태 비변경. */
    fun isUsable(now: LocalDateTime, expiredAt: LocalDateTime): Boolean =
        status == CouponIssueStatus.AVAILABLE && !now.isAfter(expiredAt)

    /** 사용 처리 — 가드 통과 시 USED 로 전이한다. 가드가 변이보다 먼저. */
    fun markUsed(now: LocalDateTime, expiredAt: LocalDateTime) {
        if (status != CouponIssueStatus.AVAILABLE) {
            throw CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.")
        }
        if (now.isAfter(expiredAt)) {
            throw CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.")
        }
        status = CouponIssueStatus.USED
        usedAt = now
    }

    companion object {
        fun issue(couponId: Long, userId: Long, now: LocalDateTime): CouponIssue =
            CouponIssue(
                couponId = couponId,
                userId = userId,
                issuedAt = now,
                status = CouponIssueStatus.AVAILABLE,
                usedAt = null,
            )
    }
}
