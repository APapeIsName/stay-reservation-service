# 유일성 제약 — 어플 선검사 + DB unique 둘 다

## Rule
유일성 검사가 필요한 식별자(`loginId` 등) 는 **두 곳에서 보장한다**:
① **애플리케이션 선검사**로 친절한 도메인 예외 (`CoreException(CONFLICT)`)
② **DB unique 제약**으로 동시성 안전망

## Why
- 어플 선검사만으로는 **동시 가입(race condition)** 을 막을 수 없음 — 두 트랜잭션이 동시에 select 후 둘 다 insert 시도 가능
- DB unique 만으로도 안전하나, 에러 메시지가 SQL 예외 형태 → 사용자 친화성 떨어짐
- 두 층으로 95% 케이스는 어플에서 친절히, 5% 경합은 DB 에서 안전하게

## How to apply
- 어플: Service 에서 `repository.existsByLoginId(loginId)` 후 true 면 `throw CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID 입니다.")`
- DB: JPA 컬럼/테이블에 unique 제약 (`@Column(unique = true)` 또는 `@Table(uniqueConstraints = ...)`)
- `DataIntegrityViolationException` 도 `ApiControllerAdvice` 에서 `CONFLICT` 로 변환 ([응답 + 예외 매핑](./09-api-response-and-exception-mapping.md))
- 통합 테스트로 두 경로 모두 검증 (FAC-02 선검사 / FAC-03 동시성)
- **위반 신호**: 한 쪽만 적용 / DB 위반 메시지가 클라이언트에 그대로 노출 / 어플 선검사 결과를 캐시·트랜잭션 외에 두어 race 무방비

## References
- 결정: `docs/round-1/01-signup-requirements.md` D-6, R-1
- 카탈로그: `docs/round-1/02-tdd-plan.md` FAC-02, FAC-03
