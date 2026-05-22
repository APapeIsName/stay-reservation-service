# 검증을 도메인 VO 로 일원화

## Rule
모든 입력 검증은 **도메인 VO 의 생성자**에서 수행한다. 위반 시 `CoreException(ErrorType.BAD_REQUEST)` 를 던지고, `ApiControllerAdvice` 가 HTTP 400 으로 매핑한다. **Bean Validation 어노테이션(`@NotNull`, `@Pattern`, `@Email`, `@Size` 등) 은 사용하지 않는다.**

## Why
- Round 1 핵심인 "Spring 없이 단위 테스트 가능한 구조" 부합
- 검증 규칙이 한 곳에 응집 — 어노테이션 산재로 인한 누락·중복 방지
- API/배치/내부 호출 등 모든 진입점에서 동일 규칙 자동 적용 (어노테이션은 진입점마다 별도 처리 필요)

## How to apply
- 필드별 VO: `LoginId`, `RawPassword`, `Password`, `Name`, `BirthDate`, `Email`, `PhoneNumber` 등
- VO 생성자에 정규식/길이/형식 검증 → 위반 시 `throw CoreException(ErrorType.BAD_REQUEST, "<필드명> 형식이 올바르지 않습니다.")`
- Controller 의 Request DTO 는 String/원시 타입으로 받고, Service 진입 시 VO 인스턴스화 → 도메인 예외 발생 시 advice 가 HTTP 매핑 ([API 응답 + 예외 매핑](./09-api-response-and-exception-mapping.md))
- 단위 테스트는 VO 클래스만 대상 — `@SpringBootTest` 불필요, 빠르게 회귀 검증
- **위반 신호**:
  - `@Valid`, `@NotBlank`, `@Pattern`, `@Email` 등 Bean Validation 사용
  - Controller 단에서 `if (...) throw ...` 산재 검증
  - DTO 에 검증 어노테이션 부착

## References
- 결정: `docs/round-1/01-signup-requirements.md` D-1
- 카탈로그: `docs/round-1/02-tdd-plan.md` Part B
