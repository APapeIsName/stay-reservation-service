package com.stay.application.property

/**
 * 숙소 검색 결과 항목 출력 DTO (rule 20 — Application 출력 Info).
 *  - minTotalPrice: 가용 RoomType 별 기간 합산가의 최소값
 */
data class PropertySearchItemInfo(
    val propertyId: Long,
    val name: String,
    val minTotalPrice: Long,
    val wishCount: Long,
)
