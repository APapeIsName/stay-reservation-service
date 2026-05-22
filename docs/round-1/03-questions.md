# Round 1 — 작업 중 떠오른 질문

> TDD 사이클을 돌면서 떠오른 궁금증을 답변과 함께 누적하는 Q&A 로그.
> 면접 자산: "왜 이렇게 했냐?" 에 답할 수 있는 근거 모음.
>
> 각 항목에는 **출처 표기**(질의자: 사용자 / 제안자: Claude) 를 명시해 누가 화두를 던졌는지 구분한다.

---

## Q1. (2026-05-21) JUnit `@Nested` 에서 `inner class` vs 그냥 `class` ?

**질의자**: 사용자

### 맥락
`LoginIdTest` 에서 케이스를 `@Nested inner class Valid / InvalidLength / InvalidCharset` 로 그룹화. `inner` 키워드가 꼭 필요한지.

### 답 — `inner class` 필수
**JUnit Jupiter 의 `@Nested` 는 non-static 중첩 클래스만 발견·실행한다.**

| Kotlin 표현 | JVM | JUnit 5 |
|---|---|---|
| `class X { class Inner { ... } }` (중첩) | static class | **발견 안 됨** (테스트 조용히 누락) |
| `class X { inner class Inner { ... } }` | non-static inner class | 발견·실행 ✓ |

JUnit 5 User Guide 명시: "JUnit Jupiter will only execute `@Nested` test classes that are non-static."

### 트레이드오프
- **장점 (`inner`)**: 외부 인스턴스 참조 보유 → 외부의 helper(`assertBadRequest`), fixture 를 그대로 사용 가능. 케이스 그룹화에 자연스러움
- **비용**: 미세한 메모리 (외부 인스턴스 참조) — 테스트 컨텍스트에선 무시 가능
- **주의**: 외부의 **mutable 상태**에 의존하기 시작하면 테스트 격리성 깨질 위험. 외부 helper 는 **stateless** 로 유지하고, fixture 가 필요하면 `@BeforeEach` 로 매 테스트 초기화

### 우리 코드에서
`LoginIdTest.assertBadRequest(input)` 는 무상태 helper → `inner` 가 안전하게 가치 발휘.

