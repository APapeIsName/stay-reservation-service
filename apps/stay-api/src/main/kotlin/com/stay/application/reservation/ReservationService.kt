package com.stay.application.reservation

import com.stay.domain.coupon.CouponIssueRepository
import com.stay.domain.coupon.CouponRepository
import com.stay.domain.dailyroom.DailyRoomRepository
import com.stay.domain.dailyroom.StayAvailabilityService
import com.stay.domain.property.DisplayStatus
import com.stay.domain.property.PropertyRepository
import com.stay.domain.reservation.DateRange
import com.stay.domain.reservation.GuestInfo
import com.stay.domain.reservation.PriceSnapshot
import com.stay.domain.reservation.Reservation
import com.stay.domain.reservation.ReservationRepository
import com.stay.domain.user.Name
import com.stay.domain.user.PhoneNumber
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * 예약 생성·취소 유스케이스 오케스트레이션 — Property + RoomType + DailyRoom + Reservation 조립.
 *
 * reserve 흐름 (선검증 순서가 곧 명세 — 실패 시 차감·저장 0 보장):
 *  1. DateRange(checkIn, checkOut) — 0박·역전은 도메인 VO 가 BAD_REQUEST (rule 06)
 *  2. GuestInfo(Name, PhoneNumber, guestCount) — 투숙자 형식·인원 VO 검증
 *  3. Property 조회 → 미존재 NOT_FOUND / findRoomType → 미존재 NOT_FOUND
 *  4. RoomType HIDDEN 이면 BAD_REQUEST — 예약 불가 노출 상태
 *  5. DailyRoom 조회 범위 checkIn ~ checkOut-1 — 체크아웃 당일 미차감
 *  6. validateAvailability — 기간 완전성·재고·휴실은 StayAvailabilityService 위임 (CONFLICT)
 *  7. quote (baseSnapshot) — 재고 선검증이 쿠폰보다 먼저 (매진 시 markUsed 미호출, RSVC2-10)
 *  8. 쿠폰 적용 (couponId 있을 때) — 발급분 조회(NOT_FOUND)·소유(FORBIDDEN)·calculateDiscount(BAD_REQUEST)
 *     ·markUsed(CONFLICT/BAD_REQUEST) → 할인 반영 PriceSnapshot. 미적용이면 baseSnapshot 그대로
 *  9. Reservation.confirm — 인원 가드가 차감보다 먼저 (스냅샷·Clock 시각 보존)
 * 10. consumeAll — 선검증 덕에 all-or-nothing 차감 → 저장 후 ReservationInfo 반환
 *
 * cancel 흐름 (소유 검증이 재고·상태 변동보다 먼저):
 *  1. Reservation 조회 → 미존재 NOT_FOUND
 *  2. belongsTo(userId) 위반 시 FORBIDDEN — 재고·상태 변동 전 (E3-6)
 *  3. reservation.cancel(now) — 상태 머신 가드는 도메인 위임 (CONFLICT), CHECKED_IN 취소 허용
 *  4. 재고 복원 — checkIn ~ checkOut-1 전 기간 releaseAll (S-7 잠정).
 *     가드 실패 시 여기 도달 안 함 — 이중 복원 방지
 *  5. 저장 후 CancellationInfo 반환
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.5 ReservationService (RSVC-01~21)
 *  - .claude/rules/08-static-factory-and-clock-injection.md (Clock 주입)
 *  - .claude/rules/19-layered-architecture-dip.md (port 주입, Service 는 얇게)
 */
@Component
class ReservationService(
    private val propertyRepository: PropertyRepository,
    private val dailyRoomRepository: DailyRoomRepository,
    private val reservationRepository: ReservationRepository,
    private val couponRepository: CouponRepository,
    private val couponIssueRepository: CouponIssueRepository,
    private val stayAvailabilityService: StayAvailabilityService,
    private val clock: Clock,
) {
    @Transactional
    fun reserve(userId: Long, command: ReserveCommand): ReservationInfo {
        val period = DateRange(command.checkIn, command.checkOut)
        val guestInfo = GuestInfo(
            guestName = Name(command.guestName),
            guestPhone = PhoneNumber(command.guestPhone),
            guestCount = command.guestCount,
        )
        val property = propertyRepository.findById(command.propertyId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "숙소를 찾을 수 없습니다.")
        val roomType = property.findRoomType(command.roomTypeId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "객실 타입을 찾을 수 없습니다.")
        if (roomType.displayStatus == DisplayStatus.HIDDEN) {
            throw CoreException(ErrorType.BAD_REQUEST, "예약할 수 없는 객실 타입입니다.")
        }
        val dailyRooms = dailyRoomRepository.findForReserve(
            roomTypeId = roomType.id,
            from = period.checkIn,
            to = period.checkOut.minusDays(1),
        )
        stayAvailabilityService.validateAvailability(period, dailyRooms)
        val now = LocalDateTime.now(clock)
        val baseSnapshot = stayAvailabilityService.quote(period, dailyRooms)
        val priceSnapshot = command.couponId?.let { couponId ->
            val issue = couponIssueRepository.findById(couponId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
            if (!issue.belongsTo(userId)) {
                throw CoreException(ErrorType.FORBIDDEN, "본인의 쿠폰만 사용할 수 있습니다.")
            }
            val coupon = couponRepository.findById(issue.couponId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
            val discount = coupon.calculateDiscount(baseSnapshot.priceBeforeDiscount)
            issue.markUsed(now, coupon.expiredAt)
            PriceSnapshot.of(baseSnapshot.entries, discount)
        } ?: baseSnapshot
        val reservation = Reservation.confirm(
            userId = userId,
            property = property,
            roomType = roomType,
            couponId = command.couponId,
            period = period,
            guestInfo = guestInfo,
            priceSnapshot = priceSnapshot,
            now = now,
        )
        stayAvailabilityService.consumeAll(period, dailyRooms)
        return ReservationInfo.from(reservationRepository.save(reservation))
    }

    @Transactional
    fun cancel(userId: Long, reservationId: Long): CancellationInfo {
        val reservation = reservationRepository.findById(reservationId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "예약을 찾을 수 없습니다.")
        if (!reservation.belongsTo(userId)) {
            throw CoreException(ErrorType.FORBIDDEN, "본인의 예약만 취소할 수 있습니다.")
        }
        val result = reservation.cancel(LocalDateTime.now(clock))
        val dailyRooms = dailyRoomRepository.findForReserve(
            roomTypeId = reservation.roomTypeId,
            from = reservation.period.checkIn,
            to = reservation.period.checkOut.minusDays(1),
        )
        stayAvailabilityService.releaseAll(dailyRooms)
        reservationRepository.save(reservation)
        return CancellationInfo(result.refundAmount, result.cancelledAt, reservation.status)
    }
}
