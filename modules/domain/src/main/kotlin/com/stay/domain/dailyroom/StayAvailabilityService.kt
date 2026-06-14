package com.stay.domain.dailyroom

import com.stay.domain.reservation.DailyPriceEntry
import com.stay.domain.reservation.DateRange
import com.stay.domain.reservation.PriceSnapshot
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType

/**
 * 숙박 기간(DateRange) × 다중 DailyRoom 협력 규칙의 단일 소유자 — 무상태 Domain Service.
 *  - validateAvailability: ① 기간 완전성 — dailyRooms 날짜가 period.stayDates() 와 정확히 일치 (누락·불일치·중복 → CONFLICT)
 *                          ② 전 일자 차감 가능 — 하나라도 !canConsume() 이면 CONFLICT. 조회 전용 (입력 비변경)
 *  - isAvailable: validateAvailability 의 비예외 쌍둥이 — 기간 완전성 && 전 일자 DailyRoom.isAvailable() (검색·상세의 가용 필터용, 예외-제어흐름 회피)
 *  - quote: stayDates 순으로 정렬된 DailyPriceEntry 목록의 PriceSnapshot 생성 (입력 비변경)
 *  - consumeAll: validateAvailability 선검증 후 전 일자 consumeOne 위임 — 선검증 덕에 all-or-nothing (중간 실패 시 어떤 일자도 차감 안 됨)
 *  - releaseAll: 전 일자 releaseOne 위임 — period 불요 (복원 범위는 호출자가 결정, S-7 분리)
 *  - 개별 일자의 휴실·만실 규칙은 DailyRoom 메서드에 위임 — 본 서비스는 컬렉션 수준 규칙만 소유
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.6 StayAvailabilityService (SAS-01~16)
 *  - docs/round-3/03-questions.md Q3 (기간 × 다중 DailyRoom 협력 규칙의 소유자 확정)
 *  - .claude/rules/18-domain-modeling.md (Domain Service — 무상태, 동일 도메인 경계 내 협력)
 */
class StayAvailabilityService {
    fun validateAvailability(period: DateRange, dailyRooms: List<DailyRoom>) {
        if (!matchesStayDates(period, dailyRooms)) {
            throw CoreException(ErrorType.CONFLICT, "판매 정보가 올바르지 않은 일자가 있습니다.")
        }
        if (dailyRooms.any { !it.canConsume() }) {
            throw CoreException(ErrorType.CONFLICT, "예약이 불가능한 일자가 있습니다.")
        }
    }

    fun isAvailable(period: DateRange, dailyRooms: List<DailyRoom>): Boolean =
        matchesStayDates(period, dailyRooms) && dailyRooms.all { it.isAvailable() }

    fun quote(period: DateRange, dailyRooms: List<DailyRoom>): PriceSnapshot {
        val priceByDate = dailyRooms.associate { it.date to it.pricePerNight }
        return PriceSnapshot(
            period.stayDates().map { date -> DailyPriceEntry(date, priceByDate.getValue(date)) },
        )
    }

    fun consumeAll(period: DateRange, dailyRooms: List<DailyRoom>) {
        validateAvailability(period, dailyRooms)
        dailyRooms.forEach { it.consumeOne() }
    }

    fun releaseAll(dailyRooms: List<DailyRoom>) {
        dailyRooms.forEach { it.releaseOne() }
    }

    private fun matchesStayDates(period: DateRange, dailyRooms: List<DailyRoom>): Boolean =
        dailyRooms.map { it.date }.sorted() == period.stayDates()
}
