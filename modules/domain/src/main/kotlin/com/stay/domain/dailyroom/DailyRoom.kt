package com.stay.domain.dailyroom

import com.stay.domain.AuditedEntity
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * 일자별 객실 — (roomTypeId, date) 복합 자연키의 재고·요금 Aggregate Root (ADR-003 단일 테이블).
 *  - availableRooms: totalRooms - reservedRooms (파생 계산, 컬럼 X)
 *  - isAvailable: !closed && availableRooms() > 0 — closed 가 잔여 수량보다 우선
 *  - canConsume: !closed && reservedRooms < totalRooms
 *  - pricePerNight >= 0 (E3-3 증보) — 위반 시 생성 단계에서 CoreException(BAD_REQUEST)
 *  - consumeOne: 재고 1실 차감 — 가드 순서 고정 (① closed → CONFLICT, ② 만실 → CONFLICT, ③ 통과 시 +1), 실패 시 상태 불변
 *  - releaseOne: 재고 1실 복원 (예약 취소) — 가드 (reservedRooms <= 0 → INTERNAL_ERROR), closed 가드 없음 (휴실 후 기존 예약 취소 허용), 실패 시 상태 불변
 *  - 영속: @EmbeddedId DailyRoomId 보유, roomTypeId/date 는 파생 getter (도메인 시그니처 불변 — Q8). audit 은 AuditedEntity
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.2 DailyRoom (DR-01~25)
 *  - .claude/rules/18-domain-modeling.md §6 ((roomTypeId, date) 단위 모델링)
 *  - docs/adr/ADR-003-single-daily-room-table.md / docs/design/04-erd.md §2.7
 */
@Entity
@Table(name = "daily_room")
class DailyRoom(
    roomTypeId: Long,
    date: LocalDate,
    @Column(name = "total_rooms", nullable = false)
    val totalRooms: Int,
    reservedRooms: Int,
    @Column(name = "price_per_night", nullable = false)
    val pricePerNight: Long,
    @Column(name = "closed", nullable = false)
    val closed: Boolean = false,
) : AuditedEntity() {

    @EmbeddedId
    val id: DailyRoomId = DailyRoomId(roomTypeId, date)

    val roomTypeId: Long
        get() = id.roomTypeId

    val date: LocalDate
        get() = id.date

    @Column(name = "reserved_rooms", nullable = false)
    var reservedRooms: Int = reservedRooms
        private set

    init {
        if (pricePerNight < 0) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "1박 요금이 음수여서 올바르지 않습니다.",
            )
        }
    }

    fun availableRooms(): Int = totalRooms - reservedRooms

    fun isAvailable(): Boolean = !closed && availableRooms() > 0

    fun canConsume(): Boolean = !closed && reservedRooms < totalRooms

    fun consumeOne() {
        if (closed) {
            throw CoreException(ErrorType.CONFLICT, "휴실 상태입니다.")
        }
        if (reservedRooms >= totalRooms) {
            throw CoreException(ErrorType.CONFLICT, "재고가 부족합니다.")
        }
        reservedRooms += 1
    }

    fun releaseOne() {
        if (reservedRooms <= 0) {
            throw CoreException(ErrorType.INTERNAL_ERROR, "복원할 재고가 없습니다.")
        }
        reservedRooms -= 1
    }
}
