package com.stay.infrastructure.dailyroom

import com.stay.application.support.DomainFixtures
import com.stay.domain.dailyroom.DailyRoomId
import com.stay.testcontainers.MySqlTestContainersConfig
import com.stay.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * DailyRoom 영속 — @EmbeddedId 복합 자연키 (roomTypeId, date) 기간 조회 (경계 포함) +
 * 트랜잭션 내 관리 엔티티 변이의 dirty checking 영속 (DailyRoomRepositoryImpl KDoc 의 전제 검증).
 *
 * 환경 주의: Testcontainers + Docker. 로컬 Docker Desktop 29.x 호환 이슈로 실행이 실패할 수 있음
 * (memory: testcontainers-docker-desktop-incompat). 환경 정상화 시점에 자동 검증 가능.
 */
@Tag("integration")
@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class DailyRoomRepositoryIntegrationTest
    @Autowired
    constructor(
        private val sut: DailyRoomRepositoryImpl,
        private val dailyRoomJpaRepository: DailyRoomJpaRepository,
        private val transactionTemplate: TransactionTemplate,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {

        @AfterEach
        fun cleanUp() {
            databaseCleanUp.truncateAllTables()
        }

        @Nested
        @DisplayName("기간 조회 — findByRoomTypeAndDateBetween")
        inner class FindBetween {

            @DisplayName("INT-DR-01: 4일치 저장 후 from~to 경계 포함 조회 — 가운데 구간만 반환 (다른 roomType 제외)")
            @Test
            fun findsInclusiveRange_filteredByRoomType() {
                dailyRoomJpaRepository.saveAll(
                    listOf(
                        DomainFixtures.dailyRoom(roomTypeId = 20L, date = "2026-07-01"),
                        DomainFixtures.dailyRoom(roomTypeId = 20L, date = "2026-07-02"),
                        DomainFixtures.dailyRoom(roomTypeId = 20L, date = "2026-07-03"),
                        DomainFixtures.dailyRoom(roomTypeId = 20L, date = "2026-07-04"),
                        DomainFixtures.dailyRoom(roomTypeId = 21L, date = "2026-07-02"),
                    ),
                )

                val found = sut.findByRoomTypeAndDateBetween(
                    roomTypeId = 20L,
                    from = LocalDate.parse("2026-07-02"),
                    to = LocalDate.parse("2026-07-03"),
                )

                assertThat(found.map { it.date }).containsExactlyInAnyOrder(
                    LocalDate.parse("2026-07-02"),
                    LocalDate.parse("2026-07-03"),
                )
                assertThat(found).allSatisfy { assertThat(it.roomTypeId).isEqualTo(20L) }
            }
        }

        @Nested
        @DisplayName("변이 영속 — dirty checking")
        inner class DirtyChecking {

            @DisplayName("INT-DR-02: 트랜잭션 내 관리 엔티티 consumeOne() 은 save 없이 reservedRooms 영속 (dirty checking)")
            @Test
            fun persistsConsumeOne_viaDirtyChecking() {
                dailyRoomJpaRepository.save(DomainFixtures.dailyRoom(roomTypeId = 20L, date = "2026-07-01"))

                transactionTemplate.executeWithoutResult {
                    val managed = sut.findByRoomTypeAndDateBetween(
                        roomTypeId = 20L,
                        from = LocalDate.parse("2026-07-01"),
                        to = LocalDate.parse("2026-07-01"),
                    ).single()
                    managed.consumeOne()
                }

                val reloaded = dailyRoomJpaRepository
                    .findById(DailyRoomId(20L, LocalDate.parse("2026-07-01")))
                    .orElseThrow()
                assertThat(reloaded.reservedRooms).isEqualTo(1)
            }
        }
    }
