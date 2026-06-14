package com.stay.interfaces.api.v1.reservation

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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

/**
 * 카탈로그: E2E-RSV (신규 채번) — Reservation V1 API E2E.
 * DB 시드 없이 검증 가능한 에러 경로 중심 — 인원 0 / 미존재 숙소 / 미존재 예약 취소.
 *
 * 환경 주의: Testcontainers + Docker. testcontainers-docker-desktop-incompat 메모리 참조.
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig::class)
class ReservationV1ApiE2ETest
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
            private const val URL = "/api/v1/reservations"
            private val RESERVE_REF =
                object : ParameterizedTypeReference<ApiResponse<ReservationV1Dto.ReserveResponse>>() {}
            private val CANCEL_REF =
                object : ParameterizedTypeReference<ApiResponse<ReservationV1Dto.CancelResponse>>() {}
        }

        private fun userHeaders(): HttpHeaders = HttpHeaders().apply { set("X-USER-ID", "1") }

        private fun validRequest(guestCount: Int = 2): Map<String, Any> =
            mapOf(
                "propertyId" to 999_999L,
                "roomTypeId" to 1L,
                "checkIn" to "2026-07-01",
                "checkOut" to "2026-07-03",
                "guestCount" to guestCount,
                "guestName" to "공명선",
                "guestPhone" to "010-1234-5678",
            )

        @Nested
        @DisplayName("예약 생성 — 실패")
        inner class ReserveFailure {

            @DisplayName("E2E-RSV-01: 투숙 인원 0 → 400 BAD_REQUEST (GuestInfo VO 검증)")
            @Test
            fun returnsBadRequest_whenGuestCountZero() {
                val response =
                    restTemplate.exchange(
                        URL,
                        HttpMethod.POST,
                        HttpEntity(validRequest(guestCount = 0), userHeaders()),
                        RESERVE_REF,
                    )

                assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(response.body!!.meta.errorCode).isEqualTo("Bad Request")
            }

            @DisplayName("E2E-RSV-02: 미존재 숙소 예약 → 404 NOT_FOUND")
            @Test
            fun returnsNotFound_whenPropertyMissing() {
                val response =
                    restTemplate.exchange(
                        URL,
                        HttpMethod.POST,
                        HttpEntity(validRequest(), userHeaders()),
                        RESERVE_REF,
                    )

                assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
                assertThat(response.body!!.meta.errorCode).isEqualTo("Not Found")
            }
        }

        @Nested
        @DisplayName("예약 취소 — 실패")
        inner class CancelFailure {

            @DisplayName("E2E-RSV-03: 미존재 예약 취소 → 404 NOT_FOUND")
            @Test
            fun returnsNotFound_whenReservationMissing() {
                val response =
                    restTemplate.exchange(
                        "$URL/999999/cancel",
                        HttpMethod.POST,
                        HttpEntity<Any>(null, userHeaders()),
                        CANCEL_REF,
                    )

                assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
                assertThat(response.body!!.meta.errorCode).isEqualTo("Not Found")
            }
        }
    }
