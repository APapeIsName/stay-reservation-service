package com.stay.domain.reservation

import com.stay.domain.property.Address
import com.stay.domain.property.Amenity
import com.stay.domain.property.BedConfiguration
import com.stay.domain.property.BedEntry
import com.stay.domain.property.BedType
import com.stay.domain.property.CancellationPolicy
import com.stay.domain.property.City
import com.stay.domain.property.DisplayStatus
import com.stay.domain.property.Property
import com.stay.domain.property.PropertyType
import com.stay.domain.property.RefundRule
import com.stay.domain.property.RoomType
import com.stay.domain.property.ViewType
import com.stay.domain.user.Name
import com.stay.domain.user.PhoneNumber
import com.stay.support.assertConflict
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.3 Reservation.confirm (RSV-01~05)
 * 규칙: 생성 = 즉시 CONFIRMED (ADR-002, private ctor + 정적 팩토리), 스냅샷 4종 보존 (시점 일관성),
 *      totalPrice 단일 출처 = priceSnapshot.totalPrice(), canAccommodate 위반 → BAD_REQUEST.
 *      Q3 확정: confirm 은 PriceSnapshot 수취 (DailyRoom 목록 아님 — 타 Aggregate 객체 의존 제거)
 */
@Tag("unit")
class ReservationTest {

    private val cancellationPolicy =
        CancellationPolicy(listOf(RefundRule(7, 100), RefundRule(3, 50), RefundRule(0, 0)))

    private fun property() = Property(
        id = 10L,
        name = "스테이 제주",
        city = City.JEJU,
        address = Address("애월로 100", "63062"),
        propertyType = PropertyType.HOTEL,
        amenities = setOf(Amenity.WIFI),
        checkInTime = LocalTime.of(15, 0),
        checkOutTime = LocalTime.of(11, 0),
        cancellationPolicy = cancellationPolicy,
        representativeImageUrl = "https://img.example.com/jeju.jpg",
        displayStatus = DisplayStatus.VISIBLE,
        wishCount = 0L,
        roomTypes = emptyList(),
    )

