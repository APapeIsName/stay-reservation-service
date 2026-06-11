package com.stay.application.property

import com.stay.application.support.DomainFixtures
import com.stay.application.support.FakeDailyRoomRepository
import com.stay.application.support.FakePropertyRepository
import com.stay.domain.dailyroom.StayAvailabilityService
import com.stay.domain.property.City
import com.stay.domain.property.DisplayStatus
import com.stay.domain.property.SearchCriteria
import com.stay.domain.property.SortType
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
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.5 PropertyService.search (PSVC-08~19·21)
 * 잠정 가정: S-1 — RATING_DESC 는 rating 필드 부재로 기본 정렬 fallback / S-2 — RECOMMENDED 기준 미정 (순서 단언 보류)
 *           S-5 — HIDDEN Property 검색 제외
 * 흐름: 도시 필터 → VISIBLE 필터 → RoomType 가용·합산 (상세 조회와 동일 견적 재사용 — Q3) → 정렬 → 페이지네이션
 */
@Tag("slow-unit")
class PropertySearchTest {

    private val propertyRepository = FakePropertyRepository()
    private val dailyRoomRepository = FakeDailyRoomRepository()
    private val sut = PropertyService(propertyRepository, dailyRoomRepository, StayAvailabilityService())

    /** 단일 RoomType + 2박 DailyRoom 을 갖춘 검색 가능 숙소 시드. */
    private fun seedSearchable(
        propertyId: Long,
        roomTypeId: Long,
        name: String,
        city: City = City.SEOUL,
        wishCount: Long = 0L,
        maxGuestCount: Int = 2,
        firstNightPrice: Long = 75000L,
        secondNightPrice: Long = 75000L,
        includeSecondDay: Boolean = true,
        firstDayReserved: Int = 0,
        displayStatus: DisplayStatus = DisplayStatus.VISIBLE,
    ) {
        val roomType = DomainFixtures.roomType(id = roomTypeId, name = "기본 객실", maxGuestCount = maxGuestCount)
        propertyRepository.seed(
            DomainFixtures.property(
                id = propertyId,
                name = name,
                city = city,
                wishCount = wishCount,
                displayStatus = displayStatus,
                roomTypes = listOf(roomType),
            ),
        )
        dailyRoomRepository.seed(
            DomainFixtures.dailyRoom(roomTypeId, "2026-06-20", reservedRooms = firstDayReserved, pricePerNight = firstNightPrice),
        )
        if (includeSecondDay) {
            dailyRoomRepository.seed(DomainFixtures.dailyRoom(roomTypeId, "2026-06-21", pricePerNight = secondNightPrice))
        }
    }

    private fun search(
        city: City = City.SEOUL,
        guests: Int = 2,
        sort: SortType = SortType.PRICE_ASC,
        page: Int = 0,
        size: Int = 10,
        checkIn: String = "2026-06-20",
        checkOut: String = "2026-06-22",
    ) = sut.search(
        SearchCriteria(
            city = city,
            checkIn = LocalDate.parse(checkIn),
            checkOut = LocalDate.parse(checkOut),
            guests = guests,
            sort = sort,
            page = page,
            size = size,
        ),
    )

    @Nested
    @DisplayName("필터 — 도시·인원·가용·노출")
    inner class Filtering {

        @DisplayName("PSVC-08: 요청 도시의 숙소만 반환한다 (1차 도시 필터)")
        @Test
        fun filtersByCity() {
            seedSearchable(2L, 30L, "스테이 서울역")
            seedSearchable(3L, 31L, "스테이 강남")
            seedSearchable(10L, 32L, "스테이 제주", city = City.JEJU)
            val items = search(City.SEOUL)
            assertThat(items.map { it.propertyId }).containsExactlyInAnyOrder(2L, 3L)
        }

        @DisplayName("PSVC-09: 전 RoomType 이 인원 초과인 숙소는 제외된다 (2차 인원 필터)")
        @Test
        fun excludesProperty_whenNoRoomTypeAccommodates() {
            seedSearchable(2L, 30L, "스테이 서울역", maxGuestCount = 2)
            seedSearchable(3L, 31L, "스테이 강남", maxGuestCount = 4)
            val items = search(City.SEOUL, guests = 3)
            assertThat(items.map { it.propertyId }).containsExactly(3L)
        }

        @DisplayName("PSVC-10: 일자 누락 숙소는 제외된다 — 기간 완전성")
        @Test
        fun excludesProperty_whenDateRowMissing() {
            seedSearchable(2L, 30L, "스테이 서울역")
            seedSearchable(3L, 31L, "스테이 강남", includeSecondDay = false)
            val items = search(City.SEOUL)
            assertThat(items.map { it.propertyId }).containsExactly(2L)
        }

        @DisplayName("PSVC-11: 재고 0 숙소는 제외된다")
        @Test
        fun excludesProperty_whenSoldOut() {
            seedSearchable(2L, 30L, "스테이 서울역")
            seedSearchable(3L, 31L, "스테이 강남", firstDayReserved = 5)
            val items = search(City.SEOUL)
            assertThat(items.map { it.propertyId }).containsExactly(2L)
        }

        @DisplayName("PSVC-12: 항목은 가용 합산가 최소값 (minTotalPrice) 과 찜 수를 함께 노출한다")
        @Test
        fun exposesMinTotalPriceAndWishCount() {
            val r1 = DomainFixtures.roomType(id = 20L, name = "디럭스 더블", maxGuestCount = 2)
            val r2 = DomainFixtures.roomType(id = 21L, name = "패밀리 스위트", maxGuestCount = 4)
            propertyRepository.seed(DomainFixtures.property(id = 10L, wishCount = 7L, roomTypes = listOf(r1, r2)))
            dailyRoomRepository.seed(DomainFixtures.dailyRoom(20L, "2026-06-20", pricePerNight = 100000L))
            dailyRoomRepository.seed(DomainFixtures.dailyRoom(20L, "2026-06-21", pricePerNight = 120000L))
            dailyRoomRepository.seed(DomainFixtures.dailyRoom(21L, "2026-06-20", pricePerNight = 200000L))
            dailyRoomRepository.seed(DomainFixtures.dailyRoom(21L, "2026-06-21", pricePerNight = 220000L))

            val items = search(City.JEJU)

            assertThat(items).hasSize(1)
            assertThat(items.first().minTotalPrice).isEqualTo(220000L)
            assertThat(items.first().wishCount).isEqualTo(7L)
        }

        @DisplayName("PSVC-21: HIDDEN 숙소는 검색에서 제외된다 (S-5 잠정)")
        @Test
        fun excludesHiddenProperty() {
            seedSearchable(2L, 30L, "스테이 서울역")
            seedSearchable(9L, 39L, "스테이 비공개", displayStatus = DisplayStatus.HIDDEN)
            val items = search(City.SEOUL)
            assertThat(items.map { it.propertyId }).containsExactly(2L)
        }
    }

