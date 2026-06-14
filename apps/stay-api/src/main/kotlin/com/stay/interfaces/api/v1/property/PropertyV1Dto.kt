package com.stay.interfaces.api.v1.property

import com.stay.application.property.PropertyDetailInfo
import com.stay.application.property.PropertySearchItemInfo

/**
 * 숙소 V1 API DTO container.
 *  - Response 는 application Info 에서 `from` 정적 팩토리로 변환 (rule 20 — 레이어별 DTO 분리)
 *  - 검색/상세 모두 조회 전용 — Request DTO 없음 (쿼리 파라미터는 Controller 가 SearchCriteria 로 조립)
 */
class PropertyV1Dto private constructor() {

    data class SearchResponse(
        val items: List<SearchItem>,
    ) {
        data class SearchItem(
            val propertyId: Long,
            val name: String,
            val minTotalPrice: Long,
            val wishCount: Long,
        ) {
            companion object {
                fun from(info: PropertySearchItemInfo): SearchItem =
                    SearchItem(
                        propertyId = info.propertyId,
                        name = info.name,
                        minTotalPrice = info.minTotalPrice,
                        wishCount = info.wishCount,
                    )
            }
        }

        companion object {
            fun from(infos: List<PropertySearchItemInfo>): SearchResponse =
                SearchResponse(items = infos.map { SearchItem.from(it) })
        }
    }

    data class DetailResponse(
        val propertyId: Long,
        val name: String,
        val wishCount: Long,
        val roomTypes: List<RoomTypeOption>,
    ) {
        data class RoomTypeOption(
            val roomTypeId: Long,
            val name: String,
            val maxGuestCount: Int,
            val totalPrice: Long,
        ) {
            companion object {
                fun from(option: PropertyDetailInfo.RoomTypeOption): RoomTypeOption =
                    RoomTypeOption(
                        roomTypeId = option.roomTypeId,
                        name = option.name,
                        maxGuestCount = option.maxGuestCount,
                        totalPrice = option.totalPrice,
                    )
            }
        }

        companion object {
            fun from(info: PropertyDetailInfo): DetailResponse =
                DetailResponse(
                    propertyId = info.propertyId,
                    name = info.name,
                    wishCount = info.wishCount,
                    roomTypes = info.roomTypes.map { RoomTypeOption.from(it) },
                )
        }
    }
}
