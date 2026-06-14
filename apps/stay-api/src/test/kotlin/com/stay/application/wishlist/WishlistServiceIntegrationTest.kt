package com.stay.application.wishlist

import com.stay.application.support.DomainFixtures
import com.stay.domain.wishlist.WishlistId
import com.stay.infrastructure.property.PropertyJpaRepository
import com.stay.infrastructure.wishlist.WishlistJpaRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import com.stay.testcontainers.MySqlTestContainersConfig
import com.stay.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * 찜 등록·취소 유스케이스 영속 검증 — wishlist row (복합 PK) + property.wish_count 카운터 연동,
 * 멱등 no-op (Q5) 의 DB 수준 확인.
 *
 * 환경 주의: Testcontainers + Docker. 로컬 Docker Desktop 29.x 호환 이슈로 실행이 실패할 수 있음
 * (memory: testcontainers-docker-desktop-incompat). 환경 정상화 시점에 자동 검증 가능.
 */
@Tag("integration")
@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class WishlistServiceIntegrationTest
    @Autowired
    constructor(
        private val sut: WishlistService,
        private val wishlistJpaRepository: WishlistJpaRepository,
        private val propertyJpaRepository: PropertyJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {

        @AfterEach
        fun cleanUp() {
            databaseCleanUp.truncateAllTables()
        }

        private fun seedProperty(): Long =
            propertyJpaRepository.save(DomainFixtures.newProperty()).id

        private fun wishCountOf(propertyId: Long): Long =
            propertyJpaRepository.findById(propertyId).orElseThrow().wishCount

        @Nested
        @DisplayName("찜 등록 — add 영속")
        inner class Add {

            @DisplayName("INT-WL-01: add 시 wishlist row 저장 + property.wish_count 1 영속")
            @Test
            fun savesWishlistRow_andIncrementsCounter() {
                val propertyId = seedProperty()

                sut.add(userId = 1L, propertyId = propertyId)

                assertThat(wishlistJpaRepository.existsById(WishlistId(1L, propertyId))).isTrue()
                assertThat(wishlistJpaRepository.count()).isEqualTo(1L)
                assertThat(wishCountOf(propertyId)).isEqualTo(1L)
            }

            @DisplayName("INT-WL-02: 중복 add 는 멱등 no-op — row 1건, wish_count 1 유지")
            @Test
            fun addIsIdempotent_inDb() {
                val propertyId = seedProperty()
                sut.add(1L, propertyId)

                sut.add(1L, propertyId)

                assertThat(wishlistJpaRepository.count()).isEqualTo(1L)
                assertThat(wishCountOf(propertyId)).isEqualTo(1L)
            }
        }

        @Nested
        @DisplayName("찜 취소 — remove 영속")
        inner class Remove {

            @DisplayName("INT-WL-03: remove 시 row 삭제 + wish_count 0 복원 영속")
            @Test
            fun deletesRow_andRestoresCounter() {
                val propertyId = seedProperty()
                sut.add(1L, propertyId)

                sut.remove(1L, propertyId)

                assertThat(wishlistJpaRepository.existsById(WishlistId(1L, propertyId))).isFalse()
                assertThat(wishlistJpaRepository.count()).isZero()
                assertThat(wishCountOf(propertyId)).isZero()
            }
        }

        @Nested
        @DisplayName("실패 — 미존재 숙소")
        inner class PropertyNotFound {

            @DisplayName("INT-WL-04: 미존재 propertyId 로 add 하면 NOT_FOUND — wishlist 미저장")
            @Test
            fun throwsNotFound_andPersistsNothing() {
                val thrown = assertThrows<CoreException> { sut.add(1L, 999L) }

                assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
                assertThat(wishlistJpaRepository.count()).isZero()
            }
        }
    }