    @Nested
    @DisplayName("정렬 — SortType 4종 (S-1·S-2 잠정)")
    inner class Sorting {

        private fun seedThree() {
            seedSearchable(2L, 30L, "스테이 서울역", wishCount = 5L, firstNightPrice = 75000L, secondNightPrice = 75000L)
            seedSearchable(3L, 31L, "스테이 강남", wishCount = 12L, firstNightPrice = 90000L, secondNightPrice = 90000L)
            seedSearchable(4L, 32L, "스테이 홍대", wishCount = 0L, firstNightPrice = 65000L, secondNightPrice = 65000L)
        }

        @DisplayName("PSVC-13: PRICE_ASC 는 합산가 오름차순이다")
        @Test
        fun sortsByPriceAscending() {
            seedThree()
            val items = search(City.SEOUL, sort = SortType.PRICE_ASC)
            assertThat(items.map { it.propertyId }).containsExactly(4L, 2L, 3L)
        }

        @DisplayName("PSVC-14: WISHES_DESC 는 찜 수 내림차순이다")
        @Test
        fun sortsByWishesDescending() {
            seedThree()
            val items = search(City.SEOUL, sort = SortType.WISHES_DESC)
            assertThat(items.map { it.propertyId }).containsExactly(3L, 2L, 4L)
        }

        @DisplayName("PSVC-15: RECOMMENDED 는 예외 없이 전건 반환한다 — 순서 단언은 S-2 확정 후")
        @Test
        fun recommendedReturnsAll() {
            seedThree()
            assertThat(search(City.SEOUL, sort = SortType.RECOMMENDED)).hasSize(3)
        }

        @DisplayName("PSVC-16: RATING_DESC 는 rating 부재로 기본 정렬 fallback — 예외 없이 전건 반환 (S-1 잠정)")
        @Test
        fun ratingDescFallsBack() {
            seedThree()
            assertThat(search(City.SEOUL, sort = SortType.RATING_DESC)).hasSize(3)
        }
    }

    @Nested
    @DisplayName("페이지네이션·빈 결과·입력 검증")
    inner class PagingAndEdges {

        @DisplayName("PSVC-17: size=2 면 page 0→2건, 1→1건, 2→0건 (초과 페이지는 빈 목록)")
        @Test
        fun paginates() {
            seedSearchable(2L, 30L, "스테이 서울역")
            seedSearchable(3L, 31L, "스테이 강남")
            seedSearchable(4L, 32L, "스테이 홍대")
            assertThat(search(City.SEOUL, page = 0, size = 2)).hasSize(2)
            assertThat(search(City.SEOUL, page = 1, size = 2)).hasSize(1)
            assertThat(search(City.SEOUL, page = 2, size = 2)).isEmpty()
        }

        @DisplayName("PSVC-18: 결과 0건 도시는 빈 목록이다 — 예외 아님")
        @Test
        fun returnsEmpty_whenNoProperty() {
            assertThat(search(City.GANGNEUNG)).isEmpty()
        }

        @DisplayName("PSVC-19: 0박 기간이면 BAD_REQUEST — DateRange 위임")
        @Test
        fun throwsBadRequest_whenZeroNights() {
            val thrown = assertThrows<CoreException> {
                search(City.SEOUL, checkIn = "2026-06-20", checkOut = "2026-06-20")
            }
            assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }
}
