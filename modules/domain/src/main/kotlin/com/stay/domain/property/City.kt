package com.stay.domain.property

/**
 * 서비스 대상 도시 enum — URL/검색 파라미터용 소문자 code 보유.
 *
 * Refs:
 *  - docs/design/03-class-diagram.md §7.1 enum
 */
enum class City(val code: String) {
    SEOUL("seoul"),
    JEJU("jeju"),
    BUSAN("busan"),
    GANGNEUNG("gangneung"),
}
