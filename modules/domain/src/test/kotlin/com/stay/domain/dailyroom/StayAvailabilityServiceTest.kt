package com.stay.domain.dailyroom

import com.stay.domain.reservation.DateRange
import com.stay.support.assertConflict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.6 StayAvailabilityService (SAS-01~13)
 * 규칙 (Q3 확정): ① 기간 완전성 — stayDates() 전 일자에 정확히 1건의 DailyRoom (누락·날짜 불일치·중복 → CONFLICT)
 *              ② 전 일자 차감 가능 (휴실·만실 → CONFLICT)   ③ quote — stayDates 순 정렬된 PriceSnapshot
 *              ④ consumeAll — 선검증 후 차감 (all-or-nothing) / releaseAll — 대칭 복원   ⑤ 무상태 (조회 연산은 입력 비변경)
 */
@Tag("unit")
class StayAvailabilityServiceTest {

    private val sut = StayAvailabilityService()

    private fun dailyRoom(
        date: String,
        totalRooms: Int = 10,
        reservedRooms: Int = 0,
        pricePerNight: Long = 100000L,
        closed: Boolean = false,
    ) = DailyRoom(
        roomTypeId = 1L,
        date = LocalDate.parse(date),
        totalRooms = totalRooms,
        reservedRooms = reservedRooms,
        pricePerNight = pricePerNight,
        closed = closed,
    )

    private fun period(checkIn: String, checkOut: String) =
        DateRange(LocalDate.parse(checkIn), LocalDate.parse(checkOut))

    @Nested
    @DisplayName("기간 가용성 검증 — validateAvailability")
    inner class ValidateAvailability {

        @DisplayName("SAS-01: 전 일자 row 존재 + 가용이면 통과한다")
        @Test
        fun passes_whenAllDatesAvailable() {
            sut.validateAvailability(
                period("2026-07-01", "2026-07-03"),
                listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02")),
            )
        }

        @DisplayName("SAS-02: 일자 row 누락이면 CONFLICT — 판매 정보 없는 일자는 가용 불가")
        @Test
        fun throwsConflict_whenDateRowIsMissing() {
            assertConflict {
                sut.validateAvailability(
                    period("2026-07-01", "2026-07-03"),
                    listOf(dailyRoom("2026-07-01")),
                )
            }
        }

        @DisplayName("SAS-03: 개수가 맞아도 날짜가 불일치면 CONFLICT")
        @Test
        fun throwsConflict_whenDatesMismatch() {
            assertConflict {
                sut.validateAvailability(
                    period("2026-07-01", "2026-07-03"),
                    listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-05")),
                )
            }
        }

        @DisplayName("SAS-04: 중복 날짜 입력이면 CONFLICT")
        @Test
        fun throwsConflict_whenDatesDuplicated() {
            assertConflict {
                sut.validateAvailability(
                    period("2026-07-01", "2026-07-03"),
                    listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-01")),
                )
            }
        }

        @DisplayName("SAS-05: 중간 일자가 휴실이면 CONFLICT")
        @Test
        fun throwsConflict_whenAnyDateIsClosed() {
            assertConflict {
                sut.validateAvailability(
                    period("2026-07-01", "2026-07-03"),
                    listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02", closed = true)),
                )
            }
        }

        @DisplayName("SAS-06: 중간 일자가 만실이면 CONFLICT")
        @Test
        fun throwsConflict_whenAnyDateIsFull() {
            assertConflict {
                sut.validateAvailability(
                    period("2026-07-01", "2026-07-03"),
                    listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02", reservedRooms = 10)),
                )
            }
        }

        @DisplayName("SAS-07: 1박 (경계) 은 체크인 일자 1건으로 통과한다 — 체크아웃 일자는 검사 대상 아님")
        @Test
        fun passes_whenOneNightWithSingleRow() {
            sut.validateAvailability(
                period("2026-07-01", "2026-07-02"),
                listOf(dailyRoom("2026-07-01")),
            )
        }
    }

