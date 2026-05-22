# Round 1 — 회원가입 TDD 작업 계획 (v1)

> **목적**
> (A) 무엇을 만들지 = 구현 대상 컴포넌트 인벤토리
> (B) 무엇을 검증할지 = 컴포넌트별 "입력 → 예상 답변" 테스트 케이스 카탈로그
>
> **선행 문서:** [`01-signup-requirements.md`](./01-signup-requirements.md) (요구사항 확정본)
> **후속 문서(예정):** `03-tdd-cycles.md` — 본 카탈로그를 Red→Green→Refactor 사이클 순서로 변환

---

## A. 구현 대상 — 컴포넌트 인벤토리

`com.stay.*` 하위에 신설할 클래스 일람. 계층별 책임은 보존된 골격(`ApiResponse`, `ApiControllerAdvice`, `CoreException`, `ErrorType`, `BaseEntity`)을 그대로 활용한다.

> **위치 메모 (2026-05-21 리팩토링 반영)**: 6 사이클 후 도메인 코드는 `apps/stay-api` 에서 **`modules/domain`** 으로 이동했다. 아래 표의 Domain (VO/Aggregate/Port) 항목은 `modules/domain/src/main/kotlin/com/stay/domain/user/...`, 도메인 예외 `CoreException`/`ErrorType` 은 `modules/domain/src/main/kotlin/com/stay/support/error/...` 에 위치한다. Application/Infrastructure/Interfaces 계층은 `apps/stay-api` 유지. 사유 [Q7](./03-questions.md).

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (VO) | `LoginId` | 로그인 ID 포맷·길이 검증 | Unit |
| Domain (VO) | `RawPassword` | 평문 비밀번호 — 길이/charset 검증 | Unit |
| Domain (VO) | `Password` | 해시값 보유. `encrypt(raw, birthDate)`·`matches(raw)` | Unit |
| Domain (VO) | `Name` | 한글 1~10자 검증 | Unit |
| Domain (VO) | `BirthDate` | `yyyy-MM-dd` 파싱·미래/만 14세 검증 | Unit |
| Domain (VO) | `Email` | 이메일 포맷 검증 | Unit |
| Domain (VO) | `PhoneNumber` | `010-XXXX-XXXX` 검증 | Unit |
| Domain (Aggregate) | `User` | 모든 VO 합성·아이덴티티 보유. `signUp(...)` 팩토리 | Unit (합성), Integration (영속) |
| Domain (Port) | `UserRepository` | 도메인 인터페이스: `existsByLoginId`, `findByLoginId`, `save` | (인터페이스 자체는 테스트 X) |
| Application | `SignupCommand` | 가입 입력 DTO | (값 보유 — 테스트 X) |
| Application | `UserInfo` | 가입 결과 DTO + `from(User)` | Unit (factory 매핑) |
| Application | `UserService` | 가입 유스케이스: 중복 검사 → User.signUp → 저장 | Integration |
| Infrastructure | `UserJpaRepository` | Spring Data JPA | (Spring 위임) |
| Infrastructure | `UserRepositoryImpl` | 도메인 ↔ JPA 어댑터 | Integration (Service 경유) |
| Interfaces (API) | `UserV1Dto` | Request/Response DTO + 매핑 | (DTO — 컨트롤러 테스트에서 간접 검증) |
| Interfaces (API) | `UserV1ApiSpec` | OpenAPI 스펙 인터페이스 | — |
| Interfaces (API) | `UserV1Controller` | `POST /api/v1/users` | E2E |

### 의존 흐름 (가입 1건)

```
HTTP POST /api/v1/users
  → UserV1Controller (DTO → SignupCommand)
    → UserService.signUp(command)
      → UserRepository.existsByLoginId(loginId)        // 중복 검사
      → User.signUp(loginId, raw, name, birthDate, email, phone, today)
          → LoginId(...) / Name(...) / BirthDate(..., today) / Email(...) / PhoneNumber(...)
          → Password.encrypt(RawPassword(...), birthDate)   // 생년월일 substring 검사 + BCrypt
      → UserRepository.save(user)
    ← UserInfo.from(user)
  ← UserV1Dto.Response.from(info) → ApiResponse.success(...)
```

