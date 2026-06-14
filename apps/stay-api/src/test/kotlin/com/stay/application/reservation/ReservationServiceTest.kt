package com.stay.application.reservation

import com.stay.application.support.DomainFixtures
import com.stay.application.support.FakeDailyRoomRepository
import com.stay.application.support.FakePropertyRepository
import com.stay.application.support.FakeReservationRepository
import com.stay.domain.dailyroom.StayAvailabilityService
import com.stay.domain.property.CancellationPolicy
import com.stay.domain.property.DisplayStatus
import com.stay.domain.property.RefundRule
import com.stay.domain.reservation.DateRange
import com.stay.domain.reservation.GuestInfo
import com.stay.domain.reservation.Reservation
import com.stay.domain.reservation.ReservationStatus
import com.stay.domain.user.Name
import com.stay.domain.user.PhoneNumber
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
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.5 ReservationService.reserve (RSVC-01~13)
 * 흐름 검증 핵심: 선검증 순서 — 존재(NOT_FOUND) → 노출(BAD_REQUEST) → 가용(CONFLICT, SAS 위임)
 * → confirm (인원 가드, 차감 전) → consumeAll (all-or-nothing) → 저장.
 * Fake 는 rollback 이 없으므로 "실패 시 차감 0" 단언이 곧 선검증 순서의 증명이다.
 */
@Tag("slow-unit")
class ReservationServiceTest {

    private val propertyRepository = FakePropertyRepository()
    private val dailyRoomRepository = FakeDailyRoomRepository()
    private val reservationRepository = FakeReservationRepository()
    private val sut = ReservationService(
        propertyRepository = propertyRepository,
        dailyRoomRepository = dailyRoomRepository,
        reservationRepository = reservationRepository,
        stayAvailabilityService = StayAvailabilityService(),
        clock = DomainFixtures.FIXED_CLOCK,
    )

    /** 표준 픽스처: P1(스테이 제주) + R1(디럭스 더블, 최대 2인), DailyRoom 6/20 (100000)·6/21 (120000), total 5 */
    private fun seedStandard(
        roomTypeDisplayStatus: DisplayStatus = DisplayStatus.VISIBLE,
        firstDay: com.stay.domain.dailyroom.DailyRoom = DomainFixtures.dailyRoom(20L, "2026-06-20", pricePerNight = 100000L),
        secondDay: com.stay.domain.dailyroom.DailyRoom? = DomainFixtures.dailyRoom(20L, "2026-06-21", pricePerNight = 120000L),
    ) {
        val r1 = DomainFixtures.roomType(id = 20L, name = "디럭스 더블", maxGuestCount = 2, displayStatus = roomTypeDisplayStatus)
        propertyRepository.seed(DomainFixtures.property(id = 10L, roomTypes = listOf(r1)))
        dailyRoomRepository.seed(firstDay)
        secondDay?.let { dailyRoomRepository.seed(it) }
    }

    private fun command(
        propertyId: Long = 10L,
        roomTypeId: Long = 20L,
        checkIn: String = "2026-06-20",
        checkOut: String = "2026-06-22",
        guestCount: Int = 2,
    ) = ReserveCommand(
        propertyId = propertyId,
        roomTypeId = roomTypeId,
        checkIn = LocalDate.parse(checkIn),
        checkOut = LocalDate.parse(checkOut),
        guestCount = guestCount,
        guestName = "공명선",
        guestPhone = "010-1234-5678",
    )

    private fun reservedOf(date: String): Int = dailyRoomRepository.find(20L, date)!!.reservedRooms

