package com.stay.infrastructure.dailyroom

import com.stay.domain.dailyroom.DailyRoom
import com.stay.domain.dailyroom.DailyRoomRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * 도메인 `DailyRoomRepository` 의 인프라 구현 (DIP — rule 19).
 * 조회된 관리 엔티티의 consumeOne/releaseOne 변이는 트랜잭션 내 dirty checking 으로 영속.
 */
@Component
class DailyRoomRepositoryImpl(
    private val jpaRepository: DailyRoomJpaRepository,
) : DailyRoomRepository {

    override fun findByRoomTypeAndDateBetween(roomTypeId: Long, from: LocalDate, to: LocalDate): List<DailyRoom> =
        jpaRepository.findByIdRoomTypeIdAndIdDateBetween(roomTypeId, from, to)

    override fun findForReserve(roomTypeId: Long, from: LocalDate, to: LocalDate): List<DailyRoom> =
        jpaRepository.findForReserve(roomTypeId, from, to)
}
