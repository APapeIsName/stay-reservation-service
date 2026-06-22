package com.stay.application.wishlist

import com.stay.application.support.DomainFixtures
import com.stay.infrastructure.property.PropertyJpaRepository
import com.stay.testcontainers.MySqlTestContainersConfig
import com.stay.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 동시성 — 찜 수 원자적 증가 (ADR-004 §3 / B.5 CC-09).
 * 같은 숙소에 여러 유저가 동시에 찜해도 `wish_count = wish_count + 1` 원자적 UPDATE 가
 * lost update 없이 정확히 N 을 보장한다 (도메인 read-modify-write 가 아니라 상대 증가라 가능).
 *
 * 환경 주의: Testcontainers + Docker. 로컬 Docker Desktop 29.x 호환 이슈로 실행이 차단될 수 있음
 * (memory: testcontainers-docker-desktop-incompat). 컴파일·코드 검증까지 — 실 동시성은 CI/Docker.
 */
@Tag("integration")
@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class WishlistConcurrencyTest
    @Autowired
    constructor(
        private val sut: WishlistService,
        private val propertyJpaRepository: PropertyJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {

        @AfterEach
        fun cleanUp() {
            databaseCleanUp.truncateAllTables()
        }

        @DisplayName("CC-09: 같은 숙소에 10명 동시 찜 → wishCount 정확히 10 (원자적 증가, lost update 0)")
        @Test
        fun wishCountIsAtomic_underConcurrency() {
            val propertyId = propertyJpaRepository.save(DomainFixtures.newProperty()).id

            val threads = 10
            val latch = CountDownLatch(threads)
            val executor = Executors.newFixedThreadPool(threads)

            repeat(threads) { index ->
                executor.submit {
                    try {
                        sut.add(userId = (index + 1).toLong(), propertyId = propertyId)
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
            executor.shutdown()

            val reloaded = propertyJpaRepository.findById(propertyId).orElseThrow()
            assertThat(reloaded.wishCount).isEqualTo(10L)
        }
    }
