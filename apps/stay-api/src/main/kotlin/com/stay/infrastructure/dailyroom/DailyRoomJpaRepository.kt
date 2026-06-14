package com.stay.infrastructure.dailyroom

import com.stay.domain.dailyroom.DailyRoom
import com.stay.domain.dailyroom.DailyRoomId
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

/**
 * Spring Data JPA 진입점 — 복합 자연키 (@EmbeddedId DailyRoomId).
 * 파생 쿼리는 임베디드 키 경로 (`id.roomTypeId`, `id.date`) 로 탐색.
 */
interface DailyRoomJpaRepository : JpaRepository<DailyRoom, DailyRoomId> {
    fun findByIdRoomTypeIdAndIdDateBetween(roomTypeId: Long, from: LocalDate, to: LocalDate): List<DailyRoom>
}
