package com.stay.domain.wishlist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.4 Wishlist (WL-04)
 * 규칙: 도메인 메서드 없는 조인 엔티티 — (userId, propertyId) 사실 + 생성 시각 (now 외부 주입, rule 08).
 *      등록/취소 흐름·wishCount 연동 검증은 Application (WSVC-) 책임
 */
@Tag("unit")
class WishlistTest {

    @DisplayName("WL-04: 찜은 유저·숙소 관계 사실과 생성 시각을 보유한다")
    @Test
    fun holdsFactAndCreatedAt() {
        val createdAt = LocalDateTime.of(2026, 6, 10, 12, 0)
        val sut = Wishlist(userId = 1L, propertyId = 10L, createdAt = createdAt)
        assertThat(sut.userId).isEqualTo(1L)
        assertThat(sut.propertyId).isEqualTo(10L)
        assertThat(sut.createdAt).isEqualTo(createdAt)
        assertThat(sut.id).isEqualTo(WishlistId(1L, 10L))
    }
}
