package com.stay.interfaces.api.v1.property

import com.stay.application.property.PropertyService
import com.stay.domain.property.City
import com.stay.domain.property.SearchCriteria
import com.stay.domain.property.SortType
import com.stay.interfaces.api.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * 숙소 V1 API Controller. 얇은 계층 — 쿼리 파라미터 변환 + Service 호출 + 응답 매핑.
 *
 * 흐름:
 *  - search: 쿼리 → SearchCriteria → PropertyService.search → List<PropertySearchItemInfo> → SearchResponse
 *  - getPropertyDetail: 쿼리 → PropertyService.getPropertyDetail → PropertyDetailInfo → DetailResponse
 *  - 기간 검증(0박·역전) 은 Service 의 DateRange VO 위임 (rule 06) — 예외는 ApiControllerAdvice 가 일관 처리
 */
@RestController
@RequestMapping("/api/v1/properties")
class PropertyV1Controller(
    private val propertyService: PropertyService,
) : PropertyV1ApiSpec {

    @GetMapping
    override fun search(
        @RequestParam city: City,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkIn: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkOut: LocalDate,
        @RequestParam guests: Int,
        @RequestParam(defaultValue = "RECOMMENDED") sort: SortType,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ApiResponse<PropertyV1Dto.SearchResponse> {
        val criteria = SearchCriteria(
            city = city,
            checkIn = checkIn,
            checkOut = checkOut,
            guests = guests,
            sort = sort,
            page = page,
            size = size,
        )
        return ApiResponse.success(PropertyV1Dto.SearchResponse.from(propertyService.search(criteria)))
    }

    @GetMapping("/{propertyId}")
    override fun getPropertyDetail(
        @PathVariable propertyId: Long,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkIn: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) checkOut: LocalDate,
        @RequestParam guests: Int,
    ): ApiResponse<PropertyV1Dto.DetailResponse> {
        val info = propertyService.getPropertyDetail(propertyId, checkIn, checkOut, guests)
        return ApiResponse.success(PropertyV1Dto.DetailResponse.from(info))
    }
}
