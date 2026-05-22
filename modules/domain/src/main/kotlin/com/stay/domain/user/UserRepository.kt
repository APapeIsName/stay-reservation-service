package com.stay.domain.user

/**
 * User Aggregate 의 영속 진입점 인터페이스 (Port).
 *
 * 구현은 infrastructure 계층(`UserRepositoryImpl`) 책임. 도메인은 인터페이스만 의존하여
 * 영속 기술(JPA, NoSQL, in-memory 등) 과 무관하게 테스트 가능 (Mock/Fake 주입).
 *
 * 메서드 추가 정책: 회원가입(Round 1) 에 필요한 contract 만 시작. `findByLoginId` 등
 * 로그인·인증 흐름이 추가될 때 (Round 2 이상) 점진적으로 확장. YAGNI.
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §1
 *  - .claude/rules/06-validation-via-domain-vo.md (도메인 무의존)
 *  - .claude/rules/10-uniqueness-app-and-db.md (어플 선검사 + DB unique)
 */
interface UserRepository {

    /**
     * 동일 `loginId` 의 User 존재 여부. 가입 시 어플리케이션 선검사용 (DB unique 안전망과 이중 보장).
     */
    fun existsByLoginId(loginId: LoginId): Boolean

    /**
     * User 영속. id 가 부여된 User 반환 (JPA IDENTITY 전략 기준 INSERT 후 반환값).
     */
    fun save(user: User): User
}
