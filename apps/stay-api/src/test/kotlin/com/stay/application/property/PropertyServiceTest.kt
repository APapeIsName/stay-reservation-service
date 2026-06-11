package com.stay.application.property

import com.stay.application.support.DomainFixtures
import com.stay.application.support.FakeDailyRoomRepository
import com.stay.application.support.FakePropertyRepository
import com.stay.domain.dailyroom.StayAvailabilityService
import com.stay.domain.property.DisplayStatus
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
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.5 PropertyService.getPropertyDetail (PSVC-01~07·20)
 * 잠정 가정: S-4 — 가용 불가 RoomType 은 목록 제외 / S-5 — HIDDEN Property 는 NOT_FOUND (존재 은폐)
 * 환경: Spring 없이 Fake Repository + 실제 StayAvailabilityService (Domain Service 조회 경로 재사용 — Q3)
 */
@Tag("slow-unit")
class PropertyServiceTest {

    private val propertyRepository = FakePropertyRepository()
    private val dailyRoomRepository = FakeDailyRoomRepository()
    private val sut = PropertyService(propertyRepository, dailyRoomRepository, StayAvailabilityService())

    /**
     * 표준 픽스처 (PSVC-01): P1(스테이 제주, wishCount=7) + R1(디럭스 더블, 최대 2인)·R2(패밀리 스위트, 최대 4인),
     * DailyRoom — R1: 6/20 100000·6/21 120000, R2: 6/20 200000·6/21 220000 (전부 total 5, reserved 0)
     */
    private fun seedStandard(
        r1FirstDay: com.stay.domain.dailyroom.DailyRoom = DomainFixtures.dailyRoom(20L, "2026-06-20", pricePerNight = 100000L),
        includeR1SecondDay: Boolean = true,
        propertyDisplayStatus: DisplayStatus = DisplayStatus.VISIBLE,
    ) {
        val r1 = DomainFixtures.roomType(id = 20L, name = "디럭스 더블", maxGuestCount = 2)
        val r2 = DomainFixtures.roomType(id = 21L, name = "패밀리 스위트", maxGuestCount = 4)
        propertyRepository.seed(
            DomainFixtures.property(
                id = 10L,
                wishCount = 7L,
                displayStatus = propertyDisplayStatus,
                roomTypes = listOf(r1, r2),
            ),
        )
        dailyRoomRepository.seed(r1FirstDay)
        if (includeR1SecondDay) {
            dailyRoomRepository.seed(DomainFixtures.dailyRoom(20L, "2026-06-21", pricePerNight = 120000L))
        }
        dailyRoomRepository.seed(DomainFixtures.dailyRoom(21L, "2026-06-20", pricePerNight = 200000L))
        dailyRoomRepository.seed(DomainFixtures.dailyRoom(21L, "2026-06-21", pricePerNight = 220000L))
    }

    private fun detail(propertyId: Long = 10L, guests: Int = 2) = sut.getPropertyDetail(
        propertyId = propertyId,
        checkIn = LocalDate.parse("2026-06-20"),
        checkOut = LocalDate.parse("2026-06-22"),
        guests = guests,
    )

    @Nested
    @DisplayName("정상 조회 — Property + RoomType + DailyRoom 조합 (Application 조립)")
    inner class Detail {

        @DisplayName("PSVC-01: 가용 RoomType 별 기간 합산가와 찜 수를 함께 반환한다")
        @Test
        fun returnsDetail_withQuotesAndWishCount() {
            seedStandard()
            val info = detail()
            assertThat(info.name).isEqualTo("스테이 제주")
            assertThat(info.wishCount).isEqualTo(7L)
            assertThat(info.roomTypes).hasSize(2)
            assertThat(info.roomTypes.first { it.roomTypeId == 20L }.totalPrice).isEqualTo(220000L)
            assertThat(info.roomTypes.first { it.roomTypeId == 21L }.totalPrice).isEqualTo(420000L)
        }

        @DisplayName("PSVC-02: 인원 초과 RoomType 은 제외된다 — canAccommodate 위임 (S-4 잠정)")
        @Test
        fun excludesRoomType_whenGuestsExceedMax() {
            seedStandard()
            val info = detail(guests = 3)
            assertThat(info.roomTypes.map { it.roomTypeId }).containsExactly(21L)
        }

        @DisplayName("PSVC-03: 일자 누락 RoomType 은 제외된다 — 기간 완전성 위임")
        @Test
        fun excludesRoomType_whenDateRowMissing() {
            seedStandard(includeR1SecondDay = false)
            val info = detail()
            assertThat(info.roomTypes.map { it.roomTypeId }).containsExactly(21L)
            assertThat(info.roomTypes.first().totalPrice).isEqualTo(420000L)
        }

        @DisplayName("PSVC-04: 매진 RoomType 은 제외된다 — isAvailable 위임")
        @Test
        fun excludesRoomType_whenSoldOut() {
            seedStandard(r1FirstDay = DomainFixtures.dailyRoom(20L, "2026-06-20", totalRooms = 5, reservedRooms = 5))
            val info = detail()
            assertThat(info.roomTypes.map { it.roomTypeId }).containsExactly(21L)
        }

        @DisplayName("PSVC-05: 휴실 일자 포함 RoomType 은 제외된다")
        @Test
        fun excludesRoomType_whenClosed() {
            seedStandard(r1FirstDay = DomainFixtures.dailyRoom(20L, "2026-06-20", closed = true))
            val info = detail()
            assertThat(info.roomTypes.map { it.roomTypeId }).containsExactly(21L)
        }
    }

    @Nested
    @DisplayName("실패 — 존재·입력 검증")
    inner class Invalid {

        @DisplayName("PSVC-06: 미존재 숙소면 NOT_FOUND")
        @Test
        fun throwsNotFound_whenPropertyMissing() {
            seedStandard()
            val thrown = assertThrows<CoreException> { detail(propertyId = 999L) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("PSVC-07: 기간 역전이면 BAD_REQUEST — DateRange 위임 (rule 06)")
        @Test
        fun throwsBadRequest_whenPeriodReversed() {
            seedStandard()
            val thrown = assertThrows<CoreException> {
                sut.getPropertyDetail(10L, LocalDate.parse("2026-06-22"), LocalDate.parse("2026-06-20"), 2)
            }
            assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("PSVC-20: HIDDEN 숙소면 NOT_FOUND — 존재 은폐 (S-5 잠정)")
        @Test
        fun throwsNotFound_whenPropertyHidden() {
            seedStandard(propertyDisplayStatus = DisplayStatus.HIDDEN)
            val thrown = assertThrows<CoreException> { detail() }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
