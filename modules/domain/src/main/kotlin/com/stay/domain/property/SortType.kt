package com.stay.domain.property

/**
 * 검색 결과 정렬 기준 enum.
 *  - RECOMMENDED: 추천순 — 기준 미정 (S-2 잠정), 현재 입력 순서 유지 fallback
 *  - RATING_DESC: 평점순 — rating 필드 부재 (S-1 잠정), 현재 입력 순서 유지 fallback
 *
 * Refs:
 *  - docs/design/03-class-diagram.md §7.1 enum
 */
enum class SortType {
    RECOMMENDED,
    PRICE_ASC,
    RATING_DESC,
    WISHES_DESC,
}