    @Nested
    @DisplayName("견적 산출 — quote")
    inner class Quote {

        @DisplayName("SAS-08: 일자별 요금이 합산된 PriceSnapshot 을 반환한다")
        @Test
        fun quotesTotalPrice() {
            val snapshot = sut.quote(
                period("2026-07-01", "2026-07-03"),
                listOf(
                    dailyRoom("2026-07-01", pricePerNight = 100000L),
                    dailyRoom("2026-07-02", pricePerNight = 120000L),
                ),
            )
            assertThat(snapshot.totalPrice()).isEqualTo(220000L)
            assertThat(snapshot.entries).hasSize(2)
        }

        @DisplayName("SAS-09: 역순 입력도 stayDates 순으로 정렬되어 생성된다")
        @Test
        fun sortsEntriesByStayDates_whenInputIsUnordered() {
            val snapshot = sut.quote(
                period("2026-07-01", "2026-07-03"),
                listOf(
                    dailyRoom("2026-07-02", pricePerNight = 120000L),
                    dailyRoom("2026-07-01", pricePerNight = 100000L),
                ),
            )
            assertThat(snapshot.entries.map { it.date }).containsExactly(
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-02"),
            )
        }
    }

    @Nested
    @DisplayName("일괄 차감·복원 — consumeAll/releaseAll")
    inner class ConsumeAndRelease {

        @DisplayName("SAS-10: 전 일자 재고가 1 씩 차감된다")
        @Test
        fun consumesAllDates() {
            val rooms = listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02"))
            sut.consumeAll(period("2026-07-01", "2026-07-03"), rooms)
            assertThat(rooms.map { it.reservedRooms }).containsOnly(1)
        }

        @DisplayName("SAS-11: 중간 일자 만실이면 CONFLICT + 어떤 일자도 차감되지 않는다 (all-or-nothing)")
        @Test
        fun consumesNothing_whenAnyDateIsFull() {
            val first = dailyRoom("2026-07-01")
            val full = dailyRoom("2026-07-02", reservedRooms = 10)
            assertConflict {
                sut.consumeAll(period("2026-07-01", "2026-07-03"), listOf(first, full))
            }
            assertThat(first.reservedRooms).isEqualTo(0)
        }

        @DisplayName("SAS-12: releaseAll 은 consumeAll 과 대칭 복원한다")
        @Test
        fun releasesAllSymmetrically() {
            val rooms = listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02"))
            sut.consumeAll(period("2026-07-01", "2026-07-03"), rooms)
            sut.releaseAll(rooms)
            assertThat(rooms.map { it.reservedRooms }).containsOnly(0)
        }
    }

    @Nested
    @DisplayName("조회용 가용 판단 — isAvailable (validateAvailability 의 비예외 쌍둥이, 검색·상세 필터용)")
    inner class IsAvailableCheck {

        @DisplayName("SAS-14: 전 일자 row 존재 + 가용이면 true")
        @Test
        fun availableTrue_whenAllDatesAvailable() {
            val result = sut.isAvailable(
                period("2026-07-01", "2026-07-03"),
                listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02")),
            )
            assertThat(result).isTrue()
        }

        @DisplayName("SAS-15: 일자 row 누락이면 false — throw 아님 (조회 필터용)")
        @Test
        fun availableFalse_whenDateRowIsMissing() {
            val result = sut.isAvailable(
                period("2026-07-01", "2026-07-03"),
                listOf(dailyRoom("2026-07-01")),
            )
            assertThat(result).isFalse()
        }

        @DisplayName("SAS-16: 만실 또는 휴실 일자가 포함되면 false")
        @Test
        fun availableFalse_whenAnyDateIsFullOrClosed() {
            val periodTwoNights = period("2026-07-01", "2026-07-03")
            assertThat(
                sut.isAvailable(
                    periodTwoNights,
                    listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02", reservedRooms = 10)),
                ),
            ).isFalse()
            assertThat(
                sut.isAvailable(
                    periodTwoNights,
                    listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02", closed = true)),
                ),
            ).isFalse()
        }
    }

    @Nested
    @DisplayName("무상태 — 조회 연산의 입력 비변경 (발제: 도메인 서비스는 상태 없이)")
    inner class Statelessness {

        @DisplayName("SAS-13: validateAvailability 와 quote 는 입력 DailyRoom 의 상태를 바꾸지 않는다")
        @Test
        fun readOperationsDoNotMutateInput() {
            val rooms = listOf(dailyRoom("2026-07-01"), dailyRoom("2026-07-02"))
            val period = period("2026-07-01", "2026-07-03")
            sut.validateAvailability(period, rooms)
            sut.quote(period, rooms)
            assertThat(rooms.map { it.reservedRooms }).containsOnly(0)
        }
    }
}
