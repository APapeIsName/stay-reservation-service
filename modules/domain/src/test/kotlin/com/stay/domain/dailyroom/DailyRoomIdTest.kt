package com.stay.domain.dailyroom

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.2 DailyRoomId VO
 * 규칙: (roomTypeId, date) 복합 자연키 — 값 동등성 (LLD §3.2)
 */
@Tag("unit")
class DailyRoomIdTest {

    @DisplayName("DRID-01: roomTypeId 와 date 로 정상 생성된다")
    @Test
    fun valueIsAccepted() {
        val sut = DailyRoomId(1L, LocalDate.parse("2026-07-01"))
        assertThat(sut.roomTypeId).isEqualTo(1L)
        assertThat(sut.date).isEqualTo(LocalDate.parse("2026-07-01"))
    }

    @DisplayName("DRID-02: 동일 값이면 equals 와 hashCode 가 같다")
    @Test
    fun instancesAreEqual_whenSameValues() {
        val a = DailyRoomId(1L, LocalDate.parse("2026-07-01"))
        val b = DailyRoomId(1L, LocalDate.parse("2026-07-01"))
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @DisplayName("DRID-03: roomTypeId 또는 date 하나만 달라도 다른 복합키다")
    @Test
    fun instancesAreNotEqual_whenAnyPartDiffers() {
        val base = DailyRoomId(1L, LocalDate.parse("2026-07-01"))
        assertThat(base).isNotEqualTo(DailyRoomId(1L, LocalDate.parse("2026-07-02")))
        assertThat(base).isNotEqualTo(DailyRoomId(2L, LocalDate.parse("2026-07-01")))
    }
}
