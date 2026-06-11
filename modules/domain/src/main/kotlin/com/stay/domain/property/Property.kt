package com.stay.domain.property

import com.stay.domain.AuditedEntity
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalTime

/**
 * 숙소 — Aggregate Root. RoomType 을 composition 으로 보유하며, RoomType 의 모든 변경은
 * Property 를 경유한다 (단일 진입).
 *  - 규칙: 같은 이름 RoomType 중복 금지 (호텔 단위 불변식) — 위반 시 CoreException(CONFLICT), 가드가 변이보다 먼저
 *  - updateRoomType: 미보유 id 는 NOT_FOUND, 자기 제외 이름 중복은 CONFLICT — 통과 시 RoomType.updateInfo 위임 (Tell Don't Ask)
 *  - removeRoomType: 미보유 id 는 NOT_FOUND (목록 불변), 보유 시 목록에서 제거
 *  - updateInfo: 어드민 수정 가능 필드 전체 갱신 — displayStatus·wishCount·roomTypes 는 건드리지 않음 (LLD §2.2)
 *  - incrementWish/decrementWish: 찜 수 카운터 — 0 에서 감소 시 CoreException(INTERNAL_ERROR),
 *    서비스 멱등 가드가 정상이면 도달 불가능한 등록·취소 짝 깨짐 신호 (Q5, DailyRoom.releaseOne 패턴)
 *  - 생성 진입점은 정적 팩토리 `Property.register(...)` (id=0L, wishCount=0, VISIBLE, 빈 roomTypes)
 *  - 주 생성자는 영속 재구성 경로 — 전 필드 + roomTypes 수취
 *  - roomTypes 는 내부 가변 목록을 캡슐화하고 외부에는 읽기 전용 List 만 노출
 *  - 영속: roomTypes 는 @OneToMany cascade + orphanRemoval (Aggregate 내부 — 삭제 cascade),
 *    amenities 는 @ElementCollection property_amenity, cancellationPolicy 는 @Embedded (rules → refund_rule). audit 은 AuditedEntity
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.1 Property Aggregate (PRP-01~16)
 *  - .claude/rules/08-static-factory-and-clock-injection.md, 18-domain-modeling.md / docs/design/04-erd.md §2.2~2.5
 */
@Entity
@Table(name = "property")
class Property(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    name: String,
    city: City,
    address: Address,
    propertyType: PropertyType,
    amenities: Set<Amenity>,
    checkInTime: LocalTime,
    checkOutTime: LocalTime,
    cancellationPolicy: CancellationPolicy,
    representativeImageUrl: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "display_status", nullable = false, length = 20)
    val displayStatus: DisplayStatus,
    wishCount: Long,
    roomTypes: List<RoomType>,
) : AuditedEntity() {

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "city", nullable = false, length = 20)
    var city: City = city
        private set

    @Embedded
    var address: Address = address
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    var propertyType: PropertyType = propertyType
        private set

    @ElementCollection
    @CollectionTable(name = "property_amenity", joinColumns = [JoinColumn(name = "property_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "amenity", nullable = false, length = 20)
    var amenities: Set<Amenity> = amenities
        private set

    @Column(name = "check_in_time", nullable = false)
    var checkInTime: LocalTime = checkInTime
        private set

    @Column(name = "check_out_time", nullable = false)
    var checkOutTime: LocalTime = checkOutTime
        private set

    @Embedded
    var cancellationPolicy: CancellationPolicy = cancellationPolicy
        private set

    @Column(name = "representative_image_url", nullable = false, length = 500)
    var representativeImageUrl: String = representativeImageUrl
        private set

    @Column(name = "wish_count", nullable = false)
    var wishCount: Long = wishCount
        private set

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "property_id", nullable = false)
    private val _roomTypes: MutableList<RoomType> = roomTypes.toMutableList()

    val roomTypes: List<RoomType>
        get() = _roomTypes.toList()

    fun updateInfo(
        name: String,
        city: City,
        address: Address,
        propertyType: PropertyType,
        amenities: Set<Amenity>,
        checkInTime: LocalTime,
        checkOutTime: LocalTime,
        cancellationPolicy: CancellationPolicy,
        representativeImageUrl: String,
    ) {
        this.name = name
        this.city = city
        this.address = address
        this.propertyType = propertyType
        this.amenities = amenities
        this.checkInTime = checkInTime
        this.checkOutTime = checkOutTime
        this.cancellationPolicy = cancellationPolicy
        this.representativeImageUrl = representativeImageUrl
    }

    fun incrementWish() {
        wishCount += 1
    }

    fun decrementWish() {
        if (wishCount <= 0L) {
            throw CoreException(ErrorType.INTERNAL_ERROR, "감소할 찜 수가 없습니다.")
        }
        wishCount -= 1
    }

    fun addRoomType(
        name: String,
        standardGuestCount: Int,
        maxGuestCount: Int,
        bedConfiguration: BedConfiguration,
        sizeSqm: Int,
        viewType: ViewType,
    ): RoomType {
        if (_roomTypes.any { it.name == name }) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 객실 타입 이름입니다.")
        }
        val roomType = RoomType(
            id = 0L,
            name = name,
            standardGuestCount = standardGuestCount,
            maxGuestCount = maxGuestCount,
            bedConfiguration = bedConfiguration,
            sizeSqm = sizeSqm,
            viewType = viewType,
            displayStatus = DisplayStatus.VISIBLE,
        )
        _roomTypes.add(roomType)
        return roomType
    }

    fun findRoomType(roomTypeId: Long): RoomType? = _roomTypes.find { it.id == roomTypeId }

    fun updateRoomType(
        roomTypeId: Long,
        name: String,
        standardGuestCount: Int,
        maxGuestCount: Int,
        bedConfiguration: BedConfiguration,
        sizeSqm: Int,
        viewType: ViewType,
    ) {
        val roomType = findRoomType(roomTypeId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "객실 타입을 찾을 수 없습니다.")
        if (_roomTypes.any { it.id != roomTypeId && it.name == name }) {
            throw CoreException(ErrorType.CONFLICT, "이미 존재하는 객실 타입 이름입니다.")
        }
        roomType.updateInfo(
            name = name,
            standardGuestCount = standardGuestCount,
            maxGuestCount = maxGuestCount,
            bedConfiguration = bedConfiguration,
            sizeSqm = sizeSqm,
            viewType = viewType,
        )
    }

    fun removeRoomType(roomTypeId: Long) {
        val roomType = findRoomType(roomTypeId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "객실 타입을 찾을 수 없습니다.")
        _roomTypes.remove(roomType)
    }

    companion object {
        fun register(
            name: String,
            city: City,
            address: Address,
            propertyType: PropertyType,
            amenities: Set<Amenity>,
            checkInTime: LocalTime,
            checkOutTime: LocalTime,
            cancellationPolicy: CancellationPolicy,
            representativeImageUrl: String,
        ): Property = Property(
            id = 0L,
            name = name,
            city = city,
            address = address,
            propertyType = propertyType,
            amenities = amenities,
            checkInTime = checkInTime,
            checkOutTime = checkOutTime,
            cancellationPolicy = cancellationPolicy,
            representativeImageUrl = representativeImageUrl,
            displayStatus = DisplayStatus.VISIBLE,
            wishCount = 0L,
            roomTypes = emptyList(),
        )
    }
}
