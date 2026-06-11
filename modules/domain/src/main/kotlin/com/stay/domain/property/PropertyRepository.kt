package com.stay.domain.property

/**
 * Property Aggregate 의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층 책임 (DIP — rule 19). Application Service 는 본 port 타입으로만
 * 주입받아 영속 기술과 무관하게 테스트 가능 (Fake 주입).
 *
 * 메서드 추가 정책: 찜·검색 (Round 3) 에 필요한 contract 만 시작. YAGNI.
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.5 WishlistService (WSVC-01~07)
 *  - .claude/rules/19-layered-architecture-dip.md (port 는 domain, adapter 는 infrastructure)
 */
interface PropertyRepository {

    /**
     * id 로 Property 단건 조회. 미존재 시 null — 호출부가 NOT_FOUND 매핑 책임.
     */
    fun findById(id: Long): Property?

    /**
     * 도시 단위 숙소 목록 조회 (검색 진입점).
     */
    fun findByCity(city: City): List<Property>

    /**
     * Property 영속. 저장된 Property 반환.
     */
    fun save(property: Property): Property
}
