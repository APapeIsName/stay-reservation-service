package com.stay.domain.property

import com.stay.domain.AuditedEntity
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 객실 타입 — Property Aggregate 의 내부 Entity (고유 id 동일성, 상태 변화).
 *  - 규칙: standardGuestCount <= maxGuestCount (생성·갱신 공통 불변식, E.2 잠정 가정 G-10)
 *  - canAccommodate: guestCount in 1..maxGuestCount — 기준 인원은 요금 정책용, 수용 한도 아님
 *  - updateInfo: 검증 후 변이 — 불변식 위반 시 CoreException(BAD_REQUEST), 기존 상태 유지
 *  - 영속: room_type 테이블, FK (property_id) 는 Property 의 @OneToMany @JoinColumn 이 소유 (단방향). audit 은 AuditedEntity
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.1 RoomType Entity (RT-01~09)
 *  - .claude/rules/18-domain-modeling.md (Entity 분류, Tell Don't Ask) / docs/design/04-erd.md §2.5
 */
@Entity
@Table(name = "room_type")
class RoomType(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,
    name: String,
    standardGuestCount: Int,
    maxGuestCount: Int,
    bedConfiguration: BedConfiguration,
    sizeSqm: Int,
    viewType: ViewType,
    @Enumerated(EnumType.STRING)
    @Column(name = "display_status", nullable = false, length = 20)
    val displayStatus: DisplayStatus,
) : AuditedEntity() {

    @Column(name = "name", nullable = false, length = 50)
    var name: String = name
        private set

    @Column(name = "standard_guest_count", nullable = false)
    var standardGuestCount: Int = standardGuestCount
        private set

    @Column(name = "max_guest_count", nullable = false)
    var maxGuestCount: Int = maxGuestCount
        private set

    @Embedded
    var bedConfiguration: BedConfiguration = bedConfiguration
        private set

    @Column(name = "size_sqm", nullable = false)
    var sizeSqm: Int = sizeSqm
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "view_type", nullable = false, length = 20)
    var viewType: ViewType = viewType
        private set

    init {
        validateGuestCountInvariant(standardGuestCount, maxGuestCount)
    }

    fun canAccommodate(guestCount: Int): Boolean = guestCount in 1..maxGuestCount

    fun updateInfo(
        name: String,
        standardGuestCount: Int,
        maxGuestCount: Int,
        bedConfiguration: BedConfiguration,
        sizeSqm: Int,
        viewType: ViewType,
    ) {
        validateGuestCountInvariant(standardGuestCount, maxGuestCount)
        this.name = name
        this.standardGuestCount = standardGuestCount
        this.maxGuestCount = maxGuestCount
        this.bedConfiguration = bedConfiguration
        this.sizeSqm = sizeSqm
        this.viewType = viewType
    }

    private fun validateGuestCountInvariant(standardGuestCount: Int, maxGuestCount: Int) {
        if (standardGuestCount > maxGuestCount) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "기준 인원이 최대 인원을 초과하여 올바르지 않습니다.",
            )
        }
    }
}
