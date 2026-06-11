package com.stay.domain.dailyroom

import com.stay.support.assertBadRequest
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

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.2 DailyRoom (DR-11~20 판정·파생 + DR-24~25 요금)
 * 규칙: availableRooms = totalRooms - reservedRooms (파생, 컬럼 X),
 *      isAvailable = !closed && availableRooms() > 0 (closed 가 잔여 수량보다 우선),
 *      canConsume = !closed && reservedRooms < totalRooms,
 *      pricePerNight >= 0 (E3-3 증보)
 */
@Tag("unit")
class DailyRoomTest {

    private fun dailyRoom(
        totalRooms: Int = 10,
        reservedRooms: Int = 0,
        pricePerNight: Long = 100000L,
        closed: Boolean = false,
    ) = DailyRoom(
        roomTypeId = 1L,
        date = LocalDate.parse("2026-07-01"),
        totalRooms = totalRooms,
        reservedRooms = reservedRooms,
        pricePerNight = pricePerNight,
        closed = closed,
    )

    @Nested
    @DisplayName("잔여 수량 — availableRooms (파생 계산)")
    inner class AvailableRooms {

        @DisplayName("DR-11: 10실 중 3실 예약이면 잔여 7")
        @Test
        fun derivesRemainder() {
            assertThat(dailyRoom(reservedRooms = 3).availableRooms()).isEqualTo(7)
        }

        @DisplayName("DR-12: 만실이면 잔여 0")
        @Test
        fun derivesZero_whenFull() {
            assertThat(dailyRoom(reservedRooms = 10).availableRooms()).isEqualTo(0)
        }

        @DisplayName("DR-13: 예약 없으면 잔여 == 총수량")
        @Test
        fun derivesTotal_whenNoReservation() {
            assertThat(dailyRoom(reservedRooms = 0).availableRooms()).isEqualTo(10)
        }
    }

    @Nested
    @DisplayName("판매 가능 판단 — isAvailable")
    inner class IsAvailable {

        @DisplayName("DR-14: 영업 중 + 잔여 있으면 true")
        @Test
        fun available_whenOpenAndRemaining() {
            assertThat(dailyRoom(reservedRooms = 3).isAvailable()).isTrue()
        }

        @DisplayName("DR-15: 만실이면 false")
        @Test
        fun unavailable_whenFull() {
            assertThat(dailyRoom(reservedRooms = 10).isAvailable()).isFalse()
        }

        @DisplayName("DR-16: 휴실이면 잔여 10실이어도 false — closed 가 잔여 수량보다 우선")
        @Test
        fun unavailable_whenClosed() {
            assertThat(dailyRoom(reservedRooms = 0, closed = true).isAvailable()).isFalse()
        }

        @DisplayName("DR-17: 잔여 1실 (경계) 이면 true")
        @Test
        fun available_whenOneRoomLeft() {
            assertThat(dailyRoom(reservedRooms = 9).isAvailable()).isTrue()
        }
    }

    @Nested
    @DisplayName("차감 가능 판단 — canConsume")
    inner class CanConsume {

        @DisplayName("DR-18: 잔여 1실이면 차감 가능")
        @Test
        fun consumable_whenOneRoomLeft() {
            assertThat(dailyRoom(reservedRooms = 9).canConsume()).isTrue()
        }

        @DisplayName("DR-19: 만실이면 차감 불가")
        @Test
        fun notConsumable_whenFull() {
            assertThat(dailyRoom(reservedRooms = 10).canConsume()).isFalse()
        }

        @DisplayName("DR-20: 휴실이면 차감 불가")
        @Test
        fun notConsumable_whenClosed() {
            assertThat(dailyRoom(reservedRooms = 0, closed = true).canConsume()).isFalse()
        }
    }

    @Nested
    @DisplayName("요금 보유 — DailyRoomRate 축 (E3-3 증보)")
    inner class Price {

        @DisplayName("DR-24: 일자별 요금을 보유한다")
        @Test
        fun holdsPricePerNight() {
            assertThat(dailyRoom(pricePerNight = 150000L).pricePerNight).isEqualTo(150000L)
        }

        @DisplayName("DR-25: 음수 요금이면 생성 시 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenPriceIsNegative() {
            assertBadRequest { dailyRoom(pricePerNight = -1L) }
        }
    }

