package com.stay.domain.property

import com.stay.support.assertConflict
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.1 Property Aggregate (PRP-14·01~03·08~09)
 * 규칙: 같은 이름 RoomType 중복 금지 (LLD §2.3 호텔 단위 불변식 → CONFLICT),
 *      RoomType 변경은 모두 Property 경유 (Aggregate Root 단일 진입)
 */
@Tag("unit")
class PropertyTest {

    private fun property(roomTypes: List<RoomType> = emptyList(), wishCount: Long = 0L) = Property(
        id = 1L,
        name = "스테이 호텔 강남",
        city = City.SEOUL,
        address = Address("테헤란로 152", "06236"),
        propertyType = PropertyType.HOTEL,
        amenities = setOf(Amenity.WIFI, Amenity.PARKING),
        checkInTime = LocalTime.of(15, 0),
        checkOutTime = LocalTime.of(11, 0),
        cancellationPolicy = CancellationPolicy(listOf(RefundRule(7, 100), RefundRule(0, 0))),
        representativeImageUrl = "https://img.example.com/gangnam.jpg",
        displayStatus = DisplayStatus.VISIBLE,
        wishCount = wishCount,
        roomTypes = roomTypes,
    )

    private fun roomType(id: Long = 1L, name: String = "스탠다드 더블") = RoomType(
        id = id,
        name = name,
        standardGuestCount = 2,
        maxGuestCount = 2,
        bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1))),
        sizeSqm = 23,
        viewType = ViewType.CITY,
        displayStatus = DisplayStatus.VISIBLE,
    )

    private fun addRoomType(sut: Property, name: String): RoomType = sut.addRoomType(
        name = name,
        standardGuestCount = 2,
        maxGuestCount = 2,
        bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1))),
        sizeSqm = 23,
        viewType = ViewType.CITY,
    )

    private fun updateRoomType(sut: Property, roomTypeId: Long, name: String) = sut.updateRoomType(
        roomTypeId = roomTypeId,
        name = name,
        standardGuestCount = 2,
        maxGuestCount = 3,
        bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.SINGLE, 2))),
        sizeSqm = 26,
        viewType = ViewType.OCEAN,
    )

    @Nested
    @DisplayName("생성 — register 정적 팩토리")
    inner class Register {

        @DisplayName("PRP-14: 위치·편의시설을 보유하고 찜 수 0 으로 등록된다")
        @Test
        fun registersWithLocationAmenitiesAndZeroWishCount() {
            val sut = Property.register(
                name = "스테이 호텔 강남",
                city = City.SEOUL,
                address = Address("테헤란로 152", "06236"),
                propertyType = PropertyType.HOTEL,
                amenities = setOf(Amenity.WIFI, Amenity.PARKING),
                checkInTime = LocalTime.of(15, 0),
                checkOutTime = LocalTime.of(11, 0),
                cancellationPolicy = CancellationPolicy(listOf(RefundRule(7, 100), RefundRule(0, 0))),
                representativeImageUrl = "https://img.example.com/gangnam.jpg",
            )
            assertThat(sut.city).isEqualTo(City.SEOUL)
            assertThat(sut.address).isEqualTo(Address("테헤란로 152", "06236"))
            assertThat(sut.amenities).containsExactlyInAnyOrder(Amenity.WIFI, Amenity.PARKING)
            assertThat(sut.wishCount).isEqualTo(0L)
            assertThat(sut.displayStatus).isEqualTo(DisplayStatus.VISIBLE)
            assertThat(sut.roomTypes).isEmpty()
        }
    }

    @Nested
    @DisplayName("객실 타입 추가 — addRoomType (Aggregate 경유 단일 진입)")
    inner class AddRoomType {

        @DisplayName("PRP-01: 빈 숙소에 객실 타입을 추가하면 반환되고 목록에 담긴다")
        @Test
        fun addsRoomType() {
            val sut = property()
            val created = addRoomType(sut, "스탠다드 더블")
            assertThat(created.name).isEqualTo("스탠다드 더블")
            assertThat(sut.roomTypes).hasSize(1)
        }

        @DisplayName("PRP-02: 같은 이름이면 CONFLICT — 호텔 단위 중복 금지 불변식, 목록 불변")
        @Test
        fun throwsConflict_whenNameIsDuplicated() {
            val sut = property()
            addRoomType(sut, "스탠다드 더블")
            assertConflict { addRoomType(sut, "스탠다드 더블") }
            assertThat(sut.roomTypes).hasSize(1)
        }

        @DisplayName("PRP-03: 다른 이름이면 정상 추가된다")
        @Test
        fun addsAnotherRoomType_whenNameDiffers() {
            val sut = property()
            addRoomType(sut, "스탠다드 더블")
            addRoomType(sut, "디럭스 트윈")
            assertThat(sut.roomTypes).hasSize(2)
        }
    }

    @Nested
    @DisplayName("객실 타입 조회 — findRoomType")
    inner class FindRoomType {

        @DisplayName("PRP-08: 보유한 id 로 조회하면 해당 RoomType 을 반환한다")
        @Test
        fun findsRoomTypeById() {
            val sut = property(roomTypes = listOf(roomType(id = 1L)))
            val found = sut.findRoomType(1L)
            assertThat(found).isNotNull
            assertThat(found!!.id).isEqualTo(1L)
            assertThat(found.name).isEqualTo("스탠다드 더블")
        }

        @DisplayName("PRP-09: 미보유 id 면 null 을 반환한다 — throw 아님 (nullable 시그니처)")
        @Test
        fun returnsNull_whenRoomTypeNotFound() {
            val sut = property(roomTypes = listOf(roomType(id = 1L)))
            assertThat(sut.findRoomType(99L)).isNull()
        }
    }

    @Nested
    @DisplayName("객실 타입 갱신 — updateRoomType (Aggregate 경유)")
    inner class UpdateRoomType {

        @DisplayName("PRP-04: 보유한 id 를 갱신하면 필드가 반영된다")
        @Test
        fun updatesRoomType() {
            val sut = property(roomTypes = listOf(roomType(id = 1L)))
            updateRoomType(sut, 1L, "디럭스 더블")
            val updated = sut.findRoomType(1L)!!
            assertThat(updated.name).isEqualTo("디럭스 더블")
            assertThat(updated.maxGuestCount).isEqualTo(3)
        }

        @DisplayName("PRP-05: 미보유 id 갱신이면 NOT_FOUND")
        @Test
        fun throwsNotFound_whenUpdatingMissingId() {
            val sut = property(roomTypes = listOf(roomType(id = 1L)))
            val thrown = assertThrows<CoreException> { updateRoomType(sut, 99L, "디럭스 더블") }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("PRP-15: 다른 객실 타입의 이름으로 갱신이면 CONFLICT — 기존 이름 유지")
        @Test
        fun throwsConflict_whenRenamingToExistingName() {
            val sut = property(
                roomTypes = listOf(
                    roomType(id = 1L, name = "스탠다드 더블"),
                    roomType(id = 2L, name = "디럭스 트윈"),
                ),
            )
            assertConflict { updateRoomType(sut, 2L, "스탠다드 더블") }
            assertThat(sut.findRoomType(2L)!!.name).isEqualTo("디럭스 트윈")
        }

        @DisplayName("PRP-16: 자기 이름을 유지한 갱신은 허용된다 — 중복 검사는 자기 제외")
        @Test
        fun allowsUpdate_whenKeepingOwnName() {
            val sut = property(roomTypes = listOf(roomType(id = 1L, name = "스탠다드 더블")))
            updateRoomType(sut, 1L, "스탠다드 더블")
            assertThat(sut.findRoomType(1L)!!.maxGuestCount).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("객실 타입 제거 — removeRoomType")
    inner class RemoveRoomType {

        @DisplayName("PRP-06: 보유한 id 를 제거하면 목록에서 사라진다")
        @Test
        fun removesRoomType() {
            val sut = property(roomTypes = listOf(roomType(id = 1L)))
            sut.removeRoomType(1L)
            assertThat(sut.roomTypes).isEmpty()
            assertThat(sut.findRoomType(1L)).isNull()
        }

        @DisplayName("PRP-07: 미보유 id 제거면 NOT_FOUND — 목록 불변")
        @Test
        fun throwsNotFound_whenRemovingMissingId() {
            val sut = property(roomTypes = listOf(roomType(id = 1L)))
            val thrown = assertThrows<CoreException> { sut.removeRoomType(99L) }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(sut.roomTypes).hasSize(1)
        }
    }

    @Nested
    @DisplayName("숙소 정보 갱신 — updateInfo")
    inner class UpdateInfo {

        @DisplayName("PRP-10: 갱신하면 대상 필드만 바뀌고 roomTypes·wishCount 는 불변이다")
        @Test
        fun updatesInfo_withoutTouchingRoomTypesAndWishCount() {
            val sut = property(roomTypes = listOf(roomType(id = 1L)), wishCount = 5L)
            sut.updateInfo(
                name = "스테이 호텔 역삼",
                city = City.SEOUL,
                address = Address("테헤란로 311", "06151"),
                propertyType = PropertyType.HOTEL,
                amenities = setOf(Amenity.WIFI),
                checkInTime = LocalTime.of(16, 0),
                checkOutTime = LocalTime.of(11, 0),
                cancellationPolicy = CancellationPolicy(listOf(RefundRule(3, 50))),
                representativeImageUrl = "https://img.example.com/yeoksam.jpg",
            )
            assertThat(sut.name).isEqualTo("스테이 호텔 역삼")
            assertThat(sut.checkInTime).isEqualTo(LocalTime.of(16, 0))
            assertThat(sut.roomTypes).hasSize(1)
            assertThat(sut.wishCount).isEqualTo(5L)
        }
    }

    @Nested
    @DisplayName("찜 수 카운터 — incrementWish/decrementWish")
    inner class WishCount {

        @DisplayName("PRP-11: 찜 등록이면 wishCount 가 1 증가한다")
        @Test
        fun incrementsWishCount() {
            val sut = property(wishCount = 5L)
            sut.incrementWish()
            assertThat(sut.wishCount).isEqualTo(6L)
        }

        @DisplayName("PRP-12: 찜 취소면 wishCount 가 1 감소한다")
        @Test
        fun decrementsWishCount() {
            val sut = property(wishCount = 5L)
            sut.decrementWish()
            assertThat(sut.wishCount).isEqualTo(4L)
        }

        @DisplayName("PRP-13: 0 에서 감소하면 INTERNAL_ERROR — 등록·취소 짝 깨짐 신호 (Q5 안전망)")
        @Test
        fun throwsInternalError_whenDecrementingZero() {
            val sut = property(wishCount = 0L)
            val thrown = assertThrows<CoreException> { sut.decrementWish() }
            assertThat(thrown.errorType).isEqualTo(ErrorType.INTERNAL_ERROR)
            assertThat(sut.wishCount).isEqualTo(0L)
        }
    }
}
