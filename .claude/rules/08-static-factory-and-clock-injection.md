# 정적 팩토리 + 시간 의존성 외부 주입

## Rule
- 도메인 객체의 의도가 분명한 **생성 진입점은 정적 팩토리 메서드**로 노출한다 (e.g., `User.signUp(...)`, `Password.encrypt(raw, birthDate)`)
- **시간(현재 시각/오늘 날짜) 에 의존하는 도메인 검증은 시간을 외부에서 주입받는다** (e.g., `BirthDate(value, today: LocalDate)`)

## Why
- **정적 팩토리**: 생성자 노출 대비 의도가 코드에 드러남 (`User.signUp` vs `User(...)`), 다양한 생성 경로를 의미 분리해 표현 가능 (`Password.encrypt` vs `Password.ofHashed`)
- **시간 외부 주입**: 도메인 검증(만 14세 등) 테스트가 결정적·재현 가능. `LocalDate.now()` 를 도메인 코드에서 직접 부르면 경계 테스트가 시점 의존적이 되어 fragile

## How to apply
- Aggregate/VO 의 기본 생성자는 가능하면 `internal`/`private` 으로 두고, 의미가 드러나는 정적 팩토리 메서드 추가
- 시간 의존 VO 시그니처: `BirthDate(value: String, today: LocalDate)` — Service 에서 `LocalDate.now(clock)` 을 전달
- Spring 에서 `Clock` 빈을 정의해 Service 가 주입받음 (테스트에선 `Clock.fixed(Instant.parse("..."), ZoneId.of("Asia/Seoul"))`)
- **위반 신호**:
  - 도메인 코드에 `LocalDate.now()`, `LocalDateTime.now()`, `Instant.now()` 직접 호출
  - 생성자 직접 노출로 호출 의도가 코드에 드러나지 않음

## References
- 결정: `docs/round-1/02-tdd-plan.md` D-A2, D-A4
