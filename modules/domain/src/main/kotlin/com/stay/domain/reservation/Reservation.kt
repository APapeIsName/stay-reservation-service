package com.stay.domain.reservation

import com.stay.domain.property.CancellationPolicySnapshot
import com.stay.domain.property.Property
import com.stay.domain.property.RoomType
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 예약 — Aggregate Root. 생성 = 즉시 CONFIRMED (ADR-002) — private 생성자 + 정적 팩토리 `confirm(...)` 만이 진입점.
 *  - confirm: roomType.canAccommodate(guestCount) 위반 시 CoreException(BAD_REQUEST) — 가드가 생성보다 먼저
 *  - 타 Aggregate (Property·RoomType·Coupon) 는 ID 만 보관 (객체 참조 금지)
 *  - couponId (Round 4): 예약에 적용된 쿠폰의 감사 추적용 ID. 미적용 예약은 null (CouponIssue 는 별도 Aggregate)
 *  - 스냅샷 4종 (propertyName·roomTypeName·price·cancellationPolicy) 을 예약 시점 값으로 보존 — 시점 일관성 (LLD §4.3)
 *  - totalPrice 단일 출처 = priceSnapshot.totalPrice() (= finalPrice, Round 4 — 쿠폰 할인 후 최종 결제액)
 *  - status·confirmedAt·cancelledAt 의 변이는 상태 머신 메서드 경유만 — var + private set
 *  - cancel: CONFIRMED·CHECKED_IN 에서만 (isCancellable 재사용 — 가능 상태 집합의 단일 출처), 위반 시 CONFLICT.
 *    가드가 변이보다 먼저 (이중 취소 시 cancelledAt 보존). 통과 시 CANCELLED + CancellationResult 반환
 *  - refundAmount: cancellationPolicySnapshot 위임 — 조회 전용 (상태 비변경). totalPrice(=finalPrice) 기준 환불
 *  - checkIn: CONFIRMED → CHECKED_IN / checkOut: CHECKED_IN → CHECKED_OUT — 위반 시 CONFLICT, 상태 유지 (Q4)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.3 Reservation (RSV-01~19) / docs/round-4/02-tdd-plan.md B.3 (RSV2-07~11)
 *  - docs/round-3/03-questions.md Q3·Q4 / ADR-002 (생성 = 즉시 CONFIRMED)
 *  - .claude/rules/08-static-factory-and-clock-injection.md, 18-domain-modeling.md
 */
@Entity
@Table(name = "reservation")
class Reservation private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Column(name = "property_id", nullable = false)
    val propertyId: Long,
    @Column(name = "room_type_id", nullable = false)
    val roomTypeId: Long,
    @Column(name = "coupon_id")
    val couponId: Long?,
    @Embedded
    val period: DateRange,
    @Embedded
    val guestInfo: GuestInfo,
    @Embedded
    val priceSnapshot: PriceSnapshot,
    @Column(name = "total_price", nullable = false)
    val totalPrice: Long,
    @Embedded
    val cancellationPolicySnapshot: CancellationPolicySnapshot,
    @Column(name = "property_name_snapshot", nullable = false, length = 100)
    val propertyNameSnapshot: String,
    @Column(name = "room_type_name_snapshot", nullable = false, length = 50)
    val roomTypeNameSnapshot: String,
    status: ReservationStatus,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime,
    confirmedAt: LocalDateTime?,
    cancelledAt: LocalDateTime?,
) {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ReservationStatus = status
        private set

    @Column(name = "confirmed_at")
    var confirmedAt: LocalDateTime? = confirmedAt
        private set

    @Column(name = "cancelled_at")
    var cancelledAt: LocalDateTime? = cancelledAt
        private set

    /** 취소 — 가드 통과 시 CANCELLED 로 전이하고 환불 결과를 반환한다. 가드가 변이보다 먼저. */
    fun cancel(now: LocalDateTime): CancellationResult {
        if (!isCancellable()) {
            throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 상태입니다: $status")
        }
        val refundAmount = refundAmount(now)
        status = ReservationStatus.CANCELLED
        cancelledAt = now
        return CancellationResult(refundAmount, now)
    }

    /** 환불 예상액 조회 — 스냅샷 위임, 상태 비변경. */
    fun refundAmount(now: LocalDateTime): Long =
        cancellationPolicySnapshot.refundAmount(totalPrice, now, period.checkIn)

    /** 취소 가능 상태 여부 — CONFIRMED·CHECKED_IN. cancel 가드의 단일 출처. */
    fun isCancellable(): Boolean =
        status == ReservationStatus.CONFIRMED || status == ReservationStatus.CHECKED_IN

    /** 예약 소유자 판단. */
    fun belongsTo(userId: Long): Boolean = this.userId == userId

    /** 체크인 — CONFIRMED 에서만 CHECKED_IN 으로 전이. */
    fun checkIn() {
        if (status != ReservationStatus.CONFIRMED) {
            throw CoreException(ErrorType.CONFLICT, "체크인할 수 없는 상태입니다: $status")
        }
        status = ReservationStatus.CHECKED_IN
    }

    /** 체크아웃 — CHECKED_IN 에서만 CHECKED_OUT 으로 전이. */
    fun checkOut() {
        if (status != ReservationStatus.CHECKED_IN) {
            throw CoreException(ErrorType.CONFLICT, "체크아웃할 수 없는 상태입니다: $status")
        }
        status = ReservationStatus.CHECKED_OUT
    }

    companion object {
        fun confirm(
            userId: Long,
            property: Property,
            roomType: RoomType,
            couponId: Long? = null,
            period: DateRange,
            guestInfo: GuestInfo,
            priceSnapshot: PriceSnapshot,
            now: LocalDateTime,
        ): Reservation {
            if (!roomType.canAccommodate(guestInfo.guestCount)) {
                throw CoreException(ErrorType.BAD_REQUEST, "최대 인원을 초과했습니다.")
            }
            return Reservation(
                id = 0L,
                userId = userId,
                propertyId = property.id,
                roomTypeId = roomType.id,
                couponId = couponId,
                period = period,
                guestInfo = guestInfo,
                priceSnapshot = priceSnapshot,
                totalPrice = priceSnapshot.totalPrice(),
                cancellationPolicySnapshot = property.cancellationPolicy.snapshot(),
                propertyNameSnapshot = property.name,
                roomTypeNameSnapshot = roomType.name,
                status = ReservationStatus.CONFIRMED,
                createdAt = now,
                confirmedAt = now,
                cancelledAt = null,
            )
        }
    }
}
