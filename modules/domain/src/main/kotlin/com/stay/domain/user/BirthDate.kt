package com.stay.domain.user

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Embeddable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 생년월일 VO.
 *  - 포맷: yyyy-MM-dd (ISO_LOCAL_DATE, ResolverStyle.STRICT)
 *  - 미래 날짜 거부, 만 14세 이상 (today 외부 주입)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * 생성 진입점:
 *  - 1차 (검증 통과된 LocalDate 보관): `BirthDate(value: LocalDate)` — JPA materialization
 *  - 2차 (사용자 입력 파싱/검증): `BirthDate(rawValue: String, today: LocalDate)` — 일반 도메인 흐름
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §2
 *  - .claude/rules/08-static-factory-and-clock-injection.md, 12-user-field-policy.md
 */
@Embeddable
data class BirthDate(val value: LocalDate) {

    constructor(rawValue: String, today: LocalDate) : this(parseAndValidate(rawValue, today))

    fun toYyyyMMdd(): String = value.format(DateTimeFormatter.BASIC_ISO_DATE)

    companion object {
        private const val MIN_AGE_YEARS: Long = 14L
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

        private fun parseAndValidate(rawValue: String, today: LocalDate): LocalDate {
            val parsed = try {
                LocalDate.parse(rawValue, FORMATTER)
            } catch (e: DateTimeParseException) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "생년월일 형식이 올바르지 않습니다.",
                )
            }
            if (parsed.isAfter(today)) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "생년월일은 미래일 수 없습니다.",
                )
            }
            if (parsed.isAfter(today.minusYears(MIN_AGE_YEARS))) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "만 14세 이상만 가입할 수 있습니다.",
                )
            }
            return parsed
        }
    }
}