### 출처
- JUnit 5 User Guide — [Nested Tests](https://junit.org/junit5/docs/current/user-guide/#writing-tests-nested)
- Kotlin Doc — [Nested and inner classes](https://kotlinlang.org/docs/nested-classes.html)

---

## Q2. (2026-05-21) Kotlin VO 를 `data class` 로 vs `@JvmInline value class` 로?

**제안자**: Claude (Cycle 1 Green 단계에서 선택의 트레이드오프를 기록할 가치가 있다고 판단)

### 맥락
`LoginId` 같은 단일 String 래퍼 VO 를 만들 때 Kotlin 의 두 가지 VO 표현 중 어떤 것을 채택할지.

### 두 선택지 비교

| 측면 | `data class LoginId(val value: String)` | `@JvmInline value class LoginId(val value: String)` |
|---|---|---|
| 런타임 비용 | VO 인스턴스마다 객체 1개 할당 | **0** (가능한 경우 JVM 바이트코드에서 String 으로 인라이닝) |
| `equals` / `hashCode` / `toString` | 자동 생성 (모든 프로퍼티 기준) | 자동 생성 (값 기준) |
| `copy()` 메서드 | 있음 (불필요한 변형 가능성 존재) | 없음 |
| JPA `@Embeddable` 친화도 | **자연 지원** — `kotlin-jpa` 플러그인이 no-arg 생성자를 만들어줌 | **불가** — 필드마다 `AttributeConverter<X, String>` 작성/등록 필요 |
| Java 측 호출 호환 | 그대로 호환 | 메서드 시그니처에서 `LoginId` 가 `String` 으로 풀려 노출 (간헐적 혼란) |
| 멘탈 모델 | 표준 / 친숙 | Kotlin 2.x 모던 VO 패턴 |

### Round 1 결정 — **`data class` 채택**

이유:
1. **Cycle 8** 에서 User Aggregate 에 `@Embedded LoginId` 로 매핑 예정 — `data class` + `@Embeddable` 조합이 보일러 0
2. value class 의 zero-cost 이점은 회원가입 1\~2건/요청 단위 처리에서 의미 없음
3. VO 가 7개 (LoginId, RawPassword, Password, Name, BirthDate, Email, PhoneNumber) — 각각 `AttributeConverter` 작성/등록은 과한 비용
4. `copy()` 의 위험은 도메인 외부에 VO 가 거의 노출되지 않는 특성상 사실상 비활성 (외부는 application Facade 가 Command/Info DTO 로 차단)

### 재검토 트리거
- 같은 VO 를 hot path 에서 초당 수만 건 생성하는 케이스 발생
- JPA 가 아닌 다른 영속(NoSQL / 외부 API) 으로 이행
- `copy()` 우회로 도메인 불변성이 깨진 사례가 한 번이라도 관측됨

### 출처
- Kotlin Doc — [Inline value classes](https://kotlinlang.org/docs/inline-classes.html)
- 결정 기록: `docs/round-1/02-tdd-plan.md` Part A
- 관련 rule: [`07-domain-jpa-integration.md`](../../.claude/rules/07-domain-jpa-integration.md)

---

## Q3. (2026-05-21) 단일 역할·중복 없는 필드도 VO 로 감싸야 하는가? "낭비 vs 취향차이"

**질의자**: 사용자

### 사용자 입장
> "phoneNumber 처럼 각자의 역할은 가지지만 중복되지 않는 경우 VO 가 낭비인가? 취향차이로 본다."

### 답 — 부분 동의. **순수 취향이 아니라 4축 트레이드오프**

VO 의 가치는 아래 4축으로 평가 가능. 모두 충족되면 "낭비 아님", 절반 이하면 "취향차이" 로 수렴.

| 축 | 평가 질문 | PhoneNumber 의 경우 |
|---|---|---|
| **(1) 타입 안전성** | 같은 `String` 타입의 다른 필드와 **인자 순서 실수**로 뒤바뀔 위험이 있는가? | ✅ 회원 도메인에 String 필드 5개 공존 — `signup(name, phone, email, ...)` 가 모두 `String` 이면 swap 사일런트 실패. VO 면 컴파일 에러 |
| **(2) 검증 응집** | 검증 규칙이 trivial 한가? 여러 진입점에서 같은 규칙을 강제해야 하는가? | ✅ `010-\d{4}-\d{4}` 강제. Controller / Facade / 향후 배치·내부 호출 모든 진입점에서 일관 적용 필요 |
| **(3) 행동(behavior) 부여 여지** | 메서드를 붙일 가능성이 있는가? | ✅ 차주 "내 정보 조회" 에서 `phoneNumber.mask()` (가운데 자리 마스킹) 추가 예정. VO 없으면 `PhoneNumberUtils.mask(String)` 같은 정적 헬퍼로 산재 |
| **(4) 도메인 계층 통과 길이** | 이 값이 layer 를 깊게 통과하는가? | ✅ Controller → Facade → User Entity → DB → Response 까지 전 계층 통과 |

### 4/4 ✅ → VO 가 비용 이상의 가치
- PhoneNumber 는 4축 모두 충족 → **객관적 정당화 가능, 낭비 아님**
- Round 1 의 다른 필드(LoginId / Password / Name / BirthDate / Email) 도 4축 모두 ✅

### 진짜 "취향차이" 영역
- (1) 타입 안전성 — 어떤 도메인이든 거의 항상 ✅. 약한 경우는 사실상 없음
- (2) 검증 응집 — 규칙이 `non-blank` 수준이면 VO 의 이득 ↓
- (3) 행동 부여 여지 — 영원히 read-only 라면 VO 의 이득 ↓
- (4) 계층 통과 — 컨트롤러 내부에서만 쓰이고 사라지는 일회용 키워드라면 VO 과함

(2)(3)(4) 가 동시에 약하면 **취향차이로 수렴**. 자유 입력 텍스트(예: 검색 키워드, 자유 메모) 같은 게 대표 예.

### 면접 답변 템플릿
> "단순 String 보다 VO 가 무거운 건 사실입니다. 다만 (1) 같은 타입의 다른 필드와 인자 swap 위험, (2) 검증 응집, (3) 향후 메서드 부여 여지, (4) 계층 통과 깊이 — 이 4축 중 절반 이상이 ✅ 면 VO 가 비용 이상의 가치를 줍니다. PhoneNumber 는 4/4 라 채택했고, 일회용 자유 텍스트라면 plain String 으로 갔을 겁니다."

### 출처
- 결정 기준: 본 프로젝트 [`12-user-field-policy.md`](../../.claude/rules/12-user-field-policy.md), [`06-validation-via-domain-vo.md`](../../.claude/rules/06-validation-via-domain-vo.md)
- 일반 참고: Eric Evans DDD — Value Objects 챕터, Vaughn Vernon IDDD

---

## Q4. (2026-05-21) 동형(同形) VO 본체를 abstract 로 추출하지 않는 이유

**제안자**: Claude (Cycle 3 Refactor 에서 "공통점이 보이는데 왜 안 묶나" 가 자연스러운 질문이라 선제 기록)

### 맥락
`LoginId`, `Name`, `PhoneNumber` 본체가 동일한 패턴.

```kotlin
data class X(val value: String) {
    init { if (!PATTERN.matches(value)) throw CoreException(BAD_REQUEST, "...") }
    companion object { private val PATTERN = Regex("...") }
}
```

자연스러운 일반화는 `abstract class StringVo(val value: String)` 같은 추출. **하지 않는다.** 4가지 이유:

### 1. Kotlin 초기화 순서 함정 — **결정적 이유**
Kotlin 에서 부모 클래스 `init` 블록은 **자식 프로퍼티가 초기화되기 전에** 실행된다.

```kotlin
abstract class StringVo(val value: String) {
    init {
        if (!pattern.matches(value)) throw CoreException(...) // ❌ pattern 미초기화 시점
    }
    protected abstract val pattern: Regex
}

class LoginId(value: String) : StringVo(value) {
    override val pattern = Regex("^[A-Za-z0-9]{4,20}$") // 부모 init 이후 초기화
}
```

`StringVo` 의 init 이 `pattern` 참조 시점에 `pattern` 은 아직 미초기화 → NPE 또는 의도치 않은 동작. 우회하려면 abstract method 로 바꿔야 하는데, 그 자체가 단순 정규식 검증보다 복잡해짐.

### 2. `data class` 능력 상실
`data class` 는 상속에 적합하지 않다 (final + 자동 생성 메서드들이 단일 클래스 가정). VO 의 값 평등성을 얻으려면 `equals/hashCode` 직접 작성 — 보일러 증가, 안전성 ↓.

### 3. 이질성을 추상화로 누름
- `Password` 는 `birthDate` 교차 검증 필요 → 단일 정규식에 안 맞음
- `BirthDate` 는 `today: LocalDate` 외부 주입 필요 → 다른 시그니처
- 7개 VO 중 단순 정규식인 건 3개뿐. 추상화의 ROI 낮음

### 4. 의도 흐림
`data class LoginId(val value: String)` 한 줄로 도메인 의도가 끝남.
`class LoginId(value: String) : StringVo(value)` 는 구조적 정보(상속) 가 의도를 가린다.

### 결론 — 추출의 비대칭성
- ✅ **테스트 helper** (`assertBadRequest`) 는 추출 — 단언 패턴은 어차피 동일, 의도 단일
- ❌ **VO 본체** 는 비추출 — 위 4가지 이유

**"중복 = 추출" 이 항상 옳지 않다** 의 좋은 사례. Rule of Three 는 추출 가능성의 *신호*일 뿐 강제는 아님.

### 면접 답변 템플릿
> "Refactor 단계에서 LoginId / Name / PhoneNumber 가 동형이라 abstract class 로 묶을지 검토했지만, ① Kotlin 의 init 초기화 순서 함정(부모 init 이 자식 override val 보다 먼저 실행), ② data class 능력 상실, ③ Password·BirthDate 같은 다른 VO 의 이질성, ④ 의도의 명확도 — 4가지 이유로 비추출 결정했습니다. 대신 테스트 측 단언 helper 만 추출했습니다."

### 출처
- Kotlin Spec — [Initialization order](https://kotlinlang.org/spec/declarations.html#initialization-order)
- Effective Kotlin Item 36: "Prefer composition over inheritance"

---

## Q5. (2026-05-21) `LocalDate.parse("1995-02-30")` 는 왜 거부되는가? — `ResolverStyle.STRICT`

**제안자**: Claude (Cycle 5 Green 에서 BD-07 `"1995-02-30"` 케이스가 별도 검증 코드 없이 통과한 배경 설명)

### 맥락
`BirthDate` 가 `LocalDate.parse(rawValue, DateTimeFormatter.ISO_LOCAL_DATE)` 한 줄로 `1995-02-30` 같은 **실제 존재하지 않는 날짜**도 자동 거부한다. 왜?

### 답 — `ISO_LOCAL_DATE` 는 기본 `ResolverStyle.STRICT`

JDK 의 `DateTimeFormatter.ISO_LOCAL_DATE` 정의:

```java
ISO_LOCAL_DATE = new DateTimeFormatterBuilder()
    .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
    .appendLiteral('-')
    .appendValue(MONTH_OF_YEAR, 2)
    .appendLiteral('-')
    .appendValue(DAY_OF_MONTH, 2)
    .toFormatter(ResolverStyle.STRICT, IsoChronology.INSTANCE);
//                ^^^^^^^^^^^^^^^^^^^^^^^^ 핵심
```

`ResolverStyle.STRICT` 는 **field 값이 chronology(달력 체계) 에서 유효한지 엄격 검증**한다. `MONTH=2, DAY=30` 은 ISO 달력에서 불가능 → `DateTimeParseException`.

### 3가지 ResolverStyle

| 스타일 | `1995-02-30` 처리 |
|---|---|
| `STRICT` | throw `DateTimeParseException` ✓ 우리가 원하는 동작 |
| `SMART` | 부분 보정: `1995-02-28` (그 달 마지막 날로 조정) |
| `LENIENT` | 광범위 보정: `1995-03-02` (오버플로우 허용) |

`LocalDate.parse(string)` (formatter 미명시) 도 내부적으로 ISO_LOCAL_DATE 사용 → **항상 STRICT**.

### 우리 코드에서
```kotlin
LocalDate.parse(rawValue, DateTimeFormatter.ISO_LOCAL_DATE)
```
한 줄이 BD-05\~08 4가지 케이스를 동시에 거부한다:
- BD-05 `1995/03/15` — pattern mismatch (`-` 요구)
- BD-06 `1995-13-01` — month out of 1..12
- BD-07 `1995-02-30` — day out of valid range for month (**여기서 STRICT 가 결정적**)
- BD-08 `19950315` — pattern mismatch

별도 `ResolverStyle` 명시 불필요. 만약 SMART 가 기본이었다면 BD-07 은 silently `1995-02-28` 로 변환되어 **통과**했을 것 — 사일런트 변형은 보안·UX 모두에 위험.

### 비교: 다른 언어/라이브러리의 기본 동작
- `SimpleDateFormat` (legacy Java) — `setLenient(true)` 기본 → `1995-02-30` 을 `1995-03-02` 로 변환. 함정
- JodaTime — `LENIENT` 기본 (강제 STRICT 설정 필요)
- JavaScript `Date` — overflow 보정 (`new Date(1995, 1, 30)` → `1995-03-02`)
- Python `datetime.strptime` — STRICT 기본 (raise ValueError)

### 면접 답변 템플릿
> "Java 8+ 의 `DateTimeFormatter.ISO_LOCAL_DATE` 는 기본 `ResolverStyle.STRICT` 라 `1995-02-30` 같은 불가능 날짜를 자동 거부합니다. 도메인 검증에서 별도 분기 없이 사용 가능. 반면 legacy `SimpleDateFormat` 은 lenient 기본이라 의도치 않은 보정이 발생할 수 있어, 도메인 코드에 쓰면 안 됩니다."

### 출처
- JDK Doc — [DateTimeFormatter.ISO_LOCAL_DATE](https://docs.oracle.com/javase/21/docs/api/java/time/format/DateTimeFormatter.html#ISO_LOCAL_DATE)
- JDK Doc — [ResolverStyle](https://docs.oracle.com/javase/21/docs/api/java/time/format/ResolverStyle.html)
- BirthDate 카탈로그: `docs/round-1/02-tdd-plan.md` B.5

---

## Q6. (2026-05-21) `RawPassword` vs `Password` — 두 VO 의 본질적 차이는?

**질의자**: 사용자

### 핵심 — 같은 "비밀번호" 단어 안에 **라이프사이클이 다른 두 개념**

| 측면 | RawPassword | Password |
|---|---|---|
| **보유 값** | 평문 (`"P@ssw0rd"`) | BCrypt 해시 (`"$2a$10$N9qo..."`) |
| **생명주기** | 휘발성 — 요청 처리 시 1회용 | 영속성 — User 평생, DB column |
| **검증 책임** | 형식 (길이 + 허용 charset) | (생성 시) 생년월일 substring 검사 + 해시화 / (사용 시) 평문 매칭 |
| **DB 저장** | ❌ 절대 안 됨 | ✅ 해시값 그대로 |
| **로그 노출** | 마스킹 필수 (`toString=****`) | 해시는 그대로 가능 (일방향이라 노출돼도 평문 복원 불가) |
| **응답 반환** | ❌ | ❌ (응답엔 둘 다 안 담음 — rule 13) |
| **함수 시그니처에서의 역할** | "사용자가 방금 입력한 비밀번호" | "DB 에 저장된 비밀번호 자격 증명" |
| **존재 이유** | 평문을 도메인 타입으로 받아 **검증 진입점 강제** | 해시 + 평문 비교 가능한 **일방향 자격 증명** |

### 흐름으로 보기

```
[가입]
사용자 입력 String
  → RawPassword(string)                       // 형식 검증 통과
  → Password.encrypt(raw, birthDate)           // 교차 검증 + BCrypt
  → User.password = Password(hashedValue)
  → DB INSERT (hashedValue 만)
                                               ← raw 인스턴스는 여기서 폐기 (참조 끊김 → GC)

[로그인 / 비밀번호 확인]
사용자 입력 String
  → RawPassword(string)
  → user.password.matches(raw)                 // BCrypt 비교
  → true/false
```

### 왜 한 클래스로 합치지 않나? — 검토했던 대안들

**대안 A**: `class Password(val value: String, val isHashed: Boolean)`
- 문제: 함수 시그니처에서 의도 모호. `acceptPassword(p)` — 평문 받아? 해시 받아? 매번 `isHashed` 분기 필요 — **state machine 누수**
- 호출자가 잘못된 mode 를 패스하면 silently 잘못된 동작

**대안 B**: `class Password(val hashedValue: String)` 하나로 통합, encrypt 가 String 평문을 직접 받음
```kotlin
class Password(val hashedValue: String) {
    companion object {
        fun encrypt(rawValue: String, birthDate: BirthDate): Password { ... }
    }
}
```
- 문제: **평문이 String 채로 떠다님** — 타입 안전성 X. 검증 안 된 임의 String 이 encrypt 호출 어디서든 들어올 수 있음
- 형식 검증(길이/charset) 을 encrypt 내부에서 또 해야 함 → encrypt 의 책임 비대화

**우리 선택 — 분리** (D-A1):
```kotlin
fun encrypt(raw: RawPassword, birthDate: BirthDate): Password   // "검증된 평문" 명시
fun matches(raw: RawPassword): Boolean                          // "평문 비교" 명시
fun ofHashed(hashedValue: String): Password                     // "영속 복원" 명시
```
호출하는 사람이 **의도를 헷갈릴 수 없는 API**. 각 VO 의 책임 단일.

### 비유
- **RawPassword** = 식재료 (생, 짧게 보관, 검증 필요)
- **Password** = 조리된 음식 (오래 보관 가능, 다른 조리법으로 변환 불가)
- 한 클래스로 묶으면 매번 "이거 생거야 익은 거야?" 분기

### 테스트 가능성 측면
- RawPassword 테스트 8건: BCrypt 호출 0 → **ms 단위**
- Password 테스트 6건: BCrypt 실호출 → **100ms+ 단위**
- 분리 안 했으면 모든 비밀번호 테스트가 BCrypt 비용을 짊어짐 (CI 시간 누적)

### 보안 측면
- RawPassword 평문은 `toString=****` 마스킹으로 로그 누출 차단
- Password 는 해시값만 보관 — 메모리에서도 평문 잔존 X
- 코드 추적에서 **"평문이 어디서 사라지는지"** 명확 (Password.encrypt 호출 직후)

### 면접 답변 템플릿
> "비밀번호는 같은 단어 안에 라이프사이클이 다른 두 개념이 섞여 있습니다. 입력 단계의 휘발성 평문(RawPassword) 과 저장 단계의 일방향 해시(Password). 한 클래스로 묶으면 함수 시그니처에서 의도가 모호해지고 검증 책임이 비대해집니다. 분리하면 (1) 함수 인자에서 의도 명시, (2) 책임 단일, (3) BCrypt 호출 없는 단위테스트 가능, (4) 코드 추적에서 평문이 어디서 사라지는지 명확. 단점은 클래스가 2개라는 점뿐, 비용보다 훨씬 작은 트레이드오프입니다."

### 출처
- 결정: [`docs/round-1/02-tdd-plan.md`](./02-tdd-plan.md) **D-A1**
- 정책: [`.claude/rules/11-password-policy.md`](../../.claude/rules/11-password-policy.md) "VO 분리" 항목

---

## Q7. (2026-05-21) 도메인 코드는 어느 모듈에 두는가 — apps vs modules

**질의자**: 사용자 ("api 안에 vo 가 있어?")

### 사용자 지적
Cycle 7 Green 검토 중 BCrypt 의존성을 `apps/stay-api/build.gradle.kts` 에 추가하는 제안을 보고 "api 안에 vo 가 있어?" 라는 더 근본적인 지적이 나옴. 도메인 객체가 apps 모듈에 사는 게 어색하다는 판단.

### 답 — `modules/` 가 맞다 (컨벤션상)

이 프로젝트의 모듈 컨벤션 (README):

| 디렉터리 | 정의 |
|---|---|
| `apps/` | 실행 가능한 **SpringBootApplication** |
| `modules/` | 특정 구현·도메인에 의존적이지 않고 **reusable 한 configuration / 자산** |
| `supports/` | logging·monitoring 같은 **add-on** |

apps/ 는 "실행 단위" 의미. 도메인 객체는 실행 단위에 종속될 이유가 없다. 다른 앱(stay-batch, stay-streamer)에서도 같은 User 도메인을 쓸 수 있어야 자연.

### 잘못 들여다본 부분
- 부트캠프 원본 템플릿이 `apps/commerce-api/.../com/loopers/domain/example/` 패턴을 채용
- 그걸 의식 없이 답습해서 6 사이클(LoginId\~RawPassword) 진행
- BCrypt 의존성 위치 결정 단계에서 사용자 지적으로 발각 → 6 사이클 시점에 리팩토링

### 두 위치의 트레이드오프

| | apps/stay-api 에 도메인 | modules/domain 에 도메인 |
|---|---|---|
| 모듈 컨벤션 부합 | ❌ apps 정의는 실행 단위 | ✅ 재사용 자산 정의에 부합 |
| 다른 앱에서 재사용 | ❌ 코드 이동 필요 | ✅ `implementation(project(":modules:domain"))` 한 줄 |
| 도메인 의존성 격리 | ❌ BCrypt·BCrypt 같은 dep 가 apps/ 에 박힘 | ✅ domain dep 가 domain 모듈에 자연 격리 |
| 도메인 단위 테스트 격리 | 같음 | 같음 (둘 다 Spring 없음 가능) |
| 모듈 개수 | +0 | +1 |
| 6 사이클 시점 변경 비용 | 0 (그대로) | 17 파일 이동 + 빌드 설정 + 문서 |

### Round 1 결정 — `modules/domain` 으로 이동

비용 (17 파일 + 2 빌드파일 + 4 docs) 가 미래 차수(Cycle 8\~10, Round 2 이상)의 모든 도메인 코드를 옳은 위치에 두는 이득보다 작음. 6 사이클 시점이 마지막 저비용 이동 타이밍.

### CoreException / ErrorType 도 함께 이동
도메인이 `throw CoreException(...)` 하므로 domain 모듈에서 접근 가능해야 함. Gradle 의존성이 `apps → modules` 단방향이라 apps 에 두면 modules 가 못 봄. 따라서 modules/domain 으로 동반 이동.

### ErrorType 의 HttpStatus 의존 제거 (부수 효과 — 의미 있는 분리)
이동 중 ErrorType 이 `org.springframework.http.HttpStatus` 의존인 게 발견. modules/domain 에 spring-web 끌어오기 vs HttpStatus 제거 두 옵션 중 후자 채택:
- `enum ErrorType(val statusCode: Int, ...)` — 도메인은 status 코드만 알고 HTTP semantic 은 모름
- `ApiControllerAdvice` 가 `HttpStatus.valueOf(errorType.statusCode)` 매핑 — web 의존성이 web 계층에만 머무름

이제 modules/domain 은 spring-web 무의존. 깔끔.

### 면접 답변 템플릿
> "초반엔 부트캠프 템플릿 패턴 그대로 apps/ 안에 도메인을 두고 진행했는데, BCrypt 의존성 위치 논의에서 '도메인이 apps 안에 사는 게 어색하지 않냐'는 지적이 나와 6 사이클 시점에 modules/domain 으로 분리했습니다. 이로써 (1) 모듈 컨벤션 충실, (2) 도메인 의존성을 도메인 모듈에 격리, (3) 추후 stay-batch 등 다른 앱에서 User 도메인 재사용 자연스러움 — 세 가지 동시 달성. 추가로 이동 중 ErrorType 의 HttpStatus 의존을 제거해 도메인이 spring-web 에 의존하지 않도록 정리했습니다."

### 출처
- 사용자 지적: 2026-05-21 (Cycle 7 Green 검토 중 BCrypt 위치 → "vo 가 apps 에 있어?")
- 관련: [Q6 (RawPassword vs Password)](#q6-2026-05-21-rawpassword-vs-password---두-vo-의-본질적-차이는)

---

## Q8. (2026-05-22) `UserFacade` 는 뭐 하는 거야?

**질의자**: 사용자 (Cycle 9 Step 2 Red 검토 중 — "Facade 가 뭐 하는 거야?")

### 한 줄
**Application 계층 컴포넌트.** 컨트롤러로부터 `SignupCommand` 를 받아 도메인 객체들(`UserRepository`, `User.signUp`) 을 순서대로 호출해 가입을 완성하고, 결과를 `UserInfo` DTO 로 반환.

### 계층 그림

```
[HTTP]
   ↓
UserV1Controller        (interfaces.api — 얇음. 입력/출력 직렬화만)
   ↓ SignupCommand
UserFacade.signUp       ← 여기. 유스케이스 오케스트레이션
   ├─ UserRepository.existsByLoginId    선검사. 중복이면 CONFLICT throw
   ├─ User.signUp(...)                   도메인 검증 + Aggregate 합성. BAD_REQUEST 가능
   ├─ UserRepository.save(user)          영속
   └─ UserInfo.from(savedUser)           응답 DTO 매핑
   ↑ UserInfo
컨트롤러가 UserV1Dto.Response 변환 → ApiResponse envelope
```

### Facade 가 책임지는 5가지

| # | 책임 | 빠질 때 누수되는 곳 |
|---|---|---|
| 1 | 입력 DTO 수용 (`SignupCommand`) | 컨트롤러가 도메인을 직접 알게 됨 |
| 2 | 사전 검사 (`existsByLoginId`) | DB 까지 가서 SQL violation — UX 나쁨 |
| 3 | 유스케이스 본 동작 (`signUp`, `save`) | 컨트롤러가 도메인+영속을 직호출 |
| 4 | 트랜잭션 경계 (`@Transactional`) | 부분 커밋 위험 |
| 5 | 결과 매핑 (`UserInfo.from`) | User 가 응답에 직접 노출 (보안·결합) |

### 인접 개념과의 경계

| 컴포넌트 | 역할 | 위치 |
|---|---|---|
| **Controller** | HTTP serialize/deserialize. 비즈니스 로직 X | `apps/stay-api/.../interfaces/api/v1/user/` |
| **Facade** (= ApplicationService) | 유스케이스 오케스트레이션 | `apps/stay-api/.../application/user/` |
| **Domain Service** | Aggregate 간 협력 등 도메인 로직 | (현재 미도입 — YAGNI) |
| **Aggregate** | 도메인 불변식·상태·행동 | `modules/domain/.../domain/user/User` |
| **Repository** | 영속 추상화 | interface: `modules/domain/...`, 구현: `apps/stay-api/.../infrastructure/...` |

### "Service" vs "Facade" 네이밍 — 컨벤션 차이

- **ApplicationService** — DDD 책 표준 용어
- **Facade** — GoF 패턴 이름 차용

본 프로젝트는 부트캠프 골격의 `Facade` 명명을 채택해 일관성 유지. 의미는 동일. **추후 네이밍/구조 재검토 여지 사용자 메모(2026-05-22)** — 변경 시 일관성 영향만 점검.

### Domain Service 를 안 만든 이유

가입 정책의 핵심(중복 검사) 은 `UserRepository` 의존이라 **application 계층이 자연 위치**. Domain Service 끼면 `UserDomainService.signUp(repository, ...)` 형태의 thin pass-through → 추가 가치 없음.

**도입 트리거**: 차후 "회원 등급 산정" 처럼 *Aggregate 2개 이상이 협력하는 도메인 로직* 등장 시.

### 면접 답변 템플릿
> "Facade 는 application 계층 컴포넌트입니다. 컨트롤러는 HTTP serialize/deserialize 만 담당, 도메인은 불변식과 행동만 가지게 하고, 그 사이에서 유스케이스 단위로 (선검사 → 도메인 호출 → 영속 → 결과 매핑) 흐름을 조정하면서 트랜잭션 경계를 잡습니다. ApplicationService 와 동일 개념, 컨벤션상 Facade 라고 부르고 있습니다."

### 출처
- 결정: `docs/round-1/02-tdd-plan.md` D-A3 (Domain Service 미도입 사유)
- 컨벤션: 부트캠프 골격 명명
- 관련: [Q6 (RawPassword vs Password)](#q6-2026-05-21-rawpassword-vs-password---두-vo-의-본질적-차이는)

---

## Q9. (2026-05-22) `BirthDate.parseAndValidate` 의 Java-스러운 if-throw 체이닝 — Kotlin best practice 모색

**질의자**: 사용자 (Stop 3 리뷰 중 — "이건 자바느낌 많이 나게 생겼네. `require` 같은 함수나 체이닝 으로 좀 더 코틀린 스럽게?")

### 맥락
`BirthDate.parseAndValidate` 는 `try-catch + if-throw + if-throw` 3단 imperative 체이닝.

```kotlin
val parsed = try {
    LocalDate.parse(rawValue, FORMATTER)
} catch (e: DateTimeParseException) {
    throw CoreException(BAD_REQUEST, "형식이 올바르지 않습니다.")
}
if (parsed.isAfter(today)) throw CoreException(BAD_REQUEST, "미래일 수 없습니다.")
if (parsed.isAfter(today.minusYears(MIN_AGE_YEARS))) throw CoreException(BAD_REQUEST, "만 14세 이상...")
return parsed
```

### 검토했던 후보들

| 후보 | 특징 | 단점 |
|---|---|---|
| **A. `runCatching` + `.also { if ... throw }`** | try-catch 한 줄 압축, 체이닝 모양 | `runCatching` 이 모든 Throwable 잡음 (정확성 손실) / `also` 안의 throw 의도 묻힘 |
| **B. 검증 단계를 확장 함수로 분리** (`requireNotFuture`, `requireAtLeastAge`) | 호출부 3줄로 의도 선명 | 헬퍼 4개 추가, BirthDate 외 재사용 가능성 한정 |
| **C. 도메인 전용 `requireOrFail` helper + 모든 VO 일괄** | 광역 일관성 | 새 프로젝트 추상화 도입 비용, 다른 VO 들도 if-throw 1줄씩이라 LOC 절약 미미 |
| **D. `kotlin.require`** | 표준 Kotlin idiom | `IllegalArgumentException` 던져서 우리 `CoreException(BAD_REQUEST)` 와 불일치. **직접 사용 불가** |

### 현재 결정 — **deferred**
현 상태 유지. try-catch + if-throw 가 명시적이라 *나쁘지 않음*. Kotlin 공식 가이드 / Effective Kotlin / 코틀린 인 액션 등의 **베스트 프랙티스 모색 후** 진짜 권장 패턴 확정 시 일괄 적용 결정.

### 재검토 트리거
- Kotlin best practice 문헌 확인 (Effective Kotlin Item 5/Item 7, 공식 coding conventions)
- 다른 VO 들에 다단 검증(try-catch + if-throw 2개 이상) 도입 시
- BirthDate 외 *3개 이상* VO 가 동형 다단 검증 → Rule of Three 트리거

### 면접 답변 템플릿
> "초안은 imperative if-throw 체인이었는데 'Java 느낌' 이라는 지적을 받았습니다. `runCatching` / 확장함수 분리 / `requireOrFail` 헬퍼 세 후보를 검토했지만, 각각 정확성 손실·LOC 늘어남·새 추상화 비용 같은 단점이 있고 BirthDate 만 다단인 상황이라 *지금 일반화하면 over-engineering* 으로 판단해 deferred. Kotlin 공식 best practice 모색 + 다른 VO 에도 동일 패턴 등장 시 재검토 트리거로 기록했습니다."

### 출처
- 사용자 지적: 2026-05-22 (BirthDate 리뷰 중)
- 본 분석: questions.md Q9 위 표

---

## Q10. (2026-05-22) `Facade` → `Service` 리네이밍 — 사용자 컨벤션 정립

**질의자**: 사용자 (Stop 6 리뷰 중 — "Facade 별로다. UserSignUpService 나 UserAuthService 가 더 좋다")

### 사용자의 컨벤션 정의

- **Facade** = Service 간 *순환 참조*를 끊기 위한 (Service 위) 계층
- **Service** = Repository 나 domain 레이어에 있는 것들을 *조합*해서 쓰는 계층

이 정의 하에서, 우리 `UserFacade` 가 하는 일(Repository + Aggregate 합성 + 트랜잭션 경계 + DTO 매핑) 은 **Service** 가 정확.

### 결정 — `UserFacade` → `UserService`
부트캠프 골격의 `Facade` 명명을 답습한 것이라, 사용자 컨벤션과 충돌. Cycle 9 완료 + Round 1 코드 리뷰 시점에 일괄 리네이밍.

### 이름 후보 비교

| 후보 | 의미 | 차주 대응 (로그인·비번변경·조회) |
|---|---|---|
| `UserSignUpService` | 가입 유스케이스만 | 차주에 `UserSignInService`, `UserPasswordService` 추가 — 클래스 폭증 |
| `UserAuthService` | 인증 묶음 (가입+로그인+비번변경) | 자연 묶음. 조회/수정은 별도 ProfileService |
| **`UserService`** ✓ | 회원 전체 | 메서드 추가로 확장. 폭증 시 자연 분리 |

**`UserService` 선택**. Round 1 시점 회원 메서드가 적어 단일 클래스로 시작. *변경 이유가 달라지는 메서드 집합*이 발견되면 책임별 분리 트리거.

### 일괄 영향
- 3 파일 rename: `UserFacade*.kt` → `UserService*.kt`
- 클래스·필드명 변경: `UserFacade` → `UserService`, `userFacade` → `userService`
- 문서·rule 일관 갱신: docs/round-1/01·02 + `.claude/rules/` 06·08·10·14
- questions.md Q1\~Q8 의 "Facade" 언급은 **역사 보존** 차원에서 그대로 유지 — Q10 이 결정 점프 지점

### 향후 분리 트리거
- 메서드 6\~8개 이상 → 책임별 분리 검토 (예: `UserAuthService` + `UserProfileService`)
- *변경 이유*가 갈리는 메서드 집합 발견 → SRP 기반 분리
- application layer 가 도메인 객체보다 비대해지면 reorganization

### 면접 답변 템플릿
> "초기엔 부트캠프 골격의 Facade 명명을 그대로 썼지만, 'Facade 는 Service 간 순환 의존 끊기, Service 는 Repository·도메인 조합' 으로 컨벤션을 명확히 한 시점에 `UserService` 로 변경했습니다. Round 1 시점은 회원가입 단일 메서드라 단일 `UserService` 로 두고, 차주 이상 메서드가 늘어 변경 이유가 갈리면 책임별 분리를 트리거로 잡았습니다."

### 출처
- 사용자 컨벤션 정의: 2026-05-22 (Stop 6 리뷰 중)
- 관련: [Q8 (UserFacade 는 뭐 하는 거야?)](#q8-2026-05-22-userfacade-는-뭐-하는-거야) — 이름 결정 직전의 역할 토론
- 일관성: rule 06/08/10/14 의 "Facade" 표현도 동시 갱신

---

## Q11. (2026-05-22) "보존 골격 패턴" 이 뭐고, DTO 의 nested Request/Response 이유는?

**질의자**: 사용자 (Stop 7 리뷰 중 — UserV1ApiSpec.kt 와 UserV1Dto.kt 보면서)

### Q11-a — "보존 골격" 은 뭐고 Kotlin 특유인가?

**프로젝트 맥락의 어휘.** Kotlin 특유 패턴이 아님.
- "보존 골격" = *원본 부트캠프 템플릿(loop-pack) 에서 *보존하기로 결정한* 코드 패턴*
- UserV1ApiSpec.kt 의 경우엔 — 부트캠프 원본의 `ExampleV1ApiSpec` interface + `ExampleV1Controller` implements 구조를 답습한 것
- 패턴 자체의 보편적 이름: **API Specification Interface 분리** 또는 **OpenAPI Annotation Separation**. Java 에서도 동일하게 동작 (interface + class implements 표준 OOP)

#### 패턴의 트레이드오프

| 측면 | 장점 | 단점 |
|---|---|---|
| 관심사 분리 | OpenAPI 어노테이션 ↔ 라우팅·로직 분리 | 파일 2개로 분산 |
| 문서/구현 동기화 | 시그니처가 강제 일치 (override) | spec 변경 시 두 곳 갱신 |
| 계약 우선 개발 | spec 먼저 → controller implements | 작은 API 면 over-engineering |
| 테스트 | spec interface 직접 mock 가능 | 보통 controller 는 E2E 위주, mock 안 함 |

#### 코멘트 표현 개선 (deferred)
"보존 골격 패턴" 표현은 모호 — 더 명확하게 "OpenAPI 어노테이션 분리 (부트캠프 원본 ExampleV1ApiSpec 의 패턴 유지)" 같은 표현이 좋음. 본 시점에서 코멘트 수정은 deferred. Round 2 진입 시 정리.

---

### Q11-b — DTO 의 nested `SignupRequest`/`SignupResponse` 이유?

`class UserV1Dto private constructor()` 안에 Request·Response 를 중첩 두는 패턴.

#### 4가지 이유

| # | 이유 | 효과 |
|---|---|---|
| 1 | **네임스페이싱** | 호출부에 `UserV1Dto.SignupRequest` — "어느 API 의 어느 메서드용 DTO" 한눈에 |
| 2 | **파일 그룹화** | Request·Response 한 파일 응집. 도메인 묶음 navigation 쉬움 |
| 3 | **이름 충돌 회피** | `UserV1Dto.LoginRequest` vs `OrderV1Dto.LoginRequest` 같은 명시적 격리 |
| 4 | **부트캠프 골격 답습** | `ExampleV1Dto` 와 일관성 유지 |

#### 대안 패턴 비교

| 대안 | 코드 | 장점 | 단점 |
|---|---|---|---|
| (A) **Top-level 파일 분리** | `SignupRequest.kt` 별도 파일 | import 간결 (`SignupRequest`) | 파일 수 폭증. 도메인 묶음이 디렉터리에 의존 |
| (B) **`object UserV1Dto`** | `object UserV1Dto { data class ... }` | **Kotlin idiom**. "싱글톤 네임스페이스" 의도가 코드에 정확히 일치 | 부트캠프 골격(`class`) 과 불일치 |
| (C) **`class private constructor()` + nested** (현재) | `class UserV1Dto private constructor() { ... }` | 부트캠프 일관성. 네임스페이싱 | container class 가 빈 껍데기 (약간 code smell) |

#### Round 1 결정 — (C) 유지
부트캠프 골격 일관성 비중. 다만 **(B) `object` 가 더 Kotlin스러움** 인지. 차주 이상 V1Dto 가 늘어날 때(LoginV1Dto, PasswordChangeV1Dto 등) 같이 `object` 로 리팩토링 검토.

#### 차주 DTO 추가 시 두 갈래
- 현재 패턴 유지: `UserV1Dto` 안에 `LoginRequest`, `PasswordChangeRequest` 등 nested 추가
- 도메인 책임별 분리: `UserAuthV1Dto`, `UserProfileV1Dto` 등 작업 묶음별

### 면접 답변 템플릿

> "'보존 골격 패턴' 이라는 표현은 부트캠프 원본 템플릿의 패턴을 그대로 유지한 부분을 가리키는 프로젝트 내부 어휘일 뿐, 정식 명칭은 OpenAPI Annotation Separation — interface 에 OpenAPI 어노테이션을, controller 에 라우팅·로직을 두는 일반적인 관심사 분리입니다. Kotlin·Java 동일하게 동작합니다. DTO 의 nested 구조는 네임스페이싱과 도메인 묶음 응집 목적이고, Kotlin 적으로는 `object` 가 더 idiom 이지만 부트캠프 골격 일관성을 위해 `class private constructor()` 를 유지했습니다."

### 출처
- 사용자 질의: 2026-05-22 (Stop 7 리뷰 중)
- 코드: `UserV1ApiSpec.kt`, `UserV1Dto.kt`

---

> 새 질문은 아래에 `## Q12. (날짜) ...` 형식으로 추가. **질의자/제안자** 표기 잊지 말 것.
