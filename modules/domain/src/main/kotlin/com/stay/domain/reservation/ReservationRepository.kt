package com.stay.domain.reservation

/**
 * Reservation Aggregate 의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층 책임 (DIP — rule 19). Application Service 는 본 port 타입으로만
 * 주입받아 영속 기술과 무관하게 테스트 가능 (Fake 주입).
 *
 * 메서드 추가 정책: 예약 생성 (Round 3) 에 필요한 contract 만 시작. YAGNI.
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.5 ReservationService (RSVC-01~13)
 *  - .claude/rules/19-layered-architecture-dip.md (port 는 domain, adapter 는 infrastructure)
 */
interface ReservationRepository {

    /**
     * id 로 Reservation 단건 조회. 미존재 시 null — 호출부가 NOT_FOUND 매핑 책임.
     */
    fun findById(id: Long): Reservation?

    /**
     * Reservation 영속. 저장된 Reservation 반환.
     */
    fun save(reservation: Reservation): Reservation
}
