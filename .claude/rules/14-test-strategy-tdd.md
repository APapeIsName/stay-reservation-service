# 테스트 전략 + TDD

## Rule
- 테스트는 **3 계층**으로 분리: 단위 / 통합 / E2E
- 단위 테스트는 **Spring 없이** 수행 (`@SpringBootTest` 없이, 순수 JVM)
- 모든 기능은 **TDD Red → Green → Refactor 사이클**로 도입
- 각 사이클은 [코드 검수 게이트](./05-code-review-gate.md) 에 따라 단계별 (Red/Green/Refactor) 독립 승인

## Why
- 단위가 빠르고 많을수록 회귀 비용·디버깅 비용이 줄어듦
- TDD: 요구사항을 테스트로 먼저 표현 → 설계 단위가 자연스럽게 잘게 쪼개지고, 인터페이스가 사용자 관점에서 도출됨
- Spring 없는 단위테스트는 [검증을 도메인 VO 로 일원화](./06-validation-via-domain-vo.md) 와 짝 — 검증을 도메인에 가두면 Spring 컨텍스트 불필요

## How to apply

### 계층
| 계층 | 위치 | 환경 | 대상 |
|---|---|---|---|
| Unit | `apps/stay-api/src/test/kotlin/com/stay/domain/**/*Test.kt` | JUnit5 + Mockito (Spring X) | VO, Aggregate |
| Integration | `.../application/**/*IntegrationTest.kt` | `@SpringBootTest` + Testcontainers MySQL | Service, Repository |
| E2E | `.../interfaces/api/**/*E2ETest.kt` | `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` | Controller |

### TDD 사이클
1. **Red**: 실패하는 테스트 작성. 카탈로그 ID 를 `@DisplayName("<CATALOG-ID> ...")` 으로 노출 (e.g., `LID-01`)
2. **Green**: 통과시키는 **최소 구현**
3. **Refactor**: 중복/네이밍/응집 정리. 다음 사이클 진입 전 **전체 기존 테스트 회귀 통과** 확인

### 검증 명령
- 단위 테스트만 빠르게: `./gradlew :apps:stay-api:test --tests "com.stay.domain.*"`
- 컴파일+lint+assemble (Docker 불요): `./gradlew clean ktlintCheck build -x test`
- 전체 (Docker 필요): `./gradlew build` — 환경에 따라 Testcontainers 컨텍스트 테스트가 실패할 수 있음 (코드 회귀 아닐 가능성 인지)

### 카탈로그
- 각 라운드 `docs/round-N/02-tdd-plan.md` Part B 에 (ID · Given · When · Then) 표
- 테스트 클래스/메서드는 카탈로그 ID 를 `@DisplayName` 으로 노출 → 역추적성 확보

## References
- 발제: `docs/curriculum/round-1.md` "테스트 피라미드", "TDD"
- 계획: `docs/round-1/02-tdd-plan.md` Part B (카탈로그), Part C (사이클 순서)
- 환경 제약: 메모리 `testcontainers-docker-desktop-incompat`
