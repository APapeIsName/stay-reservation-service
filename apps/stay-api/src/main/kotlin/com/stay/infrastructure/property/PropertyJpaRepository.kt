package com.stay.infrastructure.property

import com.stay.domain.property.City
import com.stay.domain.property.Property
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Spring Data JPA 진입점. 구현은 Spring Data 가 런타임 생성.
 * 도메인 port (`com.stay.domain.property.PropertyRepository`) 와 분리 — 어댑터가 연결.
 */
interface PropertyJpaRepository : JpaRepository<Property, Long> {
    fun findByCity(city: City): List<Property>

    /**
     * 찜 수 원자적 증가 — 상대 증가(read-modify-write 아님)라 동시 찜에도 lost update 없음 (ADR-004 §3, B.5).
     */
    @Modifying
    @Query("UPDATE Property p SET p.wishCount = p.wishCount + 1 WHERE p.id = :id")
    fun incrementWishCount(@Param("id") id: Long)

    /**
     * 찜 수 원자적 감소 — `wish_count > 0` 가드로 음수 방지.
     */
    @Modifying
    @Query("UPDATE Property p SET p.wishCount = p.wishCount - 1 WHERE p.id = :id AND p.wishCount > 0")
    fun decrementWishCount(@Param("id") id: Long)
}
