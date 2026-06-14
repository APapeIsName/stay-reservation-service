package com.stay.infrastructure.reservation

import com.stay.domain.reservation.Reservation
import com.stay.domain.reservation.ReservationRepository
import org.springframework.stereotype.Component

/**
 * 도메인 `ReservationRepository` 의 인프라 구현 (DIP — rule 19).
 */
@Component
class ReservationRepositoryImpl(
    private val jpaRepository: ReservationJpaRepository,
) : ReservationRepository {

    override fun findById(id: Long): Reservation? =
        jpaRepository.findById(id).orElse(null)

    override fun save(reservation: Reservation): Reservation =
        jpaRepository.save(reservation)
}