    private fun roomType(maxGuestCount: Int = 3) = RoomType(
        id = 20L,
        name = "디럭스 더블",
        standardGuestCount = 2,
        maxGuestCount = maxGuestCount,
        bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1))),
        sizeSqm = 26,
        viewType = ViewType.OCEAN,
        displayStatus = DisplayStatus.VISIBLE,
    )

    private val period = DateRange(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-03"))

    private val priceSnapshot = PriceSnapshot(
        listOf(
            DailyPriceEntry(LocalDate.parse("2026-07-01"), 100000L),
            DailyPriceEntry(LocalDate.parse("2026-07-02"), 200000L),
        ),
    )

    private val now = LocalDateTime.parse("2026-06-15T10:00")

    private fun confirm(guestCount: Int = 2, maxGuestCount: Int = 3) = Reservation.confirm(
        userId = 1L,
        property = property(),
        roomType = roomType(maxGuestCount),
        period = period,
        guestInfo = GuestInfo(Name("공명선"), PhoneNumber("010-1234-5678"), guestCount),
        priceSnapshot = priceSnapshot,
        now = now,
    )

    @Nested
    @DisplayName("정상 생성 — 즉시 CONFIRMED")
    inner class Confirm {

        @DisplayName("RSV-01: 생성 즉시 CONFIRMED 이고 예약 명세 (객실 타입·기간·인원) 를 보존한다")
        @Test
        fun confirmsImmediately_withReservationFacts() {
            val sut = confirm()
            assertThat(sut.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(sut.id).isEqualTo(0L)
            assertThat(sut.createdAt).isEqualTo(now)
            assertThat(sut.confirmedAt).isEqualTo(now)
            assertThat(sut.cancelledAt).isNull()
            assertThat(sut.userId).isEqualTo(1L)
            assertThat(sut.propertyId).isEqualTo(10L)
            assertThat(sut.roomTypeId).isEqualTo(20L)
            assertThat(sut.period).isEqualTo(period)
            assertThat(sut.guestInfo.guestCount).isEqualTo(2)
        }

        @DisplayName("RSV-02: 스냅샷 4종이 예약 시점 값으로 채워진다 — 시점 일관성")
        @Test
        fun fillsFourSnapshots() {
            val sut = confirm()
            assertThat(sut.propertyNameSnapshot).isEqualTo("스테이 제주")
            assertThat(sut.roomTypeNameSnapshot).isEqualTo("디럭스 더블")
            assertThat(sut.priceSnapshot).isEqualTo(priceSnapshot)
            assertThat(sut.cancellationPolicySnapshot.rules).containsExactly(
                RefundRule(7, 100),
                RefundRule(3, 50),
                RefundRule(0, 0),
            )
        }

        @DisplayName("RSV-03: totalPrice 는 priceSnapshot 합산과 일치한다 — 단일 출처")
        @Test
        fun totalPriceComesFromPriceSnapshot() {
            assertThat(confirm().totalPrice).isEqualTo(300000L)
        }
    }

    @Nested
    @DisplayName("인원 경계 — canAccommodate 위임")
    inner class GuestCapacity {

        @DisplayName("RSV-04: 최대 인원과 동수 (경계) 면 정상 생성된다")
        @Test
        fun confirms_whenGuestCountEqualsMax() {
            assertThat(confirm(guestCount = 3).status).isEqualTo(ReservationStatus.CONFIRMED)
        }

        @DisplayName("RSV-05: 최대 인원 초과면 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenGuestCountExceedsMax() {
            val thrown = assertThrows<CoreException> { confirm(guestCount = 4) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(thrown.customMessage).isEqualTo("최대 인원을 초과했습니다.")
        }
    }

    @Nested
    @DisplayName("취소 — cancel (상태 머신 가드 + 스냅샷 환불)")
    inner class Cancel {

        @DisplayName("RSV-06: 6일 전 취소면 CANCELLED + 50% 환불 결과를 반환한다")
        @Test
        fun cancels_withTierRefund() {
            val sut = confirm()
            val cancelAt = LocalDateTime.parse("2026-06-25T10:00")
            val result = sut.cancel(cancelAt)
            assertThat(sut.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(sut.cancelledAt).isEqualTo(cancelAt)
            assertThat(result.refundAmount).isEqualTo(150000L)
            assertThat(result.cancelledAt).isEqualTo(cancelAt)
        }

        @DisplayName("RSV-07: 11일 전 취소면 100% 환불이다")
        @Test
        fun refundsFull_whenCancelledEarly() {
            val result = confirm().cancel(LocalDateTime.parse("2026-06-20T10:00"))
            assertThat(result.refundAmount).isEqualTo(300000L)
        }

        @DisplayName("RSV-08: CHECKED_IN 상태도 취소 가능 — 체크인 이후라 환불 0원")
        @Test
        fun cancelsCheckedIn_withZeroRefund() {
            val sut = confirm().apply { checkIn() }
            val result = sut.cancel(LocalDateTime.parse("2026-07-02T09:00"))
            assertThat(sut.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(result.refundAmount).isEqualTo(0L)
        }

        @DisplayName("RSV-09: CHECKED_OUT 상태 취소면 CONFLICT — 상태명이 메시지에 노출된다")
        @Test
        fun throwsConflict_whenCheckedOut() {
            val sut = confirm().apply { checkIn() }.apply { checkOut() }
            val thrown = assertConflict { sut.cancel(LocalDateTime.parse("2026-07-04T09:00")) }
            assertThat(thrown.customMessage).isEqualTo("취소할 수 없는 상태입니다: CHECKED_OUT")
        }

        @DisplayName("RSV-10: 이중 취소면 CONFLICT — 첫 취소 시각이 보존된다 (가드가 변이보다 먼저)")
        @Test
        fun throwsConflict_whenAlreadyCancelled() {
            val sut = confirm()
            val firstCancelAt = LocalDateTime.parse("2026-06-25T10:00")
            sut.cancel(firstCancelAt)
            assertConflict { sut.cancel(LocalDateTime.parse("2026-06-26T10:00")) }
            assertThat(sut.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(sut.cancelledAt).isEqualTo(firstCancelAt)
        }

        @DisplayName("RSV-11: refundAmount 단독 호출은 조회 전용 — 상태를 바꾸지 않는다")
        @Test
        fun refundAmountIsReadOnly() {
            val sut = confirm()
            val amount = sut.refundAmount(LocalDateTime.parse("2026-06-25T10:00"))
            assertThat(amount).isEqualTo(150000L)
            assertThat(sut.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(sut.cancelledAt).isNull()
        }
    }

    @Nested
    @DisplayName("취소 가능 판단 — isCancellable")
    inner class IsCancellable {

        @DisplayName("RSV-12: CONFIRMED 와 CHECKED_IN 은 취소 가능하다")
        @Test
        fun cancellable_whenConfirmedOrCheckedIn() {
            assertThat(confirm().isCancellable()).isTrue()
            assertThat(confirm().apply { checkIn() }.isCancellable()).isTrue()
        }

        @DisplayName("RSV-13: CHECKED_OUT 과 CANCELLED 는 취소 불가다")
        @Test
        fun notCancellable_whenTerminal() {
            assertThat(confirm().apply { checkIn() }.apply { checkOut() }.isCancellable()).isFalse()
            assertThat(confirm().apply { cancel(now) }.isCancellable()).isFalse()
        }
    }

    @Nested
    @DisplayName("소유 판단 — belongsTo")
    inner class BelongsTo {

        @DisplayName("RSV-14: 예약자 본인이면 true, 타인이면 false")
        @Test
        fun belongsToOwnerOnly() {
            val sut = confirm()
            assertThat(sut.belongsTo(1L)).isTrue()
            assertThat(sut.belongsTo(2L)).isFalse()
        }
    }

    @Nested
    @DisplayName("상태 머신 — 전이 가드 (Q4)")
    inner class StateTransitions {

        @DisplayName("RSV-15: 상태 enum 은 5종이다 — PENDING 은 자리만 (ADR-002)")
        @Test
        fun statusEnumHasFiveValues() {
            assertThat(ReservationStatus.entries).containsExactly(
                ReservationStatus.PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatus.CHECKED_IN,
                ReservationStatus.CHECKED_OUT,
                ReservationStatus.CANCELLED,
            )
        }

        @DisplayName("RSV-16: CONFIRMED 에서 checkIn 하면 CHECKED_IN 이 된다")
        @Test
        fun checksIn_whenConfirmed() {
            val sut = confirm()
            sut.checkIn()
            assertThat(sut.status).isEqualTo(ReservationStatus.CHECKED_IN)
        }

        @DisplayName("RSV-17: CANCELLED 에서 checkIn 하면 CONFLICT — 상태 유지")
        @Test
        fun throwsConflict_whenCheckingInCancelled() {
            val sut = confirm().apply { cancel(now) }
            assertConflict { sut.checkIn() }
            assertThat(sut.status).isEqualTo(ReservationStatus.CANCELLED)
        }

        @DisplayName("RSV-18: CHECKED_IN 에서 checkOut 하면 CHECKED_OUT 이 된다")
        @Test
        fun checksOut_whenCheckedIn() {
            val sut = confirm().apply { checkIn() }
            sut.checkOut()
            assertThat(sut.status).isEqualTo(ReservationStatus.CHECKED_OUT)
        }

        @DisplayName("RSV-19: 체크인 없이 checkOut 하면 CONFLICT — 상태 유지")
        @Test
        fun throwsConflict_whenCheckingOutBeforeCheckIn() {
            val sut = confirm()
            assertConflict { sut.checkOut() }
            assertThat(sut.status).isEqualTo(ReservationStatus.CONFIRMED)
        }
    }

    @Nested
    @DisplayName("쿠폰 적용 — Round 4 (couponId + 할인 스냅샷)")
    inner class CouponApplied {

        private fun confirmWithCoupon(couponId: Long?, snapshot: PriceSnapshot) = Reservation.confirm(
            userId = 1L,
            property = property(),
            roomType = roomType(),
            couponId = couponId,
            period = period,
            guestInfo = GuestInfo(Name("공명선"), PhoneNumber("010-1234-5678"), 2),
            priceSnapshot = snapshot,
            now = now,
        )

        private val discounted = PriceSnapshot.of(
            listOf(
                DailyPriceEntry(LocalDate.parse("2026-07-01"), 100000L),
                DailyPriceEntry(LocalDate.parse("2026-07-02"), 200000L),
            ),
            50000L,
        )

        @DisplayName("RSV2-07: couponId=null (미적용) → couponId null, totalPrice 300000, CONFIRMED")
        @Test
        fun noCoupon() {
            val sut = confirmWithCoupon(couponId = null, snapshot = priceSnapshot)
            assertThat(sut.couponId).isNull()
            assertThat(sut.totalPrice).isEqualTo(300000L)
            assertThat(sut.status).isEqualTo(ReservationStatus.CONFIRMED)
        }

        @DisplayName("RSV2-08: couponId=77 + 할인 50000 → couponId 77, totalPrice 250000 (finalPrice)")
        @Test
        fun withCoupon() {
            val sut = confirmWithCoupon(couponId = 77L, snapshot = discounted)
            assertThat(sut.couponId).isEqualTo(77L)
            assertThat(sut.totalPrice).isEqualTo(250000L)
            assertThat(sut.priceSnapshot.priceBeforeDiscount).isEqualTo(300000L)
            assertThat(sut.priceSnapshot.discountAmount).isEqualTo(50000L)
        }

        @DisplayName("RSV2-09: 3금액 일관 — before - discount == final == totalPrice")
        @Test
        fun amountsConsistent() {
            val sut = confirmWithCoupon(couponId = 77L, snapshot = discounted)
            val ps = sut.priceSnapshot
            assertThat(ps.priceBeforeDiscount - ps.discountAmount).isEqualTo(ps.finalPrice)
            assertThat(ps.finalPrice).isEqualTo(sut.totalPrice)
        }

        @DisplayName("RSV2-10: 쿠폰 적용 예약 취소 — 환불은 finalPrice(250000) 기준 50% = 125000")
        @Test
        fun cancelRefundsOnFinalPrice() {
            val sut = confirmWithCoupon(couponId = 77L, snapshot = discounted)
            val result = sut.cancel(LocalDateTime.parse("2026-06-25T10:00"))
            assertThat(sut.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(result.refundAmount).isEqualTo(125000L)
        }

        @DisplayName("RSV2-11: couponId=null 예약 취소도 기존 cancel 불변 (회귀)")
        @Test
        fun noCouponCancelRegression() {
            val sut = confirmWithCoupon(couponId = null, snapshot = priceSnapshot)
            val result = sut.cancel(LocalDateTime.parse("2026-06-25T10:00"))
            assertThat(result.refundAmount).isEqualTo(150000L)
        }
    }
}