    @Nested
    @DisplayName("재고 차감 — consumeOne (음수·초과 방지가 도메인 레벨)")
    inner class ConsumeOne {

        @DisplayName("DR-01: 차감하면 reservedRooms 가 1 증가한다")
        @Test
        fun consumesOne() {
            val sut = dailyRoom(reservedRooms = 0)
            sut.consumeOne()
            assertThat(sut.reservedRooms).isEqualTo(1)
            assertThat(sut.availableRooms()).isEqualTo(9)
        }

        @DisplayName("DR-02: 만실 직전 (totalRooms-1) 차감으로 만실에 도달한다")
        @Test
        fun reachesFull_whenConsumingLastRoom() {
            val sut = dailyRoom(reservedRooms = 9)
            sut.consumeOne()
            assertThat(sut.reservedRooms).isEqualTo(10)
            assertThat(sut.availableRooms()).isEqualTo(0)
            assertThat(sut.isAvailable()).isFalse()
        }

        @DisplayName("DR-03: 만실이면 CONFLICT — 상태 불변 (초과 차감 방지)")
        @Test
        fun throwsConflict_whenFull() {
            val sut = dailyRoom(reservedRooms = 10)
            val thrown = assertConflict { sut.consumeOne() }
            assertThat(thrown.customMessage).isEqualTo("재고가 부족합니다.")
            assertThat(sut.reservedRooms).isEqualTo(10)
        }

        @DisplayName("DR-04: 휴실이면 CONFLICT — 상태 불변")
        @Test
        fun throwsConflict_whenClosed() {
            val sut = dailyRoom(reservedRooms = 0, closed = true)
            val thrown = assertConflict { sut.consumeOne() }
            assertThat(thrown.customMessage).isEqualTo("휴실 상태입니다.")
            assertThat(sut.reservedRooms).isEqualTo(0)
        }

        @DisplayName("DR-05: 휴실 + 만실 동시면 휴실 가드가 선행한다")
        @Test
        fun closedGuardPrecedesFullGuard() {
            val sut = dailyRoom(reservedRooms = 10, closed = true)
            val thrown = assertConflict { sut.consumeOne() }
            assertThat(thrown.customMessage).isEqualTo("휴실 상태입니다.")
        }

        @DisplayName("DR-06: 최소 재고 (1실) — 1회 차감 후 2회째는 CONFLICT")
        @Test
        fun secondConsumeFails_whenSingleRoom() {
            val sut = dailyRoom(totalRooms = 1, reservedRooms = 0)
            sut.consumeOne()
            assertThat(sut.reservedRooms).isEqualTo(1)
            assertConflict { sut.consumeOne() }
        }

        @DisplayName("DR-22: 재고 0실 행도 차감은 CONFLICT (0 >= 0 가드 적중)")
        @Test
        fun throwsConflict_whenZeroTotalRooms() {
            val sut = dailyRoom(totalRooms = 0, reservedRooms = 0)
            assertConflict { sut.consumeOne() }
            assertThat(sut.reservedRooms).isEqualTo(0)
            assertThat(sut.isAvailable()).isFalse()
        }
    }

    @Nested
    @DisplayName("재고 복원 — releaseOne (consumeOne 과 대칭)")
    inner class ReleaseOne {

        @DisplayName("DR-07: 복원하면 reservedRooms 가 1 감소한다")
        @Test
        fun releasesOne() {
            val sut = dailyRoom(reservedRooms = 3)
            sut.releaseOne()
            assertThat(sut.reservedRooms).isEqualTo(2)
            assertThat(sut.availableRooms()).isEqualTo(8)
        }

        @DisplayName("DR-08: 1실 예약 상태 (하한 직전) 복원으로 0 에 도달한다")
        @Test
        fun reachesZero_whenReleasingLastReservation() {
            val sut = dailyRoom(reservedRooms = 1)
            sut.releaseOne()
            assertThat(sut.reservedRooms).isEqualTo(0)
        }

        @DisplayName("DR-09: 0 에서 복원하면 INTERNAL_ERROR — 음수 방지가 도메인 레벨, 상태 불변")
        @Test
        fun throwsInternalError_whenNothingToRelease() {
            val sut = dailyRoom(reservedRooms = 0)
            val thrown = assertThrows<CoreException> { sut.releaseOne() }
            assertThat(thrown.errorType).isEqualTo(ErrorType.INTERNAL_ERROR)
            assertThat(thrown.customMessage).isEqualTo("복원할 재고가 없습니다.")
            assertThat(sut.reservedRooms).isEqualTo(0)
        }

        @DisplayName("DR-10: 휴실 상태에서도 복원은 허용된다 — 휴실 전환 후 기존 예약 취소 시나리오")
        @Test
        fun releases_whenClosed() {
            val sut = dailyRoom(reservedRooms = 2, closed = true)
            sut.releaseOne()
            assertThat(sut.reservedRooms).isEqualTo(1)
        }

        @DisplayName("DR-21: 차감 후 복원하면 원상 복귀한다 (왕복 항등)")
        @Test
        fun roundTripRestoresOriginalState() {
            val sut = dailyRoom(totalRooms = 5, reservedRooms = 2)
            sut.consumeOne()
            sut.releaseOne()
            assertThat(sut.reservedRooms).isEqualTo(2)
            assertThat(sut.availableRooms()).isEqualTo(3)
        }

        @DisplayName("DR-23: 만실에서 복원하면 가용 상태로 복귀한다 (false → true 전이)")
        @Test
        fun fullRoomBecomesAvailable_afterRelease() {
            val sut = dailyRoom(reservedRooms = 10)
            assertThat(sut.isAvailable()).isFalse()
            sut.releaseOne()
            assertThat(sut.reservedRooms).isEqualTo(9)
            assertThat(sut.availableRooms()).isEqualTo(1)
            assertThat(sut.isAvailable()).isTrue()
        }
    }
}
