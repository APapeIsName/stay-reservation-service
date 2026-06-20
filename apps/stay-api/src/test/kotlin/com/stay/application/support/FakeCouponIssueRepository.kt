package com.stay.application.support

import com.stay.domain.coupon.CouponIssue
import com.stay.domain.coupon.CouponIssueRepository

/**
 * CouponIssueRepository 인메모리 Fake.
 *  - 신규 저장 시 순차 id 키 부여 (도메인 객체의 id 필드는 0L 유지 — 실제 id 부여는 JPA/L3 책임)
 *  - 동일 객체 재저장은 기존 키 유지 — 중복 적재 방지 (FakeReservationRepository 동형)
 */
class FakeCouponIssueRepository : CouponIssueRepository {

    private val store = mutableMapOf<Long, CouponIssue>()
    private var nextId = 1L

    val count: Int
        get() = store.size

    val saved: List<CouponIssue>
        get() = store.values.toList()

    override fun save(couponIssue: CouponIssue): CouponIssue {
        val alreadyStored = store.values.any { it === couponIssue }
        if (!alreadyStored) {
            store[nextId++] = couponIssue
        }
        return couponIssue
    }
}
