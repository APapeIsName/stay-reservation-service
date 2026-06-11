package com.stay.domain.property

import com.stay.support.assertBadRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.1 RoomType Entity (RT-01~09)
 * 규칙: canAccommodate — guestCount in 1..maxGuestCount (기준 인원은 수용 한도 아님),
 *      생성·갱신 불변식 standardGuestCount <= maxGuestCount (E.2 잠정 가정 G-10)
 */
@Tag("unit")
class RoomTypeTest {

    private fun roomType(
        standardGuestCount: Int = 2,
        maxGuestCount: Int = 4,
    ) = RoomType(
        id = 1L,
        name = "스탠다드 더블",
        standardGuestCount = standardGuestCount,
        maxGuestCount = maxGuestCount,
        bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1))),
        sizeSqm = 23,
        viewType = ViewType.CITY,
        displayStatus = DisplayStatus.VISIBLE,
    )

    @Nested
    @DisplayName("수용 인원 판단 — canAccommodate")
    inner class Accommodation {

        @DisplayName("RT-01: 최대 인원과 동수면 수용한다 (경계 포함)")
        @Test
        fun accommodates_whenGuestCountEqualsMax() {
            assertThat(roomType().canAccommodate(4)).isTrue()
        }

        @DisplayName("RT-02: 최대 인원 +1 이면 수용하지 않는다")
        @Test
        fun rejects_whenGuestCountExceedsMax() {
            assertThat(roomType().canAccommodate(5)).isFalse()
        }

        @DisplayName("RT-03: 기준 인원 미만도 수용한다 — 기준 인원은 요금 정책용, 수용 한도 아님")
        @Test
        fun accommodates_whenBelowStandardCount() {
            assertThat(roomType().canAccommodate(1)).isTrue()
        }

        @DisplayName("RT-04~05: 0 이하 인원은 수용하지 않는다")
        @ParameterizedTest(name = "guestCount={0}")
        @ValueSource(
            ints = [
                0, // RT-04: 0명
                -1, // RT-05: 음수
            ],
        )
        fun rejects_whenGuestCountIsBelowOne(guestCount: Int) {
            assertThat(roomType().canAccommodate(guestCount)).isFalse()
        }
    }

    @Nested
    @DisplayName("정보 갱신 — updateInfo")
    inner class Update {

        @DisplayName("RT-06: 갱신하면 필드가 바뀌고 수용 판단에 즉시 반영된다")
        @Test
        fun updatesFields_andAccommodationReflectsNewMax() {
            val sut = roomType()
            sut.updateInfo(
                name = "디럭스 트윈",
                standardGuestCount = 2,
                maxGuestCount = 3,
                bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.SINGLE, 2))),
                sizeSqm = 26,
                viewType = ViewType.OCEAN,
            )
            assertThat(sut.name).isEqualTo("디럭스 트윈")
            assertThat(sut.maxGuestCount).isEqualTo(3)
            assertThat(sut.canAccommodate(4)).isFalse()
        }

        @DisplayName("RT-08: 갱신 경로에서도 기준 <= 최대 불변식 위반이면 BAD_REQUEST — 기존 값 유지")
        @Test
        fun throwsBadRequest_andKeepsState_whenUpdateViolatesInvariant() {
            val sut = roomType()
            assertBadRequest {
                sut.updateInfo(
                    name = "디럭스 트윈",
                    standardGuestCount = 5,
                    maxGuestCount = 3,
                    bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.SINGLE, 2))),
                    sizeSqm = 26,
                    viewType = ViewType.OCEAN,
                )
            }
            assertThat(sut.standardGuestCount).isEqualTo(2)
            assertThat(sut.maxGuestCount).isEqualTo(4)
            assertThat(sut.name).isEqualTo("스탠다드 더블")
        }
    }

    @Nested
    @DisplayName("생성 불변식")
    inner class Creation {

        @DisplayName("RT-07: 기준 인원 > 최대 인원이면 생성 시 BAD_REQUEST")
        @Test
        fun throwsBadRequest_whenStandardExceedsMax() {
            assertBadRequest { roomType(standardGuestCount = 4, maxGuestCount = 2) }
        }

        @DisplayName("RT-09: 생성된 RoomType 은 노출 상태 (displayStatus) 를 보유한다")
        @Test
        fun holdsDisplayStatus() {
            assertThat(roomType().displayStatus).isEqualTo(DisplayStatus.VISIBLE)
        }
    }
}
