# Domain ↔ JPA 통합 엔티티 (MVP)

## Rule
Aggregate Root 클래스에 JPA `@Entity` 를 직접 부여한다 (Domain 클래스 ≠ JPA Entity 분리하지 않음). **MVP 범위 한정 채택**, 도메인 폭증 시 분리 가능성을 열어둔다.

## Why
- 보일러플레이트 절감 (Domain ↔ Persistence 매퍼 불필요)
- Round 1 규모에선 변환 비용이 학습 가치보다 큼
- 보존된 골격 `BaseEntity` (`modules/jpa`) 의 audit 필드 자동 활용

## How to apply
- 형태: `@Entity class User(...) : BaseEntity()` — `com.stay.domain.user.User`
- 도메인 메서드(`signUp`, `changePassword` 등) 도 같은 클래스에 둠
- JPA 가 요구하는 protected no-arg 생성자는 별도 유지 (Kotlin 의 경우 `kotlin-jpa` plugin 으로 자동 해결됨)
- VO 필드는 `@Embedded` / `@Convert` / `@AttributeOverride` 로 영속화
- **분리 검토 트리거**:
  - ① 영속 모델과 도메인 모델 분기 필요 (e.g., snapshot 테이블)
  - ② 다른 영속(NoSQL/외부 API) 동시 사용
  - ③ 도메인 메서드 비대화로 단일 책임 침해
- **위반 신호**: 별도 `UserEntity` + `UserDomain` 분리하면서 매퍼만 늘어남 / Aggregate 가 JPA 세부에 더 끌려다님

## References
- 결정: `docs/round-1/02-tdd-plan.md` 인벤토리 비고 (트레이드오프 메모)
