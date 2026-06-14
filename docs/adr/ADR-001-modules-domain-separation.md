# ADR-001 — `modules/domain` 으로 도메인 분리

**Status**: Accepted
**Date**: 2026-05-22
**Round**: Round 1 (Cycle 6 시점 리팩토링)

---

## Context

본 프로젝트는 부트캠프 템플릿 (`loop-pack-be-l2-vol3-kotlin`) 의 멀티모듈 Gradle 골격을 보존하면서 *숙박 예매 서비스* 를 처음부터 쌓아 올리는 학습 프로젝트다. 부트캠프 원본은 아래 패턴으로 도메인 코드를 두었다:

```
apps/commerce-api/src/main/kotlin/com/loopers/
  ├─ domain/example/        ExampleModel, ExampleService, ExampleRepository
  ├─ application/example/   ExampleFacade
  ├─ infrastructure/example/ ExampleJpaRepository, ExampleRepositoryImpl
  └─ interfaces/api/example/ ExampleV1Controller, Dto, ApiSpec
```

즉 **도메인 객체가 apps/ 안에** 살았다. Round 1 Cycle 1\~6 (LoginId\~RawPassword VO 6종) 을 이 패턴 그대로 답습해 진행하던 중, Cycle 7 에서 BCrypt 의존성 위치 결정을 논의하면서 사용자가 다음을 지적했다:

> *"api 안에 vo 가 있어?"*

이 한 줄이 더 근본적인 질문을 끌어냈다. 본 프로젝트의 모듈 컨벤션 (`README.md`) 은:

| 디렉터리 | 정의 |
|---|---|
| `apps/` | 실행 가능한 **SpringBootApplication** |
| `modules/` | 특정 구현·도메인에 의존적이지 않고 **reusable 한 configuration / 자산** |
| `supports/` | logging·monitoring 같은 **add-on** |

`apps/` 는 *"실행 단위"* 의미인데, 도메인 객체가 *실행 단위에 종속될 이유가 없다*. 다른 앱 (stay-batch, stay-streamer) 에서도 같은 User 도메인을 쓸 수 있어야 자연.

## Decision

**도메인 객체 (Aggregate, VO, Repository 포트) 를 `modules/domain` 모듈로 분리한다.**

구체적으로:
- `apps/stay-api/.../domain/user/` → `modules/domain/.../domain/user/`
- 17개 파일 이동 — LoginId, Name, PhoneNumber, Email, BirthDate, RawPassword, Password, User, UserRepository (interface) + 테스트
- `CoreException` / `ErrorType` 도 동반 이동 (도메인이 throw 하므로)
- `ErrorType` 의 `HttpStatus` 의존 제거 → `Int statusCode` 로 변경, web 의존성은 `ApiControllerAdvice` 에 격리

## Consequences

### ✅ 긍정적
- **모듈 컨벤션 충실** — apps = 실행 단위, modules = 재사용 자산. README 정의에 부합
- **다른 앱에서 재사용 가능** — `implementation(project(":modules:domain"))` 한 줄로 stay-batch / stay-streamer 에서 User 도메인 활용
- **도메인 의존성 격리** — BCrypt 같은 도메인 dep 가 modules/domain 에 자연 격리. apps/stay-api 의 build.gradle.kts 가 가벼움
- **modules/domain 이 spring-web 무의존** — `ErrorType` 의 HttpStatus 제거로 *도메인 모듈이 web 프레임워크에 의존하지 않음* 이 보장됨. 클린 아키텍처 의존 방향 규칙 부합
- **테스트 격리** — domain 모듈 테스트는 Spring Context 불필요 (L1 Pure Unit 테스트)

### ⚠️ 부정적 / 비용
- **이동 비용** — 17 파일 + 2 빌드파일 (apps/stay-api/build.gradle.kts + modules/domain/build.gradle.kts) + 4 docs 갱신
- **Cycle 6 진행 중 리팩토링** — 6 사이클의 코드를 그대로 옮기는 작업이 사이클 진행 흐름을 잠시 끊음
- **컨벤션 학습 부담** — 신규 진입자가 *"왜 도메인이 apps 가 아닌 modules 에?"* 를 이해해야 함

