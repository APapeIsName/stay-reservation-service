package com.stay.application.support

import com.stay.domain.dailyroom.DailyRoom
import com.stay.domain.property.Address
import com.stay.domain.property.Amenity
import com.stay.domain.property.BedConfiguration
import com.stay.domain.property.BedEntry
import com.stay.domain.property.BedType
import com.stay.domain.property.CancellationPolicy
import com.stay.domain.property.City
import com.stay.domain.property.DisplayStatus
import com.stay.domain.property.Property
import com.stay.domain.property.PropertyType
import com.stay.domain.property.RefundRule
import com.stay.domain.property.RoomType
import com.stay.domain.property.ViewType
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Application (L2) 테스트 공용 도메인 픽스처 빌더.
 *  - 시간: FIXED_CLOCK (2026-06-10T10:00 KST) — rule 08 Clock 주입과 짝
 *  - 모든 빌더는 카탈로그 기본값 (스테이 제주 / 디럭스 더블 / 100000원) 을 가지며 테스트별 필요 값만 오버라이드
 *
 * Refs: docs/round-3/02-tdd-plan.md B.5
 */
object DomainFixtures {

    val KST: ZoneId = ZoneId.of("Asia/Seoul")
    val NOW: LocalDateTime = LocalDateTime.parse("2026-06-10T10:00")
    val FIXED_CLOCK: Clock = Clock.fixed(NOW.atZone(KST).toInstant(), KST)

    fun cancellationPolicy(rules: List<RefundRule> = listOf(RefundRule(7, 100), RefundRule(3, 50), RefundRule(0, 0))) =
        CancellationPolicy(rules)

    fun roomType(
        id: Long = 20L,
        name: String = "디럭스 더블",
        standardGuestCount: Int = 2,
        maxGuestCount: Int = 2,
        displayStatus: DisplayStatus = DisplayStatus.VISIBLE,
    ) = RoomType(
        id = id,
        name = name,
        standardGuestCount = standardGuestCount,
        maxGuestCount = maxGuestCount,
        bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1))),
        sizeSqm = 26,
        viewType = ViewType.OCEAN,
        displayStatus = displayStatus,
    )

    fun property(
        id: Long = 10L,
        name: String = "스테이 제주",
        city: City = City.JEJU,
        wishCount: Long = 0L,
        displayStatus: DisplayStatus = DisplayStatus.VISIBLE,
        cancellationPolicy: CancellationPolicy = cancellationPolicy(),
        roomTypes: List<RoomType> = emptyList(),
    ) = Property(
        id = id,
        name = name,
        city = city,
        address = Address("애월로 100", "63062"),
        propertyType = PropertyType.HOTEL,
        amenities = setOf(Amenity.WIFI),
        checkInTime = LocalTime.of(15, 0),
        checkOutTime = LocalTime.of(11, 0),
        cancellationPolicy = cancellationPolicy,
        representativeImageUrl = "https://img.example.com/jeju.jpg",
        displayStatus = displayStatus,
        wishCount = wishCount,
        roomTypes = roomTypes,
    )

    /**
     * L3 통합 테스트 시드용 — id 미부여 `Property.register(...)` 경로.
     * JPA IDENTITY 가 id 를 부여하므로 저장 후 반환 객체의 id 를 사용한다.
     */
    fun newProperty(
        name: String = "스테이 제주",
        city: City = City.JEJU,
        cancellationPolicy: CancellationPolicy = cancellationPolicy(),
    ) = Property.register(
        name = name,
        city = city,
        address = Address("애월로 100", "63062"),
        propertyType = PropertyType.HOTEL,
        amenities = setOf(Amenity.WIFI, Amenity.PARKING),
        checkInTime = LocalTime.of(15, 0),
        checkOutTime = LocalTime.of(11, 0),
        cancellationPolicy = cancellationPolicy,
        representativeImageUrl = "https://img.example.com/jeju.jpg",
    )

    fun dailyRoom(
        roomTypeId: Long = 20L,
        date: String,
        totalRooms: Int = 5,
        reservedRooms: Int = 0,
        pricePerNight: Long = 100000L,
        closed: Boolean = false,
    ) = DailyRoom(
        roomTypeId = roomTypeId,
        date = LocalDate.parse(date),
        totalRooms = totalRooms,
        reservedRooms = reservedRooms,
        pricePerNight = pricePerNight,
        closed = closed,
    )
}