    @Nested
    @DisplayName("정상 예약 — 차감·스냅샷·시각")
    inner class Reserve {

        @DisplayName("RSVC-01: 예약하면 CONFIRMED + 합산가 + 전 일자 차감 + 1건 저장 (인원 경계 동수 포함)")
        @Test
        fun reservesAndConsumes() {
            seedStandard()
            val info = sut.reserve(1L, command(guestCount = 2))
            assertThat(info.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(info.totalPrice).isEqualTo(220000L)
            assertThat(reservationRepository.count).isEqualTo(1)
            assertThat(reservedOf("2026-06-20")).isEqualTo(1)
            assertThat(reservedOf("2026-06-21")).isEqualTo(1)
        }

        @DisplayName("RSVC-02: 저장된 예약은 스냅샷 4종과 Clock 주입 시각을 보존한다")
        @Test
        fun persistsSnapshotsAndClockTime() {
            seedStandard()
            sut.reserve(1L, command())
            val saved = reservationRepository.saved.single()
            assertThat(saved.propertyNameSnapshot).isEqualTo("스테이 제주")
            assertThat(saved.roomTypeNameSnapshot).isEqualTo("디럭스 더블")
            assertThat(saved.priceSnapshot.entries.map { it.pricePerNight }).containsExactly(100000L, 120000L)
            assertThat(saved.cancellationPolicySnapshot.rules).contains(RefundRule(7, 100))
            assertThat(saved.createdAt).isEqualTo(DomainFixtures.NOW)
            assertThat(saved.confirmedAt).isEqualTo(DomainFixtures.NOW)
        }

        @DisplayName("RSVC-03: 1박 (경계 하한) 은 체크인 일자만 차감한다")
        @Test
        fun consumesOnlyCheckInDate_whenOneNight() {
            seedStandard()
            val info = sut.reserve(1L, command(checkOut = "2026-06-21"))
            assertThat(info.totalPrice).isEqualTo(100000L)
            assertThat(reservedOf("2026-06-20")).isEqualTo(1)
            assertThat(reservedOf("2026-06-21")).isEqualTo(0)
        }

        @DisplayName("RSVC-04: 체크아웃 당일은 차감하지 않는다 — 조회 범위 checkIn~checkOut-1")
        @Test
        fun doesNotConsumeCheckOutDate() {
            seedStandard()
            dailyRoomRepository.seed(DomainFixtures.dailyRoom(20L, "2026-06-22", pricePerNight = 130000L))
            sut.reserve(1L, command())
            assertThat(reservedOf("2026-06-22")).isEqualTo(0)
        }

        @DisplayName("RSVC-12: 마지막 1실 (total 1) 도 예약 가능 — 차감 후 잔여 0 허용 경계")
        @Test
        fun reservesLastRoom() {
            seedStandard(
                firstDay = DomainFixtures.dailyRoom(20L, "2026-06-20", totalRooms = 1),
                secondDay = DomainFixtures.dailyRoom(20L, "2026-06-21", totalRooms = 1),
            )
            val info = sut.reserve(1L, command())
            assertThat(info.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(reservedOf("2026-06-20")).isEqualTo(1)
            assertThat(reservedOf("2026-06-21")).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("예외 예약 — 선검증 순서가 차감 0 을 보장")
    inner class ReserveFailure {

        @DisplayName("RSVC-05: 미존재 숙소면 NOT_FOUND — 저장·차감 0")
        @Test
        fun throwsNotFound_whenPropertyMissing() {
            seedStandard()
            val thrown = assertThrows<CoreException> { sut.reserve(1L, command(propertyId = 999L)) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(reservationRepository.count).isEqualTo(0)
        }

        @DisplayName("RSVC-06: 미존재 객실 타입이면 NOT_FOUND — findRoomType null 위임")
        @Test
        fun throwsNotFound_whenRoomTypeMissing() {
            seedStandard()
            val thrown = assertThrows<CoreException> { sut.reserve(1L, command(roomTypeId = 999L)) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(reservationRepository.count).isEqualTo(0)
        }

        @DisplayName("RSVC-07: HIDDEN 객실 타입이면 BAD_REQUEST — 차감·저장 0")
        @Test
        fun throwsBadRequest_whenRoomTypeHidden() {
            seedStandard(roomTypeDisplayStatus = DisplayStatus.HIDDEN)
            val thrown = assertThrows<CoreException> { sut.reserve(1L, command()) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(reservedOf("2026-06-20")).isEqualTo(0)
        }

        @DisplayName("RSVC-08: 인원 초과면 BAD_REQUEST — confirm 가드가 차감보다 먼저 (reservedRooms 변동 0)")
        @Test
        fun throwsBadRequest_whenGuestCountExceedsMax_beforeConsuming() {
            seedStandard()
            val thrown = assertThrows<CoreException> { sut.reserve(1L, command(guestCount = 3)) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(reservedOf("2026-06-20")).isEqualTo(0)
            assertThat(reservedOf("2026-06-21")).isEqualTo(0)
            assertThat(reservationRepository.count).isEqualTo(0)
        }

        @DisplayName("RSVC-09: 중간 일자 재고 부족이면 CONFLICT — 앞 일자도 차감 0 (all-or-nothing)")
        @Test
        fun throwsConflict_whenAnyDateSoldOut() {
            seedStandard(secondDay = DomainFixtures.dailyRoom(20L, "2026-06-21", totalRooms = 5, reservedRooms = 5))
            val thrown = assertThrows<CoreException> { sut.reserve(1L, command()) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(reservedOf("2026-06-20")).isEqualTo(0)
            assertThat(reservationRepository.count).isEqualTo(0)
        }

        @DisplayName("RSVC-10: 일자 누락이면 CONFLICT — 기간 완전성 위임")
        @Test
        fun throwsConflict_whenDateRowMissing() {
            seedStandard(secondDay = null)
            val thrown = assertThrows<CoreException> { sut.reserve(1L, command()) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(reservedOf("2026-06-20")).isEqualTo(0)
        }

        @DisplayName("RSVC-11: 휴실 일자 포함이면 CONFLICT — 다른 일자 차감 0")
        @Test
        fun throwsConflict_whenAnyDateClosed() {
            seedStandard(firstDay = DomainFixtures.dailyRoom(20L, "2026-06-20", closed = true))
            val thrown = assertThrows<CoreException> { sut.reserve(1L, command()) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(reservedOf("2026-06-21")).isEqualTo(0)
        }

        @DisplayName("RSVC-13: 0박 기간이면 BAD_REQUEST — DateRange 위임")
        @Test
        fun throwsBadRequest_whenZeroNights() {
            seedStandard()
            val thrown = assertThrows<CoreException> {
                sut.reserve(1L, command(checkIn = "2026-06-20", checkOut = "2026-06-20"))
            }
            assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    /**
     * 취소 픽스처: id=100 의 CONFIRMED 예약 (userId=1) + 기간 일자별 DailyRoom (reservedRooms 기본 1).
     * 기본 기간 6/20~6/22 → now(6/10) 기준 10일 전 = 100% 환불 구간.
     */
    private fun seedReservation(
        checkIn: String = "2026-06-20",
        checkOut: String = "2026-06-22",
        policy: CancellationPolicy = DomainFixtures.cancellationPolicy(),
        reservedRooms: Int = 1,
    ): Reservation {
        val r1 = DomainFixtures.roomType(id = 20L, name = "디럭스 더블", maxGuestCount = 2)
        val property = DomainFixtures.property(id = 10L, cancellationPolicy = policy, roomTypes = listOf(r1))
        propertyRepository.seed(property)
        val period = DateRange(LocalDate.parse(checkIn), LocalDate.parse(checkOut))
        period.stayDates().forEachIndexed { index, date ->
            dailyRoomRepository.seed(
                DomainFixtures.dailyRoom(
                    20L,
                    date.toString(),
                    reservedRooms = reservedRooms,
                    pricePerNight = if (index == 0) 100000L else 120000L,
                ),
            )
        }
        val dailyRooms = dailyRoomRepository.findByRoomTypeAndDateBetween(20L, period.checkIn, period.checkOut.minusDays(1))
        val reservation = Reservation.confirm(
            userId = 1L,
            property = property,
            roomType = r1,
            period = period,
            guestInfo = GuestInfo(Name("공명선"), PhoneNumber("010-1234-5678"), 2),
            priceSnapshot = StayAvailabilityService().quote(period, dailyRooms),
            now = DomainFixtures.NOW,
        )
        reservationRepository.seed(100L, reservation)
        return reservation
    }

    @Nested
    @DisplayName("취소 — cancel (소유 검증 + 상태 머신 위임 + 재고 복원)")
    inner class Cancel {

        @DisplayName("RSVC-14: 10일 전 취소면 100% 환불 결과 + CANCELLED 저장")
        @Test
        fun cancels_withFullRefund() {
            seedReservation()
            val info = sut.cancel(1L, 100L)
            assertThat(info.refundAmount).isEqualTo(220000L)
            assertThat(info.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(info.cancelledAt).isEqualTo(DomainFixtures.NOW)
            assertThat(reservationRepository.findById(100L)!!.status).isEqualTo(ReservationStatus.CANCELLED)
        }

        @DisplayName("RSVC-15: 기간 전 일자가 복원되고 (releaseOne 대칭), 체크아웃 일자는 비대상")
        @Test
        fun releasesAllStayDates() {
            seedReservation()
            dailyRoomRepository.seed(DomainFixtures.dailyRoom(20L, "2026-06-22", reservedRooms = 0))
            sut.cancel(1L, 100L)
            assertThat(reservedOf("2026-06-20")).isEqualTo(0)
            assertThat(reservedOf("2026-06-21")).isEqualTo(0)
            assertThat(reservedOf("2026-06-22")).isEqualTo(0)
        }

        @DisplayName("RSVC-16: 현재 정책이 바뀌어도 예약 시점 스냅샷으로 환불한다 — 시점 일관성")
        @Test
        fun refundsBySnapshot_whenCurrentPolicyChanged() {
            val r1 = seedReservation()
            propertyRepository.seed(
                DomainFixtures.property(
                    id = 10L,
                    cancellationPolicy = CancellationPolicy(listOf(RefundRule(0, 0))),
                    roomTypes = emptyList(),
                ),
            )
            val info = sut.cancel(1L, 100L)
            assertThat(info.refundAmount).isEqualTo(220000L)
            assertThat(r1.status).isEqualTo(ReservationStatus.CANCELLED)
        }

        @DisplayName("RSVC-17: 미존재 예약이면 NOT_FOUND")
        @Test
        fun throwsNotFound_whenReservationMissing() {
            seedReservation()
            val thrown = assertThrows<CoreException> { sut.cancel(1L, 999L) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("RSVC-18: 타인 예약 취소면 FORBIDDEN — belongsTo 위임, 재고·상태 불변 (E3-6)")
        @Test
        fun throwsForbidden_whenNotOwner() {
            seedReservation()
            val thrown = assertThrows<CoreException> { sut.cancel(2L, 100L) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.FORBIDDEN)
            assertThat(reservationRepository.findById(100L)!!.status).isEqualTo(ReservationStatus.CONFIRMED)
            assertThat(reservedOf("2026-06-20")).isEqualTo(1)
        }

        @DisplayName("RSVC-19: 이미 취소된 예약 재취소면 CONFLICT — 이중 복원 방지 (releaseOne 미호출)")
        @Test
        fun throwsConflict_whenAlreadyCancelled() {
            val reservation = seedReservation(reservedRooms = 0)
            reservation.cancel(DomainFixtures.NOW)
            val thrown = assertThrows<CoreException> { sut.cancel(1L, 100L) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(reservedOf("2026-06-20")).isEqualTo(0)
        }

        @DisplayName("RSVC-20: CHECKED_OUT 예약 취소면 CONFLICT — terminal 상태")
        @Test
        fun throwsConflict_whenCheckedOut() {
            val reservation = seedReservation()
            reservation.checkIn()
            reservation.checkOut()
            val thrown = assertThrows<CoreException> { sut.cancel(1L, 100L) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("RSVC-21: CHECKED_IN 예약도 취소 가능 — 체크인 이후라 환불 0 (스냅샷 위임)")
        @Test
        fun cancelsCheckedIn_withZeroRefund() {
            val reservation = seedReservation(
                checkIn = "2026-06-01",
                checkOut = "2026-06-03",
                policy = CancellationPolicy(listOf(RefundRule(0, 0))),
            )
            reservation.checkIn()
            val info = sut.cancel(1L, 100L)
            assertThat(info.status).isEqualTo(ReservationStatus.CANCELLED)
            assertThat(info.refundAmount).isEqualTo(0L)
        }
    }
}
