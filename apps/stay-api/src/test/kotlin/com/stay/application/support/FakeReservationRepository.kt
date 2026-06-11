package com.stay.application.support

import com.stay.domain.reservation.Reservation
import com.stay.domain.reservation.ReservationRepository

/**
 * ReservationRepository 인메모리 Fake.
 *  - 신규 저장 시 순차 id 키 부여 (도메인 객체의 id 필드는 0L 유지 — 실제 id 부여는 JPA/L3 책임)
 *  - 동일 객체 재저장 (cancel 후 save) 은 기존 키 유지 — 중복 적재 방지
 *  - seed(id, ...) 로 취소 테스트용 기존 예약을 임의 id 에 적재
 */
class FakeReservationRepository : ReservationRepository {

    private val store = mutableMapOf<Long, Reservation>()
    private var nextId = 1L

    val count: Int
        get() = store.size

    val saved: List<Reservation>
        get() = store.values.toList()

    fun seed(id: Long, reservation: Reservation) {
        store[id] = reservation
    }

    override fun findById(id: Long): Reservation? = store[id]

    override fun save(reservation: Reservation): Reservation {
        val alreadyStored = store.values.any { it === reservation }
        if (!alreadyStored) {
            store[nextId++] = reservation
        }
        return reservation
    }
}
