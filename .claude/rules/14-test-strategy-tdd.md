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
| Unit | `modules/domain/src/test/kotlin/com/stay/domain/**/*Test.kt` (도메인 코드와 같은 모듈 — Q7 이동 반영) | JUnit5 (Spring X) | VO, Aggregate, Domain Service |
| Integration | `apps/stay-api/src/test/.../application/**/*IntegrationTest.kt`, `.../infrastructure/**/*IntegrationTest.kt` | `@SpringBootTest` + Testcontainers MySQL | Service, Repository 어댑터 |
| E2E | `.../interfaces/api/**/*E2ETest.kt` | `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` | Controller |

### TDD 사이클 — 단계별 목적 (2026-06-10 사용자 정의, round-3 Q1)
1. **Red — "어떤 걸 검증할 것인가"**: 명세(요구사항·LLD·카탈로그) 기반으로 실패하는 테스트 작성. 테스트가 명세의 실행 가능한 표현이 되게 한다. 카탈로그 ID 를 `@DisplayName("<CATALOG-ID> ...")` 으로 노출 (e.g., `LID-01`)
2. **Green — 테스트 ↔ 프로덕션 일치 증명**: 테스트 작성과 **분리된 구현 에이전트**가 *테스트 코드와 명세만* 보고 통과시키는 **최소 구현**. 작성자·구현자 분리로 "테스트가 명세를 대변한다"는 증명이 성립한다. 구현 결과는 [검수 게이트](./05-code-review-gate.md) 대로 chat 제안 → 승인 → Write
3. **Refactor — 객체지향 재구성**: Green 의 "성공만을 위한" 코드를 되돌아보며 객체지향(하는 것/아는 것 분리, 역할·책임·경계·협력 기반 객체)으로 정리. **변경 한 번마다 테스트를 지속 실행**하는 것이 원칙. 다음 사이클 진입 전 **전체 기존 테스트 회귀 통과** 확인

### 검증 명령
- 단위 테스트만 빠르게: `./gradlew :apps:stay-api:test --tests "com.stay.domain.*"`
- 컴파일+lint+assemble (Docker 불요): `./gradlew clean ktlintCheck build -x test`
- 전체 (Docker 필요): `./gradlew build` — 환경에 따라 Testcontainers 컨텍스트 테스트가 실패할 수 있음 (코드 회귀 아닐 가능성 인지)
- 등급별 실행: [`17-test-categorization.md`](./17-test-categorization.md) 의 `-DtestTag=...` 옵션 참조

### 카탈로그
- 각 라운드 `docs/round-N/02-tdd-plan.md` Part B 에 (ID · Given · When · Then) 표
- 테스트 클래스/메서드는 카탈로그 ID 를 `@DisplayName` 으로 노출 → 역추적성 확보

### 환경 이슈 vs 코드 회귀 구분

테스트 실패 시 다음 체크리스트로 분류한다:

- ✅ 실패 메시지가 `DockerClientProviderStrategy` 또는 `Could not find a valid Docker environment` 를 포함하면 → **환경 의존 실패** (메모리 `testcontainers-docker-desktop-incompat` 참조)
- ✅ 실패 메시지가 `Unresolved reference`, `Compilation error` 면 → **코드 회귀**
- ✅ 실패 메시지가 `AssertionError`, `expected: ... actual: ...` 면 → **코드 회귀**
- ✅ 환경 의존 실패는 `./gradlew clean ktlintCheck build -x test` + 단위테스트 통과 시 *"코드 회귀 아님"* 으로 보고에 명시
- ✅ 보고는 *어떤 등급은 통과 / 어떤 등급은 환경 차단* 으로 분리해 표시 ([`17-test-categorization.md`](./17-test-categorization.md) 의 L1\~L4 활용)

## References
- 발제: `docs/curriculum/round-1.md` "테스트 피라미드", "TDD"
- 계획: `docs/round-1/02-tdd-plan.md` Part B (카탈로그), Part C (사이클 순서)
- 환경 제약: 메모리 `testcontainers-docker-desktop-incompat`
- 분류 시스템: [`17-test-categorization.md`](./17-test-categorization.md)
