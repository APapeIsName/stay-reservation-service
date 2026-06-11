package com.stay.domain

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import java.time.ZonedDateTime

/**
 * 생성/수정 시점만 자동 관리하는 audit 전용 superclass.
 *
 * BaseEntity 와의 분담 — BaseEntity 는 surrogate `@Id` (IDENTITY) 를 포함하므로
 * ① 복합 자연키 테이블 (daily_room 등 @EmbeddedId) ② 재구성 생성자가 id 를 받는 Aggregate
 * 에는 쓸 수 없다. 그런 엔티티는 본 클래스로 audit 만 가져간다 (Round 3 Q8).
 */
@MappedSuperclass
abstract class AuditedEntity {
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersistAudit() {
        val now = ZonedDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    private fun preUpdateAudit() {
        updatedAt = ZonedDateTime.now()
    }
}
