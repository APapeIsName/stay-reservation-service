package com.stay.infrastructure.property

import com.stay.application.support.DomainFixtures
import com.stay.domain.property.Amenity
import com.stay.domain.property.BedConfiguration
import com.stay.domain.property.BedEntry
import com.stay.domain.property.BedType
import com.stay.domain.property.City
import com.stay.domain.property.Property
import com.stay.domain.property.RefundRule
import com.stay.domain.property.ViewType
import com.stay.testcontainers.MySqlTestContainersConfig
import com.stay.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate

/**
 * Property Aggregate 영속 왕복 — PropertyRepositoryImpl + cascade 4테이블
 * (property_amenity · refund_rule · room_type · bed_entry) + orphanRemoval + findByCity.
 *
 * 컬렉션(@OneToMany·@ElementCollection)은 LAZY 이므로 재조회 단언은 TransactionTemplate 경계 안에서 수행.
 *
 * 환경 주의: Testcontainers + Docker. 로컬 Docker Desktop 29.x 호환 이슈로 실행이 실패할 수 있음
 * (memory: testcontainers-docker-desktop-incompat). 환경 정상화 시점에 자동 검증 가능.
 */
@Tag("integration")
@SpringBootTest
@Import(MySqlTestContainersConfig::class)
class PropertyRepositoryIntegrationTest
    @Autowired
    constructor(
        private val sut: PropertyRepositoryImpl,
        private val transactionTemplate: TransactionTemplate,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {

        @AfterEach
        fun cleanUp() {
            databaseCleanUp.truncateAllTables()
        }

        private fun seedPropertyWithTwoRoomTypes(): Property {
            val property = DomainFixtures.newProperty()
            property.addRoomType(
                name = "디럭스 더블",
                standardGuestCount = 2,
                maxGuestCount = 2,
                bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.DOUBLE, 1))),
                sizeSqm = 26,
                viewType = ViewType.OCEAN,
            )
            property.addRoomType(
                name = "패밀리 트윈",
                standardGuestCount = 2,
                maxGuestCount = 4,
                bedConfiguration = BedConfiguration(listOf(BedEntry(BedType.SINGLE, 2))),
                sizeSqm = 38,
                viewType = ViewType.MOUNTAIN,
            )
            return sut.save(property)
        }

        @Nested
        @DisplayName("Aggregate 왕복 — cascade 영속")
        inner class RoundTrip {

            @DisplayName("INT-PRP-01: register + addRoomType 2건 저장 후 재조회 시 본체·amenities·취소정책·객실(침대 구성) 전부 보존")
            @Test
            fun savesAndReloadsWholeAggregate() {
                val saved = seedPropertyWithTwoRoomTypes()

                assertThat(saved.id).isPositive()
                assertThat(saved.roomTypes).allSatisfy { assertThat(it.id).isPositive() }

                transactionTemplate.executeWithoutResult {
                    val found = sut.findById(saved.id)!!
                    assertThat(found.name).isEqualTo("스테이 제주")
                    assertThat(found.city).isEqualTo(City.JEJU)
                    assertThat(found.address.detailAddress).isEqualTo("애월로 100")
                    assertThat(found.address.zipCode).isEqualTo("63062")
                    assertThat(found.amenities).containsExactlyInAnyOrder(Amenity.WIFI, Amenity.PARKING)
                    assertThat(found.cancellationPolicy.rules)
                        .containsExactlyInAnyOrder(RefundRule(7, 100), RefundRule(3, 50), RefundRule(0, 0))
                    assertThat(found.roomTypes).hasSize(2)
                    val deluxe = found.roomTypes.first { it.name == "디럭스 더블" }
                    assertThat(deluxe.bedConfiguration.entries).containsExactly(BedEntry(BedType.DOUBLE, 1))
                    val family = found.roomTypes.first { it.name == "패밀리 트윈" }
                    assertThat(family.bedConfiguration.entries).containsExactly(BedEntry(BedType.SINGLE, 2))
                }
            }

            @DisplayName("INT-PRP-02: removeRoomType 후 저장하면 재조회 시 roomTypes 1건 (orphanRemoval)")
            @Test
            fun removesRoomTypeRow_onOrphanRemoval() {
                val saved = seedPropertyWithTwoRoomTypes()
                val removedId = saved.roomTypes.first { it.name == "패밀리 트윈" }.id

                saved.removeRoomType(removedId)
                sut.save(saved)

                transactionTemplate.executeWithoutResult {
                    val found = sut.findById(saved.id)!!
                    assertThat(found.roomTypes).hasSize(1)
                    assertThat(found.roomTypes.first().name).isEqualTo("디럭스 더블")
                }
            }
        }

        @Nested
        @DisplayName("findByCity — 도시 필터")
        inner class FindByCity {

            @DisplayName("INT-PRP-03: 도시가 다른 2개 숙소 중 해당 도시 숙소만 반환")
            @Test
            fun filtersByCity() {
                sut.save(DomainFixtures.newProperty(name = "스테이 제주", city = City.JEJU))
                sut.save(DomainFixtures.newProperty(name = "스테이 부산", city = City.BUSAN))

                val found = sut.findByCity(City.JEJU)

                assertThat(found).hasSize(1)
                assertThat(found.first().name).isEqualTo("스테이 제주")
            }
        }
    }
