package com.stay.infrastructure.reservation

import com.stay.domain.reservation.Reservation
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA 진입점. 구현은 Spring Data 가 런타임 생성.
 */
interface ReservationJpaRepository : JpaRepository<Reservation, Long>