### 🔄 후속 영향
- **Round 2 이후 모든 새 도메인 (Property, RoomType, Reservation, DailyRoom, Wishlist) 이 modules/domain 안에 자연 위치**. 이 결정이 없었다면 Round 2 진입 시 17 파일이 *수십\~수백 파일* 로 늘어 이동 비용이 폭증
- **Aggregate Root 정의 시 Repository 포트 위치도 modules/domain** — 영속 구현체는 apps/stay-api/infrastructure 에 (Hexagonal/Ports & Adapters 패턴)
- **infrastructure 패키지 명칭이 자연스러워짐** — `apps/stay-api/.../infrastructure/user/UserRepositoryImpl` 가 *"포트 (modules/domain) 의 어댑터 (apps)"* 라는 의미가 분명

## Alternatives Considered

### 대안 A — 부트캠프 패턴 그대로 (apps/stay-api 안에 도메인)
- 비용: 0 (그대로 진행)
- 거절 이유:
  - 모듈 컨벤션 위반 — apps = 실행 단위 정의에 모순
  - 도메인 의존성이 apps 에 박힘 — apps 가 무거워짐
  - 다른 앱에서 재사용 시 코드 이동 필요
  - 6 사이클 시점이 *마지막 저비용 이동 타이밍* — 미루면 비용 폭증

### 대안 B — `apps/stay-api` 안에 두되 도메인 패키지를 `domain/` 으로 명확히 분리
- 비용: 매우 낮음 (디렉터리명 정리만)
- 거절 이유:
  - 모듈 경계 강제력 부족 — Gradle 의존성으로 *컴파일 타임 격리* 안 됨
  - 다른 앱 재사용 시 여전히 코드 이동 필요
  - 모듈 컨벤션 위반은 여전

### 대안 C — 별도 `modules/user-domain` 도메인별 모듈 분리
- 비용: 매우 높음 (Aggregate 마다 모듈)
- 거절 이유:
  - Round 2 의 6 도메인 = 6 모듈 폭증
  - 도메인 간 참조 시 의존 그래프 복잡
  - 학습 프로젝트 규모에 과함 — 단일 `modules/domain` 이 적정

### 대안 D — DDD Bounded Context 별 멀티 모듈
- 비용: 매우 높음 (전사적 컨텍스트 매핑 필요)
- 거절 이유:
  - 단일 팀·단일 서비스 학습 프로젝트에 과함
  - 이 단계에서는 *모놀리스 + 멀티모듈* 이 적정

## Related

- 사용자 지적: 2026-05-21 ("api 안에 vo 가 있어?")
- 관련 questions: [`docs/round-1/03-questions.md` Q7](../round-1/03-questions.md) — *도메인 코드는 어느 모듈에 두는가*
- 관련 questions: [`docs/round-1/03-questions.md` Q8](../round-1/03-questions.md) — *UserFacade 는 뭐 하는 거야* (Application 계층 위치 명료화)
- 관련 design: [`docs/design/00-overview.md § 2.3 모듈 의존 방향`](../design/00-overview.md)
- 관련 rule: [`.claude/rules/`](../../.claude/rules/) 의 module 경계 규칙
- 부가 결정: `ErrorType` HttpStatus 의존 제거 (web 의존성을 web 계층에 격리) — 이 ADR 의 자연 후속물

---

## 면접 답변 템플릿

> "초반엔 부트캠프 템플릿 패턴 그대로 `apps/` 안에 도메인을 두고 진행했는데, BCrypt 의존성 위치 논의에서 *'도메인이 apps 안에 사는 게 어색하지 않냐'* 는 사용자 지적이 나와 6 사이클 시점에 `modules/domain` 으로 분리했습니다.
>
> 이로써 ① 모듈 컨벤션 (apps = 실행 단위, modules = 재사용 자산) 충실, ② 도메인 의존성을 도메인 모듈에 격리, ③ 추후 stay-batch 등 다른 앱에서 User 도메인 재사용 자연스러움 — 세 가지를 동시 달성했습니다.
>
> 추가로 이동 중 `ErrorType` 의 `HttpStatus` 의존을 제거해 *도메인 모듈이 spring-web 에 의존하지 않도록* 정리했습니다. *클린 아키텍처의 의존 방향 규칙* (도메인이 프레임워크를 모름) 을 자연스럽게 적용한 셈입니다."
