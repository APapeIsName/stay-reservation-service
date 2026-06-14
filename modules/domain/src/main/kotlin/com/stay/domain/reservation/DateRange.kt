package com.stay.domain.reservation

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 예약 숙박 기간 VO (checkIn ~ checkOut).
 *  - 규칙: checkIn < checkOut (0박·역전 불가 — E.2 잠정 가정 #1)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.3 DateRange VO
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class DateRange(
    @Column(name = "check_in", nullable = false)
    val checkIn: LocalDate,
    @Column(name = "check_out", nullable = false)
    val checkOut: LocalDate,
) {
    init {
        if (!checkIn.isBefore(checkOut)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "숙박 기간 형식이 올바르지 않습니다.",
            )
        }
    }

    /** 숙박 박수. */
    fun nights(): Int = ChronoUnit.DAYS.between(checkIn, checkOut).toInt()

    /** 숙박하는 밤의 일자 목록 — 체크인부터 체크아웃 전날까지 (체크아웃 미포함). */
    fun stayDates(): List<LocalDate> = (0 until nights()).map { checkIn.plusDays(it.toLong()) }
}
