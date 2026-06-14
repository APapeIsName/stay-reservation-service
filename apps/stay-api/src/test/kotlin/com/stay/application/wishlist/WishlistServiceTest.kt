package com.stay.application.wishlist

import com.stay.application.support.DomainFixtures
import com.stay.application.support.FakePropertyRepository
import com.stay.application.support.FakeWishlistRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.5 WishlistService (WSVC-01~07)
 * 규칙 (Q5 확정): 중복 등록·미존재 취소 = 멱등 no-op. Service 의 exists 선검사가
 * incrementWish/decrementWish 호출을 가드 — 카운터 이중 증감 원천 차단.
 * 환경: Spring 없이 Fake Repository (발제: Fake/Stub 기반 단위 테스트)
 */
@Tag("slow-unit")
class WishlistServiceTest {

    private fun newFixture(): Triple<WishlistService, FakeWishlistRepository, FakePropertyRepository> {
        val wishlistRepository = FakeWishlistRepository()
        val propertyRepository = FakePropertyRepository()
        val service = WishlistService(wishlistRepository, propertyRepository, DomainFixtures.FIXED_CLOCK)
        return Triple(service, wishlistRepository, propertyRepository)
    }

    @Nested
    @DisplayName("찜 등록 — add")
    inner class Add {

        @DisplayName("WSVC-01: 등록하면 찜 1건 저장 + wishCount 1 증가 (incrementWish 위임)")
        @Test
        fun addsWishlist_andIncrementsCounter() {
            val (sut, wishlistRepository, propertyRepository) = newFixture()
            val property = DomainFixtures.property(id = 10L, wishCount = 0L)
            propertyRepository.seed(property)

            sut.add(userId = 1L, propertyId = 10L)

            assertThat(wishlistRepository.existsByUserIdAndPropertyId(1L, 10L)).isTrue()
            assertThat(wishlistRepository.count).isEqualTo(1)
            assertThat(property.wishCount).isEqualTo(1L)
        }

        @DisplayName("WSVC-02: 이미 찜한 숙소 재등록은 멱등 no-op — 카운터 불변")
        @Test
        fun addIsIdempotent() {
            val (sut, wishlistRepository, propertyRepository) = newFixture()
            val property = DomainFixtures.property(id = 10L, wishCount = 0L)
            propertyRepository.seed(property)
            sut.add(1L, 10L)

            sut.add(1L, 10L)

            assertThat(wishlistRepository.count).isEqualTo(1)
            assertThat(property.wishCount).isEqualTo(1L)
        }

        @DisplayName("WSVC-03: 미존재 숙소 등록이면 NOT_FOUND — 저장 0건")
        @Test
        fun throwsNotFound_whenPropertyMissing() {
            val (sut, wishlistRepository, _) = newFixture()

            val thrown = assertThrows<CoreException> { sut.add(1L, 999L) }

            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(wishlistRepository.count).isEqualTo(0)
        }

        @DisplayName("WSVC-06: 서로 다른 유저의 찜은 누적된다")
        @Test
        fun accumulatesAcrossUsers() {
            val (sut, wishlistRepository, propertyRepository) = newFixture()
            val property = DomainFixtures.property(id = 10L, wishCount = 0L)
            propertyRepository.seed(property)

            sut.add(1L, 10L)
            sut.add(2L, 10L)

            assertThat(wishlistRepository.count).isEqualTo(2)
            assertThat(property.wishCount).isEqualTo(2L)
        }
    }

    @Nested
    @DisplayName("찜 취소 — remove")
    inner class Remove {

        @DisplayName("WSVC-04: 취소하면 찜 삭제 + wishCount 1 감소 (decrementWish 위임)")
        @Test
        fun removesWishlist_andDecrementsCounter() {
            val (sut, wishlistRepository, propertyRepository) = newFixture()
            val property = DomainFixtures.property(id = 10L, wishCount = 0L)
            propertyRepository.seed(property)
            sut.add(1L, 10L)

            sut.remove(1L, 10L)

            assertThat(wishlistRepository.existsByUserIdAndPropertyId(1L, 10L)).isFalse()
            assertThat(property.wishCount).isEqualTo(0L)
        }

        @DisplayName("WSVC-05: 찜하지 않은 숙소 취소는 멱등 no-op — 카운터 음수 방지 (decrementWish 미호출)")
        @Test
        fun removeIsIdempotent() {
            val (sut, _, propertyRepository) = newFixture()
            val property = DomainFixtures.property(id = 10L, wishCount = 0L)
            propertyRepository.seed(property)

            sut.remove(1L, 10L)

            assertThat(property.wishCount).isEqualTo(0L)
        }

        @DisplayName("WSVC-07: 미존재 숙소 취소면 NOT_FOUND — add 와 대칭의 존재 선검증")
        @Test
        fun throwsNotFound_whenPropertyMissing() {
            val (sut, _, _) = newFixture()

            val thrown = assertThrows<CoreException> { sut.remove(1L, 999L) }

            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
