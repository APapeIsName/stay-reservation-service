package com.stay.interfaces.api.v1.property

import com.stay.interfaces.api.ApiResponse
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
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

/**
 * 카탈로그: E2E-PRP (신규 채번) — Property V1 API E2E.
 * DB 시드 없이 검증 가능한 경로 중심 — 빈 검색 결과 / 미존재 상세 / 기간 역전.
 *
 * 환경 주의: Testcontainers + Docker. testcontainers-docker-desktop-incompat 메모리 참조.
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig::class)
class PropertyV1ApiE2ETest
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
            private const val URL = "/api/v1/properties"
            private val SEARCH_REF =
                object : ParameterizedTypeReference<ApiResponse<PropertyV1Dto.SearchResponse>>() {}
            private val DETAIL_REF =
                object : ParameterizedTypeReference<ApiResponse<PropertyV1Dto.DetailResponse>>() {}
        }

        @Nested
        @DisplayName("검색")
        inner class Search {

            @DisplayName("E2E-PRP-01: 시드 없는 도시 검색 → 200 OK + envelope SUCCESS + 빈 items")
            @Test
            fun returnsOk_withEmptyItems() {
                val response =
                    restTemplate.exchange(
                        "$URL?city=SEOUL&checkIn=2026-07-01&checkOut=2026-07-03&guests=2",
                        HttpMethod.GET,
                        null,
                        SEARCH_REF,
                    )

                assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
                val body = response.body!!
                assertThat(body.meta.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
                assertThat(body.data!!.items).isEmpty()
            }

            @DisplayName("E2E-PRP-03: 기간 역전 (checkIn > checkOut) → 400 BAD_REQUEST")
            @Test
            fun returnsBadRequest_whenPeriodReversed() {
                val response =
                    restTemplate.exchange(
                        "$URL?city=SEOUL&checkIn=2026-07-03&checkOut=2026-07-01&guests=2",
                        HttpMethod.GET,
                        null,
                        SEARCH_REF,
                    )

                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(response.body!!.meta.errorCode).isEqualTo("Bad Request")
            }
        }

        @Nested
        @DisplayName("상세")
        inner class Detail {

            @DisplayName("E2E-PRP-02: 미존재 숙소 상세 조회 → 404 NOT_FOUND")
            @Test
            fun returnsNotFound_whenPropertyMissing() {
                val response =
                    restTemplate.exchange(
                        "$URL/999999?checkIn=2026-07-01&checkOut=2026-07-03&guests=2",
                        HttpMethod.GET,
                        null,
                        DETAIL_REF,
                    )

                assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
                assertThat(response.body!!.meta.errorCode).isEqualTo("Not Found")
            }
        }
    }
