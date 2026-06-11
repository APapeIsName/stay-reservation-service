package com.stay.application.property

/**
 * 숙소 상세 조회 결과 출력 DTO (rule 20 — Application 출력 Info).
 *  - roomTypes 는 인원·가용 필터를 통과한 RoomType 만 포함 (S-4 잠정)
 */
data class PropertyDetailInfo(
    val propertyId: Long,
    val name: String,
    val wishCount: Long,
    val roomTypes: List<RoomTypeOption>,
) {
    /** 가용 객실 타입 옵션 — 기간 합산가 포함. */
    data class RoomTypeOption(
        val roomTypeId: Long,
        val name: String,
        val maxGuestCount: Int,
        val totalPrice: Long,
    )
}
