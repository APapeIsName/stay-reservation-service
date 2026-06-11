package com.stay.interfaces.api.v1.wishlist

import com.stay.interfaces.api.ApiResponse
import com.stay.testcontainers.MySqlTestContainersConfig
import com.stay.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

/**
 * 카탈로그: E2E-WSH (신규 채번) — Wishlist V1 API E2E.
 * DB 시드 없이 검증 가능한 에러 경로 중심 — 미존재 숙소 찜 등록.
 *
 * 환경 주의: Testcontainers + Docker. testcontainers-docker-desktop-incompat 메모리 참조.
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig::class)
class WishlistV1ApiE2ETest
    @Autowired
    constructor(
        private val restTemplate: TestRestTemplate,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {

        @AfterEach
        fun cleanUp() {
            databaseCleanUp.truncateAllTables()
        }

        companion object {
            private val RESPONSE_REF = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
        }

        private fun userHeaders(): HttpHeaders = HttpHeaders().apply { set("X-USER-ID", "1") }

        @DisplayName("E2E-WSH-01: 미존재 숙소 찜 등록 → 404 NOT_FOUND")
        @Test
        fun returnsNotFound_whenPropertyMissing() {
            val response =
                restTemplate.exchange(
                    "/api/v1/properties/999999/wishes",
                    HttpMethod.POST,
                    HttpEntity<Any>(null, userHeaders()),
                    RESPONSE_REF,
                )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(response.body!!.meta.errorCode).isEqualTo("Not Found")
        }
    }
