# 패키지 구조 + DTO 전략

## Rule
- 패키지는 **레이어 우선 + 도메인 하위** 로 구성한다: `com.stay.<layer>.<domain>` (e.g., `com.stay.application.user`)
- DTO 는 **레이어마다 분리**한다 — API DTO ↔ application Command/Info ↔ 도메인 객체. 레이어 경계를 넘는 객체 재사용 금지

## Why
- 레이어 우선 구조는 [rule 19](./19-layered-architecture-dip.md) 의 의존 방향을 패키지로 가시화 — import 만 봐도 위반 탐지 가능
- DTO 레이어 분리: API 스펙 변경(필드 rename 등)이 application/domain 에 안 번지고, 도메인 객체가 직렬화/웹 관심사에 오염 안 됨
- "request DTO 를 domain 까지 들고 내려가는" 안티패턴 차단 — 각 레이어가 자기 입력 모델을 소유

## How to apply

### 1. 패키지 배치 (이 레포의 실제 구조)
| 레이어 | 패키지 | 예시 |
|---|---|---|
| interfaces | `apps/stay-api` → `com.stay.interfaces.api.v1.<domain>` | `UserV1Controller`, `UserV1ApiSpec`, `UserV1Dto` |
| application | `apps/stay-api` → `com.stay.application.<domain>` | `UserService`, `SignupCommand`, `UserInfo` |
| domain | `modules/domain` → `com.stay.domain.<domain>` | `User`, `LoginId`, `UserRepository` |
| infrastructure | `apps/stay-api` → `com.stay.infrastructure.<domain>` | `UserRepositoryImpl`, `UserJpaRepository` |
| 공통 지원 | `modules/domain` → `com.stay.support.error` | `CoreException`, `ErrorType` |

- 새 도메인(property, dailyroom, reservation, wishlist) 추가 시 같은 패턴으로 4 레이어에 `<domain>` 패키지 신설
- 도메인 코드는 항상 `modules/domain` ([rule 01](./01-package-and-modules.md)) — apps 에 도메인 두지 않음

### 2. DTO 3계층 (user 도메인 실 사례)
```
[interfaces]   UserV1Dto.SignupRequest ──→ SignupCommand     (Controller 가 변환)
               UserV1Dto.Response      ←── UserInfo          (Response.from(info))
[application]  SignupCommand ──→ VO 인스턴스화 → User.signUp(...)   (Service 가 변환)
               UserInfo      ←── User                        (UserInfo.from(user))
[domain]       User · VO                                     (웹/직렬화 무지)
```
| DTO | 소유 레이어 | 형태 | 역할 |
|---|---|---|---|
| `XxxV1Dto.*Request/Response` | interfaces | String/원시 타입 | HTTP 직렬화 계약. 버저닝(`V1`) 대상 |
| `XxxCommand` | application | String/원시 타입 | 유스케이스 입력. 웹 무지 |
| `XxxInfo` | application | 원시 타입 평탄화 | 유스케이스 출력. 도메인 객체 노출 차단 |
| Entity/VO | domain | 도메인 타입 | 규칙 보유. 레이어 밖 직렬화 금지 |

### 3. 변환 책임
- **Request → Command**: Controller (`SignupCommand(request.loginId, ...)` 또는 `request.toCommand()`)
- **Command → VO/도메인**: Application Service — VO 인스턴스화 시점에 도메인 검증 발동 ([rule 06](./06-validation-via-domain-vo.md))
- **도메인 → Info**: `XxxInfo.from(domainObject)` 정적 팩토리
- **Info → Response**: `XxxV1Dto.Response.from(info)` 정적 팩토리
- 변환 메서드는 **변환 결과를 소유한 쪽**에 둔다 (Response 변환은 Response 에)

### 4. 위반 신호
- Controller 가 도메인 객체(`User`)를 직접 반환하거나 JSON 직렬화
- `XxxV1Dto.Request` 가 application/domain 레이어 import 에 등장
- 도메인 Entity 에 `@JsonProperty` 등 직렬화 어노테이션 부착
- Command/Info 없이 Request → 도메인 직행 (레이어 단락)
- 패키지가 `com.stay.<domain>.<layer>` (도메인 우선) 로 뒤집힘 — 이 레포 컨벤션 위반

### 체크리스트
- ✅ 새 클래스가 `com.stay.<layer>.<domain>` 위치에 있다 (도메인은 `modules/domain`)
- ✅ Request/Command/Info/도메인 객체가 각자 레이어에 소유돼 있다
- ✅ 변환이 Controller(Request↔), Service(Command→도메인), 정적 팩토리(`from`) 로 일관된다
- ✅ 도메인 객체가 interfaces 레이어에 노출되지 않는다
- ✅ API 버전(`V1`) 은 interfaces DTO 에만 존재한다

## References
- 실 사례: `apps/stay-api/.../interfaces/api/v1/user/UserV1Dto.kt`, `apps/stay-api/.../application/user/{SignupCommand,UserInfo,UserService}.kt`
- 발제: `docs/curriculum/round-3-quest.md` (패키지 전략 항목), `docs/curriculum/round-3.md`
- 짝 rule: [01 패키지·모듈](./01-package-and-modules.md), [06 검증 VO](./06-validation-via-domain-vo.md), [09 ApiResponse](./09-api-response-and-exception-mapping.md), [19 레이어드·DIP](./19-layered-architecture-dip.md)
