package com.stay.domain.property

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.1 BedConfiguration VO
 * 규칙: entries 빈 목록 불가 (E.2 잠정 가정 G-9)
 */
@Tag("unit")
class BedConfigurationTest {

    @DisplayName("BED-04: 침대 구성 목록이면 정상 생성되고 순서·값이 보존된다")
    @Test
    fun valueIsAccepted_whenEntriesGiven() {
        val sut = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1), BedEntry(BedType.SINGLE, 2)))
        assertThat(sut.entries).containsExactly(
            BedEntry(BedType.DOUBLE, 1),
            BedEntry(BedType.SINGLE, 2),
        )
    }

    @DisplayName("BED-05: 빈 목록이면 BAD_REQUEST")
    @Test
    fun throwsBadRequest_whenEntriesIsEmpty() {
        assertBadRequest { BedConfiguration(emptyList()) }
    }

    @DisplayName("BED-06: 동일 구성으로 생성한 두 인스턴스는 동등하다")
    @Test
    fun instancesAreEqual_whenSameEntries() {
        val a = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1)))
        val b = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1)))
        assertThat(a).isEqualTo(b)
    }
}
