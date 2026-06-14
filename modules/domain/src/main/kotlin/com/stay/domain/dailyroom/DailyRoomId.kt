package com.stay.domain.dailyroom

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.time.LocalDate

/**
 * 일자별 객실 재고·요금 Aggregate `DailyRoom` 의 복합 자연키 VO — (roomTypeId, date).
 *  - 규칙: 값 동등성 (동일 roomTypeId + date 면 같은 키)
 *  - 검증 없음: ID 값 검증 명세 없음 — 참조 무결성은 application/DB 책임
 *  - 영속: @EmbeddedId (E1 잠정, ERD §2.7) — Serializable 은 JPA 복합키 요구사항
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.2 DailyRoomId VO
 *  - LLD §3.2 <<VO (복합 PK)>> / docs/design/04-erd.md §2.7
 */
@Embeddable
data class DailyRoomId(
    @Column(name = "room_type_id", nullable = false)
    val roomTypeId: Long,
    @Column(name = "date", nullable = false)
    val date: LocalDate,
) : Serializable
