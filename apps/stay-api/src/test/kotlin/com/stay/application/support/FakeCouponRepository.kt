package com.stay.application.support

import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponRepository

/**
 * CouponRepository 인메모리 Fake.
 *  - seed(id, coupon) 로 알려진 id 에 템플릿 적재 (FakeReservationRepository 동형, 도메인 id 0L 유지)
 *  - save 는 신규 객체에 순차 id 키 부여, 동일 객체 재저장(어드민 update)은 기존 키 유지
 *  - findById 는 적재된 템플릿 반환, 미존재 시 null
 *  - findAll/deleteById 는 어드민 목록·삭제 (drop/take, store.remove)
 */
class FakeCouponRepository : CouponRepository {

    private val store = mutableMapOf<Long, Coupon>()
    private var nextId = 1L

    fun seed(id: Long, coupon: Coupon) {
        store[id] = coupon
    }

    override fun save(coupon: Coupon): Coupon {
        val alreadyStored = store.values.any { it === coupon }
        if (!alreadyStored) {
            store[nextId++] = coupon
        }
        return coupon
    }

    override fun findById(id: Long): Coupon? = store[id]

    override fun findAll(page: Int, size: Int): List<Coupon> =
        store.values.drop(page * size).take(size)

    override fun deleteById(id: Long) {
        store.remove(id)
    }
}
