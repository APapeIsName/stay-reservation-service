package com.stay.domain.wishlist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.4 WishlistId VO
 * 규칙: (userId, propertyId) 복합 PK — 값 동등성 (LLD §5, DailyRoomId 와 동일 방식)
 */
@Tag("unit")
class WishlistIdTest {

    @DisplayName("WL-01: userId 와 propertyId 로 정상 생성된다")
    @Test
    fun valueIsAccepted() {
        val sut = WishlistId(1L, 10L)
        assertThat(sut.userId).isEqualTo(1L)
        assertThat(sut.propertyId).isEqualTo(10L)
    }

    @DisplayName("WL-02: 동일 값이면 equals 와 hashCode 가 같다")
    @Test
    fun instancesAreEqual_whenSameValues() {
        val a = WishlistId(1L, 10L)
        val b = WishlistId(1L, 10L)
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @DisplayName("WL-03: userId 또는 propertyId 하나만 달라도 다른 복합키다")
    @Test
    fun instancesAreNotEqual_whenAnyPartDiffers() {
        val base = WishlistId(1L, 10L)
        assertThat(base).isNotEqualTo(WishlistId(1L, 11L))
        assertThat(base).isNotEqualTo(WishlistId(2L, 10L))
    }
}
