package com.stay.application.user

import com.stay.domain.user.RawPassword
import com.stay.infrastructure.user.UserJpaRepository
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import com.stay.testcontainers.MySqlTestContainersConfig
import com.stay.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.junit.jupiter.api.Tag

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.10 UserService Integration
 *
 * 환경 주의: Testcontainers + Docker. 로컬 Docker Desktop 29.x 호환 이슈로 실행이 실패할 수 있음
 * (memory: testcontainers-docker-desktop-incompat). 환경 정상화 시점에 자동 검증 가능.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig::class)
@Tag("integration")
class UserServiceIntegrationTest
    @Autowired
    constructor(
        private val sut: UserService,
        private val userJpaRepository: UserJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {

        @AfterEach
        fun cleanUp() {
            databaseCleanUp.truncateAllTables()
        }

        private fun validCommand(loginId: String = "alen2026") =
            SignupCommand(
                loginId = loginId,
                rawPassword = "P@ssw0rd",
                name = "공명선",
                birthDate = "1995-03-15",
                email = "alen@example.com",
                phoneNumber = "010-1234-5678",
            )

        @Nested
        @DisplayName("정상 가입 — DB 영속 검증")
        inner class Valid {

            @DisplayName("FAC-01: 정상 가입 시 DB 저장 + password 컬럼은 BCrypt 해시 (평문 ≠ 컬럼값)")
            @Test
            fun savesToDb_withHashedPassword() {
                val info = sut.signUp(validCommand())

                assertThat(info.id).isPositive()

                val saved = userJpaRepository.findById(info.id).orElseThrow()
                // 평문 비밀번호와 해시값이 달라야 함 (BCrypt 결과)
                assertThat(saved.password.hashedValue).isNotEqualTo("P@ssw0rd")
                // 그러나 matches 는 통과
                assertThat(saved.password.matches(RawPassword("P@ssw0rd"))).isTrue()
            }
        }

        @Nested
        @DisplayName("실패 — 중복 loginId 영속 검증")
        inner class DuplicateLoginId {

            @DisplayName("FAC-02: 같은 loginId 2번 가입 시 CONFLICT, DB 1건만 존재")
            @Test
            fun throwsConflict_andDoesNotInsert_onDuplicate() {
                sut.signUp(validCommand())

                val thrown =
                    assertThrows<CoreException> {
                        sut.signUp(validCommand(loginId = "alen2026"))
                    }

                assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
                assertThat(userJpaRepository.count()).isEqualTo(1L)
            }
        }

        @Nested
        @DisplayName("실패 — 검증 위반 시 DB 미저장 (트랜잭션 롤백)")
        inner class ValidationRollback {

            @DisplayName("FAC-04: password 가 birthDate 포함이면 BAD_REQUEST, DB 미저장")
            @Test
            fun throwsBadRequest_andDoesNotInsert_whenPasswordContainsBirthDate() {
                val invalidCommand = validCommand().copy(rawPassword = "a19950315b!!")

                val thrown = assertThrows<CoreException> { sut.signUp(invalidCommand) }

                assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
                assertThat(userJpaRepository.count()).isZero()
            }
        }
    }
