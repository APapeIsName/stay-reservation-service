package com.stay.domain.property

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.1 BedEntry VO
 * 규칙: count >= 1 (E.2 잠정 가정 G-9)
 */
@Tag("unit")
class BedEntryTest {

    @DisplayName("BED-01: 침대 종류와 1개 이상 개수면 정상 생성된다 (하한 경계)")
    @Test
    fun valueIsAccepted_whenCountIsLowerBound() {
        val sut = BedEntry(BedType.DOUBLE, 1)
        assertThat(sut.type).isEqualTo(BedType.DOUBLE)
        assertThat(sut.count).isEqualTo(1)
    }

    @DisplayName("BED-02~03: 개수가 1 미만이면 BAD_REQUEST")
    @ParameterizedTest(name = "type={0}, count={1}")
    @CsvSource(
        // BED-02: 0개
        "SINGLE, 0",
        // BED-03: 음수
        "KING, -1",
    )
    fun throwsBadRequest_whenCountIsBelowOne(type: BedType, count: Int) {
        assertBadRequest { BedEntry(type, count) }
    }
}