### 설계 결정 메모 (요구사항 확정본 + 본 문서에서 구체화)

- **D-A1 `RawPassword` ↔ `Password` 분리**: `RawPassword`는 길이/charset만, `Password`는 birthDate 교차 검증 + BCrypt 해시. BCrypt 호출이 없는 영역(길이/charset)을 빠른 단위 테스트로 분리.
- **D-A2 `BirthDate` 검증의 `today` 외부 주입**: `BirthDate(value, today: LocalDate)` 시그니처로 의존성 명시. Service에서 `LocalDate.now(clock)` 전달. 14세 경계 테스트가 결정적이 됨.
- **D-A3 도메인 서비스 없음**: 가입 정책(중복 검사)은 Repository 의존이라 **애플리케이션 계층(Service)** 에 위치. 도메인 서비스를 굳이 도입하지 않음(과한 추상화 회피).
- **D-A4 `User.signUp` 정적 팩토리**: 생성자 노출 대신 의도가 드러나는 팩토리 메서드. JPA용 protected no-arg 생성자는 별도 유지.

---

## B. 검증 카탈로그 — 컴포넌트별 "입력 → 예상 답변"

각 표의 형식은 **(ID · Given · When · Then)**. ID는 향후 테스트 클래스에서 `@DisplayName` 접두로 사용.

### B.1 `LoginId` (Unit)

| ID | Given (입력) | When | Then (예상) |
|---|---|---|---|
| LID-01 | `"alen2026"` | `LoginId(input)` | 정상 생성, `.value == input` |
| LID-02 | `"ABC1"` (4자 경계 하한) | `LoginId(input)` | 정상 생성 |
| LID-03 | `"a".repeat(20)` (20자 경계 상한) | `LoginId(input)` | 정상 생성 |
| LID-04 | `"abc"` (3자) | `LoginId(input)` | `CoreException(BAD_REQUEST)` |
| LID-05 | `"a".repeat(21)` (21자) | `LoginId(input)` | `CoreException(BAD_REQUEST)` |
| LID-06 | `""` (빈 문자열) | `LoginId(input)` | `CoreException(BAD_REQUEST)` |
| LID-07 | `"abc-1234"` (하이픈) | `LoginId(input)` | `CoreException(BAD_REQUEST)` |
| LID-08 | `"abc 1234"` (공백) | `LoginId(input)` | `CoreException(BAD_REQUEST)` |
| LID-09 | `"abc한글1"` (한글) | `LoginId(input)` | `CoreException(BAD_REQUEST)` |
| LID-10 | `"abc@123"` (특수문자) | `LoginId(input)` | `CoreException(BAD_REQUEST)` |

### B.2 `RawPassword` (Unit)

길이/charset 만 검증. 생년월일 의존성 없음.

| ID | Given | When | Then |
|---|---|---|---|
| RPW-01 | `"P@ssw0rd"` (8자 경계 하한, 허용 charset) | `RawPassword(input)` | 정상 생성 |
| RPW-02 | `"P@ssw0rd!23456ab"` (16자 경계 상한) | `RawPassword(input)` | 정상 생성 |
| RPW-03 | `"P@ssw0r"` (7자) | `RawPassword(input)` | `CoreException(BAD_REQUEST)` |
| RPW-04 | 17자 임의 | `RawPassword(input)` | `CoreException(BAD_REQUEST)` |
| RPW-05 | `"비밀번호12!@"` (한글) | `RawPassword(input)` | `CoreException(BAD_REQUEST)` |
| RPW-06 | `"P@ss w0rd"` (공백) | `RawPassword(input)` | `CoreException(BAD_REQUEST)` |
| RPW-07 | `"P\`ssw0rd"` (백틱, 비허용 특수) | `RawPassword(input)` | `CoreException(BAD_REQUEST)` |
| RPW-08 | `"abcdefgh"` (영문 소문자만 — 조합 강제 없으므로 OK) | `RawPassword(input)` | 정상 생성 |

