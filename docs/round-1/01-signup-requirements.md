# Round 1 — 회원가입 요구사항 (확정본)

> 본 문서는 [`docs/curriculum/round-1.md`](../curriculum/round-1.md) 의 Quest 중 **회원가입** 기능에 한해 요구사항을 확정한 것이다. 내 정보 조회·비밀번호 수정·헤더 인증은 차주 이후로 분리한다.

## 1. 도메인·패키지

- **Aggregate Root**: `User` (`com.stay.domain.user.User`)
- **검증 응집 전략**: 도메인 VO 검증을 단일 진입점으로 둔다. Bean Validation 어노테이션은 사용하지 않고, **VO 생성자에서 도메인 예외(`CoreException`)를 던지며** `ApiControllerAdvice` 가 HTTP 400 으로 매핑한다. 이로써
  - 단위 테스트가 Spring 없이 가능 (Round 1 핵심: 테스트 가능한 구조)
  - 검증 규칙이 한 곳에 응집 (어노테이션 산재 방지)
  - 다른 진입점(배치/내부 호출)에서도 동일 규칙 자동 적용

### 모듈 / 패키지 배치 (Round 1 후반 리팩토링 반영)

도메인 코드는 **`modules/domain`** 으로 분리. 사유 [Q7 (questions.md)](./03-questions.md) 참조.

```
modules/domain/                            (도메인 — 재사용 자산)
└── src/main/kotlin/com/stay
    ├── domain/user/
    │   ├── User                          // Aggregate Root (Cycle 8 예정)
    │   ├── LoginId                       // VO
    │   ├── Password                      // VO (해시값 보유)
    │   ├── RawPassword                   // VO (평문, 정책 검증만)
    │   ├── Email                         // VO
    │   ├── PhoneNumber                   // VO
    │   ├── BirthDate                     // VO
    │   └── UserRepository                // 도메인 인터페이스 (Cycle 9 예정)
    └── support/error/
        ├── CoreException
        └── ErrorType                     // HttpStatus 비의존 (Int statusCode)

apps/stay-api/                             (실행 가능한 SpringBoot 앱)
└── src/main/kotlin/com/stay
    ├── StayApiApplication
    ├── application/user/                  (Cycle 9 예정)
    │   ├── UserService                    // 가입 유스케이스
    │   ├── SignupCommand
    │   └── UserInfo
    ├── infrastructure/user/               (Cycle 9 예정)
    │   ├── UserJpaRepository             // Spring Data JPA
    │   └── UserRepositoryImpl            // 도메인 인터페이스 어댑터
    └── interfaces/api/
        ├── ApiResponse                   // envelope
        ├── ApiControllerAdvice           // ErrorType → HttpStatus 매핑
        └── v1/user/                       (Cycle 10 예정)
            ├── UserV1Controller
            ├── UserV1ApiSpec
            └── UserV1Dto
```

> 면접 트레이드오프 메모:
> - **Domain ↔ JPA 엔티티 분리 vs 통합**: 본 과제는 **통합**(엔티티에 `@Entity` + 도메인 메서드) 1차 채택. 보일러플레이트 절감. 향후 도메인 폭증 시 분리 가능성 열어둠
> - **도메인 모듈 분리** (Round 1 후반): `apps/stay-api → modules/domain` 분리. 도메인 의존성(BCrypt 등) 격리, 다른 앱(stay-batch 등) 재사용 자연 — [Q7](./03-questions.md)
> - **ErrorType HttpStatus 비의존**: 도메인 모듈이 spring-web 에 의존하지 않도록 Int `statusCode` 보관. HttpStatus 매핑은 `ApiControllerAdvice` 담당

## 2. 필드 규칙 (확정)

| 필드 | 규칙 | 비고 |
|---|---|---|
| `loginId` | `^[A-Za-z0-9]{4,20}$` | 영문 대소문자 + 숫자, **유일성 강제** |
| `password` | 8\~16자, allowed charset 만 허용, **YYYYMMDD 형식의 생년월일 substring 포함 불가**, BCrypt 단방향 해시로 저장 | 4종 조합 강제 ❌ (스펙 직역) |
| `name` | `^[가-힣]{1,10}$` | 한글 1\~10자. 외국인/로마자 미수용(MVP) |
| `birthDate` | ISO `yyyy-MM-dd`, 미래 불가, **만 14세 이상** | 한국 정통망법(개인정보 처리 동의 가능 연령) |
| `email` | RFC 5322 호환 (`@Email` 수준) | 유일성 미요구 |
| `phoneNumber` | `^010-\d{4}-\d{4}$` | 예약 SMS 발송 대상이므로 형식 엄수 |

### Password allowed charset
- 허용 문자: `A-Z`, `a-z`, `0-9`, `!@#$%^&*()-_=+[]{};:'",.<>/?\|`
- 전체 정규식: `^[A-Za-z0-9!@#$%^&*()\-_=+\[\]{};:'\",.<>/?\\|]{8,16}$`
- **조합 강제 없음** (스펙은 "~만 가능" = 허용 charset 정의에 한정). 향후 보안 요건 강화 시 추가 가능.

