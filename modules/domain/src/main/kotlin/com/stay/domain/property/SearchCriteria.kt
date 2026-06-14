package com.stay.domain.property

import java.time.LocalDate

/**
 * 숙소 검색 조건 VO — 검증 없는 값 보유.
 *  - 기간 검증 (0박·역전) 은 Service 의 DateRange 변환이 위임 (rule 06)
 *
 * Refs:
 *  - docs/design/03-class-diagram.md §7.2 결과 VO
 */
data class SearchCriteria(
    val city: City,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guests: Int,
    val sort: SortType,
    val page: Int,
    val size: Int,
)
