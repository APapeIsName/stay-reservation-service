package com.stay.infrastructure.property

import com.stay.domain.property.City
import com.stay.domain.property.Property
import com.stay.domain.property.PropertyRepository
import org.springframework.stereotype.Component

/**
 * 도메인 `PropertyRepository` 의 인프라 구현. Spring Data JPA 위에서 동작.
 * 도메인은 port 만 알고 인프라 세부 (Spring Data·JPA·MySQL) 는 무관 (DIP — rule 19).
 */
@Component
class PropertyRepositoryImpl(
    private val jpaRepository: PropertyJpaRepository,
) : PropertyRepository {

    override fun findById(id: Long): Property? =
        jpaRepository.findById(id).orElse(null)

    override fun findByCity(city: City): List<Property> =
        jpaRepository.findByCity(city)

    override fun save(property: Property): Property =
        jpaRepository.save(property)

    override fun incrementWishCount(id: Long) =
        jpaRepository.incrementWishCount(id)

    override fun decrementWishCount(id: Long) =
        jpaRepository.decrementWishCount(id)
}