### B.3 `Password` (Unit, BCrypt 실제 호출)

| ID | Given | When | Then |
|---|---|---|---|
| PW-01 | raw=`"P@ssw0rd"`, birthDate=`1995-03-15` | `Password.encrypt(raw, birthDate)` | 정상 생성. `.matches(raw) == true` |
| PW-02 | raw=`"P@ssw0rd"`, birthDate=`1995-03-15` | encrypt 후 `.matches(다른 raw)` | `false` |
| PW-03 | raw=`"19950315xyz!"` (YYYYMMDD substring 포함), birthDate=`1995-03-15` | `Password.encrypt(raw, birthDate)` | `CoreException(BAD_REQUEST)` |
| PW-04 | raw=`"a19950315b!!"` (substring 중간) | `Password.encrypt(raw, birthDate)` | `CoreException(BAD_REQUEST)` |
| PW-05 | raw=`"950315abc!"` (YYMMDD — 검사 대상 아님) | `Password.encrypt(raw, birthDate=1995-03-15)` | 정상 생성 (스펙: YYYYMMDD만 차단) |
| PW-06 | 같은 raw·birthDate로 두 번 encrypt | salt 다름 검증 | hashedValue 두 결과가 **다름** (BCrypt salt 무작위) |

> 비고: `Password` 생성 경로는 `Password.encrypt(raw, birthDate)` 단일. 영속에서 복원할 때는 별도 팩토리 `Password.ofHashed(hashedValue)` (검증 우회) — 비밀번호 변경 작업 시 활용 예정이라 본 주차에 둘 다 노출하되, ofHashed는 직접 테스트 1건만(JPA 매핑용).

### B.4 `Name` (Unit)

| ID | Given | When | Then |
|---|---|---|---|
| NAM-01 | `"공명선"` | `Name(input)` | 정상 |
| NAM-02 | `"가"` (1자 경계 하한) | `Name(input)` | 정상 |
| NAM-03 | `"가".repeat(10)` (10자 경계 상한) | `Name(input)` | 정상 |
| NAM-04 | `""` | `Name(input)` | `CoreException(BAD_REQUEST)` |
| NAM-05 | `"가".repeat(11)` (11자) | `Name(input)` | `CoreException(BAD_REQUEST)` |
| NAM-06 | `"John"` (영문) | `Name(input)` | `CoreException(BAD_REQUEST)` |
| NAM-07 | `"공 명선"` (공백) | `Name(input)` | `CoreException(BAD_REQUEST)` |
| NAM-08 | `"공명선1"` (숫자) | `Name(input)` | `CoreException(BAD_REQUEST)` |

### B.5 `BirthDate` (Unit, `today` 외부 주입)

`today = 2026-05-21` 가정으로 기술. 만 14세 도달 경계는 `2012-05-21` (= 오늘로부터 정확히 14년 전).

| ID | Given (value, today) | When | Then |
|---|---|---|---|
| BD-01 | `("1995-03-15", 2026-05-21)` | `BirthDate(value, today)` | 정상 |
| BD-02 | `("2012-05-21", 2026-05-21)` (정확히 만 14세) | `BirthDate(value, today)` | 정상 (만 14세 도달일 허용) |
| BD-03 | `("2012-05-22", 2026-05-21)` (만 14세 -1일) | `BirthDate(value, today)` | `CoreException(BAD_REQUEST)` |
| BD-04 | `("2026-05-22", 2026-05-21)` (미래) | `BirthDate(value, today)` | `CoreException(BAD_REQUEST)` |
| BD-05 | `("1995/03/15", today)` (포맷 위반) | `BirthDate(value, today)` | `CoreException(BAD_REQUEST)` |
| BD-06 | `("1995-13-01", today)` (월 범위 초과) | `BirthDate(value, today)` | `CoreException(BAD_REQUEST)` |
| BD-07 | `("1995-02-30", today)` (실제 없는 날짜) | `BirthDate(value, today)` | `CoreException(BAD_REQUEST)` |
| BD-08 | `("19950315", today)` (하이픈 없음) | `BirthDate(value, today)` | `CoreException(BAD_REQUEST)` |

