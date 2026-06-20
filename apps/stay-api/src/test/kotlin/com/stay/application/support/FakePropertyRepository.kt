package com.stay.application.support

import com.stay.domain.property.City
import com.stay.domain.property.Property
import com.stay.domain.property.PropertyRepository

/**
 * PropertyRepository 인메모리 Fake — Spring/DB 없이 L2 오케스트레이션 검증용 (발제: Fake/Stub).
 * 도메인 port 를 구현하므로 인터페이스의 소재지 (modules/domain, DIP) 를 함께 검증한다.
 */
class FakePropertyRepository : PropertyRepository {

    private val store = mutableMapOf<Long, Property>()

    fun seed(property: Property) {
        store[property.id] = property
    }

    override fun findById(id: Long): Property? = store[id]

    override fun findByCity(city: City): List<Property> = store.values.filter { it.city == city }

    override fun save(property: Property): Property {
        store[property.id] = property
        return property
    }

    override fun incrementWishCount(id: Long) {
        store[id]?.incrementWish()
    }

    override fun decrementWishCount(id: Long) {
        store[id]?.decrementWish()
    }
}
