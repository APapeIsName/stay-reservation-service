package com.stay.application.support

import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponRepository

/**
 * CouponRepository 인메모리 Fake.
 *  - seed(id, coupon) 로 알려진 id 에 템플릿 적재 (FakeReservationRepository 동형, 도메인 id 0L 유지)
 *  - findById 는 적재된 템플릿 반환, 미존재 시 null
 */
class FakeCouponRepository : CouponRepository {

    private val store = mutableMapOf<Long, Coupon>()

    fun seed(id: Long, coupon: Coupon) {
        store[id] = coupon
    }

    override fun findById(id: Long): Coupon? = store[id]
}