### B.6 `Email` (Unit)

| ID | Given | When | Then |
|---|---|---|---|
| EM-01 | `"alen@example.com"` | `Email(input)` | 정상 |
| EM-02 | `"alen+tag@sub.example.co.kr"` | `Email(input)` | 정상 |
| EM-03 | `"alen"` (도메인 없음) | `Email(input)` | `CoreException(BAD_REQUEST)` |
| EM-04 | `"alen@"` (도메인 빈값) | `Email(input)` | `CoreException(BAD_REQUEST)` |
| EM-05 | `"@example.com"` (로컬 빈값) | `Email(input)` | `CoreException(BAD_REQUEST)` |
| EM-06 | `"alen@example"` (TLD 없음 — 정책 결정) | `Email(input)` | `CoreException(BAD_REQUEST)` |
| EM-07 | `""` | `Email(input)` | `CoreException(BAD_REQUEST)` |

### B.7 `PhoneNumber` (Unit)

| ID | Given | When | Then |
|---|---|---|---|
| PH-01 | `"010-1234-5678"` | `PhoneNumber(input)` | 정상 |
| PH-02 | `"011-1234-5678"` (010 아님) | `PhoneNumber(input)` | `CoreException(BAD_REQUEST)` |
| PH-03 | `"010-123-4567"` (자릿수 부족) | `PhoneNumber(input)` | `CoreException(BAD_REQUEST)` |
| PH-04 | `"010-12345-678"` (분할 다름) | `PhoneNumber(input)` | `CoreException(BAD_REQUEST)` |
| PH-05 | `"01012345678"` (하이픈 없음) | `PhoneNumber(input)` | `CoreException(BAD_REQUEST)` |
| PH-06 | `"010-1234-567a"` (비숫자) | `PhoneNumber(input)` | `CoreException(BAD_REQUEST)` |
| PH-07 | `""` | `PhoneNumber(input)` | `CoreException(BAD_REQUEST)` |

### B.8 `User.signUp(...)` (Unit, 합성)

`User.signUp(loginId, rawPwd, name, birthDateStr, email, phoneNumber, today)` 정적 팩토리.

| ID | Given | When | Then |
|---|---|---|---|
| USR-01 | 전 필드 정상 | `User.signUp(...)` | 정상 인스턴스 (id == null, 각 VO 보유, password.matches(raw)) |
| USR-02 | loginId만 불량 | `User.signUp(...)` | `CoreException(BAD_REQUEST)` — LoginId에서 throw |
| USR-03 | password가 birthDate substring 포함 | `User.signUp(...)` | `CoreException(BAD_REQUEST)` — Password.encrypt에서 throw |
| USR-04 | birthDate 만 14세 미만 | `User.signUp(...)` | `CoreException(BAD_REQUEST)` — BirthDate에서 throw |

> 비고: 각 VO 위반 케이스는 해당 VO 단위 테스트에서 망라되므로 User 합성 테스트에서는 "원인별 throw 위치를 확인"하는 정도의 sanity check만 둔다(중복 회피).

### B.9 `UserInfo.from(User)` (Unit)

| ID | Given | When | Then |
|---|---|---|---|
| INF-01 | persist된 User (id=1) | `UserInfo.from(user)` | 모든 필드 매핑. **password 미포함** |

### B.10 `UserService.signUp(SignupCommand)` (Integration, `@SpringBootTest` + Testcontainers MySQL)

