# 테스트 분류 (Test Categorization)

## Rule
모든 테스트는 **4단계 등급** 으로 분류·태깅된다. 변경 영향에 따라 적절한 등급만 실행하여 *피드백 루프* 를 최소화한다.

## Why
- 등급별 *언제 실행할지* 다름 — 변경 코드 책임에 맞춰 빠른 피드백
- 에이전트가 "변경 코드 책임 → 실행할 테스트 등급" 자동 매핑 가능
- 환경 의존(Docker 등) 과 환경 무관 분리 — 환경 이슈가 fast 테스트를 막지 않음

## How to apply

### 1. 4단계 등급

| 등급 | 이름 | 환경 | 속도 | 예시 | `@Tag` |
|---|---|---|---|---|---|
| **L1** | Pure Unit | JVM only | < 1ms | LoginIdTest, BirthDateTest, NameTest, ... | `unit` |
| **L2** | Slow Unit | JVM + 느린 라이브러리 (BCrypt 등) 또는 mock | 50\~500ms | PasswordTest, UserTest, UserServiceTest | `slow-unit` |
| **L3** | Integration | `@SpringBootTest` + Testcontainers | 5\~30s | UserServiceIntegrationTest | `integration` |
| **L4** | E2E | `@SpringBootTest(RANDOM_PORT)` + HTTP | 10\~60s | UserV1ApiE2ETest | `e2e` |

### 2. `@Tag` 부착

```kotlin
@Tag("unit")
class LoginIdTest { ... }

@Tag("slow-unit")
class PasswordTest { ... }

@Tag("integration")
class UserServiceIntegrationTest(...) { ... }

@Tag("e2e")
class UserV1ApiE2ETest(...) { ... }
```

### 3. Gradle 실행 명령

`build.gradle.kts` 의 `tasks.test {}` 설정:

```kotlin
tasks.test {
    useJUnitPlatform {
        System.getProperty("testTag")?.let { includeTags(it) }
    }
}
```

명령어:
- 전체: `./gradlew test`
- L1 만: `./gradlew test -DtestTag=unit`
- L1 + L2: `./gradlew test -DtestTag="unit | slow-unit"`
- L3 만: `./gradlew test -DtestTag=integration`
- L4 만: `./gradlew test -DtestTag=e2e`

### 4. 신규 테스트 추가 체크리스트

- ✅ `@Tag` 가 4단계 중 하나로 부착됐다
- ✅ 등급 결정 기준 (속도 + 환경 의존성) 에 부합한다
- ✅ 같은 도메인·계층의 기존 테스트와 등급이 일관된다
- ✅ CI 파이프라인 단계 (L1 → L2 → L3 → L4 fail-fast) 와 정합한다

### 5. 변경 영향 → 실행 등급 매핑

| 변경 코드 위치 | 자동 실행할 등급 |
|---|---|
| `modules/domain/.../user/*VO*` | L1 |
| `modules/domain/.../user/Password.kt` (BCrypt 호출) | L1 + L2 |
| `modules/domain/.../user/User.kt` (Aggregate) | L1 + L2 |
| `apps/stay-api/.../application/*` | L2 |
| `apps/stay-api/.../infrastructure/*` | L3 (Docker 환경 가능 시) |
| `apps/stay-api/.../interfaces/*` | L4 (Docker 환경 가능 시) |
| 빌드 설정 / Gradle | L1 + L2 + 컴파일 + assemble |

변경 코드 → 실행 결정 체크리스트:
- ✅ 변경 위치 → 적용 등급을 위 표로 매핑했다
- ✅ Docker 환경 가용성 확인 후 L3/L4 실행 여부 결정했다
- ✅ 실패 발생 시 [`14-test-strategy-tdd.md`](./14-test-strategy-tdd.md) 의 환경 이슈 vs 코드 회귀 분류를 적용했다

### 6. 에이전트 자동화 효과 (참고)

| 시나리오 | 분류 후 가능해지는 것 |
|---|---|
| "도메인만 변경" | `-DtestTag=unit` 1초 피드백 |
| "infrastructure 변경" | `-DtestTag=integration` 만 (Docker 가용 시) |
| "PR 빌드 단계" | L1 → L2 → L3 → L4 fail-fast 파이프라인 |
| "야간 회귀 vs 매 커밋" | L1+L2 매 커밋, L3+L4 야간 |
| "에이전트의 변경 영향 분석" | 변경 위치만으로 등급 자동 결정 |

## References
- 분류 제안: 2026-05-25 (사용자 — "에이전트 자동화도 쉬워질 거 같아서")
- 보완 rule: [`14-test-strategy-tdd.md`](./14-test-strategy-tdd.md) (TDD 사이클 + 환경 분류)
