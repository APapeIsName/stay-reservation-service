package com.stay.application.reservation

import java.time.LocalDate

/**
 * 예약 생성 입력 DTO.
 * Controller 의 Request DTO 를 Service 진입점 용으로 변환한 형태.
 *
 * 도메인 VO 가 아닌 raw 타입으로 보관 — 검증은 Service 가 생성하는 DateRange·GuestInfo
 * VO 생성자에서 수행 (rule 06 — 검증을 도메인 VO 로 일원화).
 */
data class ReserveCommand(
    val propertyId: Long,
    val roomTypeId: Long,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guestCount: Int,
    val guestName: String,
    val guestPhone: String,
)