| ID | Given | When | Then |
|---|---|---|---|
| FAC-01 | 비어 있는 user 테이블, 정상 command | `facade.signUp(cmd)` | `UserInfo` 반환 (id 부여됨), DB에 1건. password 컬럼은 BCrypt 해시(평문 ≠ DB값) |
| FAC-02 | 동일 loginId 사용자 사전 저장, 같은 loginId로 가입 | `facade.signUp(cmd)` | `CoreException(CONFLICT)`. DB 건수 변동 없음 |
| FAC-03 | 동시성: 동일 loginId 2건 병렬 호출 (선택, 가능하면) | 병렬 `facade.signUp` | 정확히 1건 성공, 1건 `CoreException(CONFLICT)` (DB unique 안전망 동작 확인) |
| FAC-04 | password가 birthDate 포함 | `facade.signUp(cmd)` | `CoreException(BAD_REQUEST)`. DB 저장 없음 (롤백) |

> ⚠️ FAC-03은 Testcontainers Docker 비호환([[testcontainers-docker-desktop-incompat]])으로 환경에 따라 실행 불가. 환경 정상화 시 활성화.

### B.11 `UserV1Controller` — `POST /api/v1/users` (E2E, `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`)

| ID | Given | When | Then |
|---|---|---|---|
| API-01 | 정상 body | `POST /api/v1/users` | `201 Created`, body `meta.result=SUCCESS`, `data.userId` 존재, `data.password` 미포함 |
| API-02 | 동일 loginId 사용자 사전 가입 + 같은 loginId로 재시도 | `POST` | `409 Conflict`, `meta.errorCode=CONFLICT` |
| API-03 (parameterized) | 필드별 위반 케이스 (loginId·password·name·birthDate·email·phone 각 1건씩) | `POST` | `400 Bad Request`, `meta.errorCode=BAD_REQUEST` |
| API-04 | 필수 필드 누락 (예: phoneNumber null) | `POST` | `400 Bad Request` (`ApiControllerAdvice`의 `HttpMessageNotReadableException`/필드 누락 처리 경로) |
| API-05 | Content-Type 누락/잘못된 JSON | `POST` | `400 Bad Request` (보존된 advice가 처리) |

---

## C. 다음 단계 — 사이클 변환 (`03-tdd-cycles.md` 예고)

본 카탈로그를 의존 방향대로 Red→Green→Refactor 사이클로 묶어 진행 순서를 만든다. 잠정 순서:

```
[Cycle 1]  LoginId VO         (B.1)
[Cycle 2]  Name VO            (B.4)
[Cycle 3]  PhoneNumber VO     (B.7)
[Cycle 4]  Email VO           (B.6)
[Cycle 5]  BirthDate VO       (B.5, today 외부 주입)
[Cycle 6]  RawPassword VO     (B.2)
[Cycle 7]  Password VO        (B.3, BCrypt 실호출)
[Cycle 8]  User aggregate     (B.8, B.9 포함)
[Cycle 9]  UserRepository(Impl) + UserService  (B.10, integration)
[Cycle 10] UserV1Controller   (B.11, E2E)
```

각 사이클은:
1. **Red**: 위 카탈로그의 해당 ID들을 `@DisplayName(id)`로 실패 테스트 작성
2. **Green**: 통과시키는 최소 구현
3. **Refactor**: 중복/네이밍/응집 정리. 다음 사이클 진입 전 모든 기존 테스트 회귀 통과

---

## D. 관찰·면접 포인트 메모

- VO 단위 테스트가 Spring 없이 통과 = "테스트 가능한 구조" 체감 증거
- 같은 raw·birthDate로 BCrypt 두 번 → 해시 다름 (salt 무작위성) 검증은 **테스트로 정책을 못박는** 좋은 예
- `BirthDate(value, today)` 시그니처는 **테스트 격리** 가치의 단적 사례 (시간 의존성 외부화)
- FAC-03(동시성)을 카탈로그에 둔 이유: DB unique 안전망의 존재 가치 명시. 면접 답변 시 "어플리케이션 선검사만으로는 동시 가입을 막을 수 없다"는 한계 설명용

---

## E. 변경 이력

- 2026-05-21 v1: 컴포넌트 인벤토리 + 테스트 카탈로그 초안.
