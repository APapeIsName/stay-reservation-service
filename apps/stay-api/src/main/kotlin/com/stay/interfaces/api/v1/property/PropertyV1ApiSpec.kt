package com.stay.interfaces.api.v1.property

import com.stay.domain.property.City
import com.stay.domain.property.SortType
import com.stay.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.LocalDate

/**
 * 숙소 V1 API OpenAPI 스펙. Controller 가 구현.
 * 보존 골격 패턴 — OpenAPI 어노테이션을 interface 에 분리해 Controller 가 비즈니스 흐름만 가지도록.
 */
@Tag(name = "Property V1 API", description = "숙소 V1 API")
interface PropertyV1ApiSpec {

    @Operation(
        summary = "숙소 검색",
        description = "도시·기간·인원 조건으로 예약 가능한 숙소 목록을 조회한다. 정렬 기본값은 RECOMMENDED.",
    )
    fun search(
        city: City,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
        sort: SortType,
        page: Int,
        size: Int,
    ): ApiResponse<PropertyV1Dto.SearchResponse>

    @Operation(
        summary = "숙소 상세 조회",
        description = "숙소 상세와 기간·인원 조건을 만족하는 가용 객실 타입 옵션(합산가 포함)을 조회한다.",
    )
    fun getPropertyDetail(
        propertyId: Long,
        checkIn: LocalDate,
        checkOut: LocalDate,
        guests: Int,
    ): ApiResponse<PropertyV1Dto.DetailResponse>
}
