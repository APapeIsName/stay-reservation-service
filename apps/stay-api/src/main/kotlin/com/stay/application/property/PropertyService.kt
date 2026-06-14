package com.stay.application.property

import com.stay.domain.dailyroom.DailyRoomRepository
import com.stay.domain.dailyroom.StayAvailabilityService
import com.stay.domain.property.DisplayStatus
import com.stay.domain.property.Property
import com.stay.domain.property.PropertyRepository
import com.stay.domain.property.SearchCriteria
import com.stay.domain.property.SortType
import com.stay.domain.reservation.DateRange
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * 숙소 조회 유스케이스 오케스트레이션 — Property + RoomType + DailyRoom 조립.
 *
 * getPropertyDetail 흐름:
 *  1. DateRange(checkIn, checkOut) 생성 — 역전·0박은 도메인 VO 가 BAD_REQUEST (rule 06)
 *  2. Property 조회 → 미존재 또는 HIDDEN 이면 NOT_FOUND — 존재 은폐 (S-5 잠정)
 *  3. RoomType 필터 — canAccommodate(guests) && 기간 가용 (StayAvailabilityService.isAvailable 위임, S-4 잠정)
 *  4. 가용 RoomType 별 quote(...).totalPrice() 합산가 산출 — 조회 경로도 예약과 동일 견적 재사용 (Q3)
 *
 * search 흐름:
 *  1. DateRange(checkIn, checkOut) 생성 — 0박·역전 BAD_REQUEST 위임
 *  2. findByCity → VISIBLE 만 (S-5 잠정 — HIDDEN 검색 제외)
 *  3. property 별 가용 RoomType 합산가 산출 (상세 조회와 동일 헬퍼 공유) → 가용 0건 숙소 제외
 *  4. 정렬 — PRICE_ASC: 합산가 최소값 오름차순 / WISHES_DESC: 찜 수 내림차순 /
 *     RECOMMENDED·RATING_DESC: 입력 순서 유지 fallback (S-1·S-2 잠정 — 기준 확정 시 갱신)
 *  5. 페이지네이션 — drop(page * size).take(size)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.5 PropertyService (PSVC-01~21)
 *  - .claude/rules/19-layered-architecture-dip.md (port 주입, Service 는 얇게)
 */
@Component
class PropertyService(
    private val propertyRepository: PropertyRepository,
    private val dailyRoomRepository: DailyRoomRepository,
    private val stayAvailabilityService: StayAvailabilityService,
) {
    @Transactional(readOnly = true)
    fun getPropertyDetail(
        propertyId: Long,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
    ): PropertyDetailInfo {
        val period = DateRange(checkIn, checkOut)
        val property = propertyRepository.findById(propertyId)
            ?.takeIf { it.displayStatus != DisplayStatus.HIDDEN }
            ?: throw CoreException(ErrorType.NOT_FOUND, "숙소를 찾을 수 없습니다.")
        return PropertyDetailInfo(
            propertyId = property.id,
            name = property.name,
            wishCount = property.wishCount,
            roomTypes = availableRoomTypeOptions(property, period, guests),
        )
    }

    @Transactional(readOnly = true)
    fun search(criteria: SearchCriteria): List<PropertySearchItemInfo> {
        val period = DateRange(criteria.checkIn, criteria.checkOut)
        val items = propertyRepository.findByCity(criteria.city)
            .filter { it.displayStatus == DisplayStatus.VISIBLE }
            .mapNotNull { property ->
                val options = availableRoomTypeOptions(property, period, criteria.guests)
                if (options.isEmpty()) {
                    return@mapNotNull null
                }
                PropertySearchItemInfo(
                    propertyId = property.id,
                    name = property.name,
                    minTotalPrice = options.minOf { it.totalPrice },
                    wishCount = property.wishCount,
                )
            }
        val sorted = when (criteria.sort) {
            SortType.PRICE_ASC -> items.sortedBy { it.minTotalPrice }
            SortType.WISHES_DESC -> items.sortedByDescending { it.wishCount }
            SortType.RECOMMENDED, SortType.RATING_DESC -> items
        }
        return sorted.drop(criteria.page * criteria.size).take(criteria.size)
    }

    /**
     * 인원·기간 가용 필터를 통과한 RoomType 별 합산가 옵션 — 상세 조회·검색이 공유하는 단일 소유 로직 (Q3).
     */
    private fun availableRoomTypeOptions(
        property: Property,
        period: DateRange,
        guests: Int,
    ): List<PropertyDetailInfo.RoomTypeOption> = property.roomTypes
        .filter { it.canAccommodate(guests) }
        .mapNotNull { roomType ->
            val dailyRooms = dailyRoomRepository.findByRoomTypeAndDateBetween(
                roomTypeId = roomType.id,
                from = period.checkIn,
                to = period.checkOut.minusDays(1),
            )
            if (!stayAvailabilityService.isAvailable(period, dailyRooms)) {
                return@mapNotNull null
            }
            PropertyDetailInfo.RoomTypeOption(
                roomTypeId = roomType.id,
                name = roomType.name,
                maxGuestCount = roomType.maxGuestCount,
                totalPrice = stayAvailabilityService.quote(period, dailyRooms).totalPrice(),
            )
        }
}