### 생년월일 포함 검사
- 검사 대상 표현: `YYYYMMDD` 한 가지 (예: `birthDate=1995-03-15` → `"19950315"` substring 검사)
- 트레이드오프: YYMMDD/MMDD 까지 확장하면 안전성↑이나 false-positive(우연의 일치) 증가. MVP는 가장 흔한 표현 1종만.

## 3. 비즈니스 규칙

| ID | 규칙 | 강제 위치 |
|---|---|---|
| R-1 | `loginId` 유일성 — 중복이면 가입 거부 | (a) 어플리케이션 선검사(친절한 에러), (b) DB unique 제약(동시성 안전망) — **둘 다** |
| R-2 | 비밀번호에 생년월일(`YYYYMMDD`) substring 포함 금지 | `Password` 생성 시 `birthDate` 인자로 받아 검증 |
| R-3 | 비밀번호 BCrypt 해시 저장 | `Password` VO 내부에서 해시화. 평문은 도메인 외부로 노출/저장 X |
| R-4 | 검증 실패 → 도메인 예외 → HTTP 매핑 | `CoreException(ErrorType.BAD_REQUEST)` / `CONFLICT` — `ApiControllerAdvice` 위임 |

## 4. API 계약

### `POST /api/v1/users` — 회원가입

**Request**
```json
{
  "loginId": "alen2026",
  "password": "P@ssw0rd!",
  "name": "공명선",
  "birthDate": "1995-03-15",
  "email": "alen@example.com",
  "phoneNumber": "010-1234-5678"
}
```

**Response 201 Created** (마스킹 미적용 — 본인이 방금 입력한 데이터 반환)
```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "userId": 1,
    "loginId": "alen2026",
    "name": "공명선",
    "birthDate": "1995-03-15",
    "email": "alen@example.com",
    "phoneNumber": "010-1234-5678"
  }
}
```

**Response 409 Conflict** — `loginId` 중복
```json
{ "meta": { "result": "FAIL", "errorCode": "CONFLICT", "message": "이미 사용 중인 로그인 ID 입니다." }, "data": null }
```

**Response 400 Bad Request** — 필드 검증 실패 (예: 비밀번호 정책 위반)
```json
{ "meta": { "result": "FAIL", "errorCode": "BAD_REQUEST", "message": "비밀번호는 8~16자의 허용 문자만 사용할 수 있으며, 생년월일을 포함할 수 없습니다." }, "data": null }
```

> 응답 envelope·에러코드는 보존 골격인 `ApiResponse`, `ErrorType` 을 그대로 활용.

## 5. 테스트 슬라이싱 (TDD 진입 매핑)

### Unit (Spring 없음)
- `LoginIdTest` — 길이 경계(3·4·20·21), 허용 charset, 위반 케이스(공백/특수문자/한글)
- `PasswordTest` — 길이 경계(7·8·16·17), 허용 charset 위반, 생년월일 substring 포함, BCrypt 해시 결과 검증(matches)
- `EmailTest` — RFC 위반/정상
- `PhoneNumberTest` — `010-XXXX-XXXX` 정상/위반(011·하이픈 없음·자릿수)
- `BirthDateTest` — `yyyy-MM-dd` 파싱, 미래 거부, 만 14세 미만 거부(경계: 14년 0일/14년 1일)
- `UserTest` — 모든 VO 정상이면 생성 성공, VO 실패는 각 VO 테스트에서 다룸 → User 는 합성·동등성 위주

### Integration (`@SpringBootTest` + Testcontainers MySQL)
- `UserServiceIntegrationTest`
  - 정상 가입 시 저장됨 + DB에 BCrypt 해시 저장 확인 (평문 미저장)
  - 중복 `loginId` 가입 시 `CoreException(CONFLICT)`
  - 검증 실패 시 트랜잭션 롤백 (사이드 이펙트 없음)

### E2E (`@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate`)
- `UserV1ApiE2ETest`
  - `POST /api/v1/users` 정상 → 201 + envelope 검증
  - 중복 loginId → 409
  - 필드 검증 실패 파라미터화 (잘못된 password / phone / name / birthDate / loginId / email)

> ⚠️ 로컬 환경 주의: Testcontainers ↔ Docker Desktop 29.x 비호환으로 `@SpringBootTest` 컨텍스트 테스트가 로컬에서 실패할 수 있다. 검증은 `./gradlew clean ktlintCheck build -x test` + 단위 테스트 우선. (메모리: `testcontainers-docker-desktop-incompat`)

## 6. 본 주차 Out-of-Scope (차주 이후)

- 내 정보 조회 (`name` 끝글자 마스킹, `phoneNumber` 중간 자리 마스킹)
- 비밀번호 변경 (현재 비밀번호 검증, 동일 비번 금지)
- 헤더 기반 인증(`X-Loopers-LoginId` / `X-Loopers-LoginPw`) — 헤더 명을 그대로 둘지(`X-Loopers-`) vs 도메인 치환(`X-Stay-`) 차주에 결정
- 이메일 유일성 / 휴대폰 유일성 (현재 명시 없음)

## 7. 변경 이력
- 2026-05-20 v1: 초안 작성. 의사결정 4건 확정(엔티티명=User, loginId=`^[A-Za-z0-9]{4,20}$`, 생년월일 검사=YYYYMMDD 1종, name=한글 1~10자).
