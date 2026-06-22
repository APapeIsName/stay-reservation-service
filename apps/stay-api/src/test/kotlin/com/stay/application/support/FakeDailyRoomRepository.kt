package com.stay.application.support

import com.stay.domain.dailyroom.DailyRoom
import com.stay.domain.dailyroom.DailyRoomRepository
import java.time.LocalDate

/**
 * DailyRoomRepository 인메모리 Fake — (roomTypeId, date) 자연키 저장.
 * 객체 참조를 그대로 보관하므로 consumeOne/releaseOne 변이가 즉시 "영속" 된 것처럼 보인다.
 */
class FakeDailyRoomRepository : DailyRoomRepository {

    private val store = mutableListOf<DailyRoom>()

    fun seed(dailyRoom: DailyRoom) {
        store.add(dailyRoom)
    }

    fun find(roomTypeId: Long, date: String): DailyRoom? =
        store.find { it.roomTypeId == roomTypeId && it.date == LocalDate.parse(date) }

    override fun findByRoomTypeAndDateBetween(roomTypeId: Long, from: LocalDate, to: LocalDate): List<DailyRoom> =
        store.filter { it.roomTypeId == roomTypeId && !it.date.isBefore(from) && !it.date.isAfter(to) }

    override fun findForReserve(roomTypeId: Long, from: LocalDate, to: LocalDate): List<DailyRoom> =
        findByRoomTypeAndDateBetween(roomTypeId, from, to).sortedBy { it.date }
}
