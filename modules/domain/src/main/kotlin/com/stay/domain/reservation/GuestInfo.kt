package com.stay.domain.reservation

import com.stay.domain.user.Name
import com.stay.domain.user.PhoneNumber
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded

/**
 * 예약 투숙자 정보 VO (투숙자명·연락처·인원).
 *  - 규칙: guestCount >= 1 (E.2 잠정 가정 #4)
 *  - 이름·전화 형식은 R1 Name·PhoneNumber VO 생성자가 검증 (재검증하지 않음)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.3 GuestInfo VO
 *  - .claude/rules/06-validation-via-domain-vo.md
 */
@Embeddable
data class GuestInfo(
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "guest_name", nullable = false, length = 50))
    val guestName: Name,
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "guest_phone", nullable = false, length = 13))
    val guestPhone: PhoneNumber,
    @Column(name = "guest_count", nullable = false)
    val guestCount: Int,
) {
    init {
        if (guestCount < 1) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "투숙 인원 형식이 올바르지 않습니다.",
            )
        }
    }
}
