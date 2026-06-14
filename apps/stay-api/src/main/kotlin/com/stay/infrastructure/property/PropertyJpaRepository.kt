package com.stay.infrastructure.property

import com.stay.domain.property.City
import com.stay.domain.property.Property
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA 진입점. 구현은 Spring Data 가 런타임 생성.
 * 도메인 port (`com.stay.domain.property.PropertyRepository`) 와 분리 — 어댑터가 연결.
 */
interface PropertyJpaRepository : JpaRepository<Property, Long> {
    fun findByCity(city: City): List<Property>
}
