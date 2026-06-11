package com.stay.interfaces.api.v1.user

import com.stay.infrastructure.user.UserJpaRepository
import com.stay.interfaces.api.ApiResponse
import com.stay.testcontainers.MySqlTestContainersConfig
import com.stay.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.junit.jupiter.api.Tag

/**
 * 카탈로그: docs/round-1/02-tdd-plan.md  B.11 UserV1Controller E2E
 *
 * 환경 주의: Testcontainers + Docker. testcontainers-docker-desktop-incompat 메모리 참조.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig::class)
@Tag("e2e")
class UserV1ApiE2ETest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val userJpaRepository: UserJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {

        @AfterEach
        fun cleanUp() {
            databaseCleanUp.truncateAllTables()
        }

        companion object {
            private const val URL = "/api/v1/users"
            private val RESPONSE_REF =
                object : ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {}
        }

        private fun validRequest(loginId: String = "alen2026"): Map<String, String> =
            mapOf(
                "loginId" to loginId,
                "password" to "P@ssw0rd",
                "name" to "공명선",
                "birthDate" to "1995-03-15",
                "email" to "alen@example.com",
                "phoneNumber" to "010-1234-5678",
            )

        @Nested
        @DisplayName("정상 가입")
        inner class Valid {

            @DisplayName("API-01: 201 Created + envelope SUCCESS + userId 존재")
            @Test
            fun returnsCreated_withSignupResponse() {
                val response =
                    restTemplate.exchange(URL, HttpMethod.POST, HttpEntity(validRequest()), RESPONSE_REF)

                assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
                val body = response.body!!
                assertThat(body.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
                assertThat(body.data!!.userId).isPositive()
                assertThat(body.data!!.loginId).isEqualTo("alen2026")
            }
        }

        @Nested
        @DisplayName("실패 — 중복 loginId")
        inner class Duplicate {

            @DisplayName("API-02: 같은 loginId 두 번째 가입 → 409 CONFLICT")
            @Test
            fun returnsConflict_onDuplicate() {
                restTemplate.exchange(URL, HttpMethod.POST, HttpEntity(validRequest()), RESPONSE_REF)
                val response =
                    restTemplate.exchange(URL, HttpMethod.POST, HttpEntity(validRequest()), RESPONSE_REF)

                assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
                assertThat(response.body!!.meta.errorCode).isEqualTo("Conflict")
                assertThat(userJpaRepository.count()).isEqualTo(1L)
            }
        }

        @Nested
        @DisplayName("실패 — 필드 검증 위반")
        inner class FieldViolation {

            @DisplayName("API-03a: 잘못된 loginId (3자) → 400 BAD_REQUEST")
            @Test
            fun returnsBadRequest_whenInvalidLoginId() {
                val body = validRequest() + ("loginId" to "abc")
                val response = restTemplate.exchange(URL, HttpMethod.POST, HttpEntity(body), RESPONSE_REF)

                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(response.body!!.meta.errorCode).isEqualTo("Bad Request")
            }

            @DisplayName("API-03b: password 가 birthDate 포함 → 400 BAD_REQUEST")
            @Test
            fun returnsBadRequest_whenPasswordContainsBirthDate() {
                val body = validRequest() + ("password" to "a19950315b!!")
                val response = restTemplate.exchange(URL, HttpMethod.POST, HttpEntity(body), RESPONSE_REF)

                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
        }

        @Nested
        @DisplayName("실패 — 필수 필드 누락")
        inner class MissingField {

            @DisplayName("API-04: phoneNumber 누락 → 400 BAD_REQUEST (HttpMessageNotReadableException 경로)")
            @Test
            fun returnsBadRequest_whenPhoneNumberMissing() {
                val body = validRequest() - "phoneNumber"
                val response = restTemplate.exchange(URL, HttpMethod.POST, HttpEntity(body), RESPONSE_REF)

                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            }
        }
    }
