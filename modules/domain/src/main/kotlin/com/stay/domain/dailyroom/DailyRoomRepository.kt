package com.stay.domain.dailyroom

import java.time.LocalDate

/**
 * DailyRoom Aggregate 의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층 책임 (DIP — rule 19). Application Service 는 본 port 타입으로만
 * 주입받아 영속 기술과 무관하게 테스트 가능 (Fake 주입).
 *
 * 메서드 추가 정책: 숙소 상세·검색 (Round 3) 에 필요한 contract 만 시작. YAGNI.
 *
 * Refs:
 *  - docs/round-3/02-tdd-plan.md B.5 PropertyService (PSVC-01~07·20)
 *  - .claude/rules/19-layered-architecture-dip.md (port 는 domain, adapter 는 infrastructure)
 */
interface DailyRoomRepository {

    /**
     * roomType 의 일자 구간 DailyRoom 목록 조회 — from·to 양끝 포함.
     */
    fun findByRoomTypeAndDateBetween(roomTypeId: Long, from: LocalDate, to: LocalDate): List<DailyRoom>
}
