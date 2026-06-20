package com.stay.infrastructure.dailyroom

import com.stay.domain.dailyroom.DailyRoom
import com.stay.domain.dailyroom.DailyRoomId
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/**
 * Spring Data JPA 진입점 — 복합 자연키 (@EmbeddedId DailyRoomId).
 * 파생 쿼리는 임베디드 키 경로 (`id.roomTypeId`, `id.date`) 로 탐색.
 */
interface DailyRoomJpaRepository : JpaRepository<DailyRoom, DailyRoomId> {
    fun findByIdRoomTypeIdAndIdDateBetween(roomTypeId: Long, from: LocalDate, to: LocalDate): List<DailyRoom>

    /**
     * 예약 전용 잠그는 finder — PESSIMISTIC_WRITE (`SELECT ... FOR UPDATE`) + 날짜 ASC 정렬.
     * 다일자 락 획득 순서를 날짜 오름차순으로 고정해 데드락을 회피한다 (ADR-004 §1, B.5).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "SELECT d FROM DailyRoom d " +
            "WHERE d.id.roomTypeId = :roomTypeId AND d.id.date BETWEEN :from AND :to " +
            "ORDER BY d.id.date ASC",
    )
    fun findForReserve(
        @Param("roomTypeId") roomTypeId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<DailyRoom>
}
