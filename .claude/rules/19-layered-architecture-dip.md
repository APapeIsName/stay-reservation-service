# 레이어드 아키텍처 + DIP

## Rule
코드는 **4 레이어**(interfaces / application / domain / infrastructure)로 나누고, 모든 의존은 **domain 을 향해서만** 흐른다. domain 은 어떤 레이어에도 의존하지 않는다 (DIP — Repository 인터페이스는 domain 에, 구현은 infrastructure 에).

```
[interfaces.api]   HTTP 요청/응답 변환, ApiResponse envelope
       ↓
[application]      유스케이스 오케스트레이션 (얇게), @Transactional 경계
       ↓
[modules/domain]   Entity·VO·Domain Service·Repository 인터페이스·도메인 예외  ← 의존 0
       ↑
[infrastructure]   Repository 구현 (JPA adapter), 외부 시스템 연동
```

## Why
- 도메인이 기술(웹/JPA/외부 API)에 안 끌려다님 → Spring 없는 단위 테스트 가능 ([rule 14](./14-test-strategy-tdd.md))
- 기술 교체(예: JPA → 다른 영속)가 domain 에 영향 없음 — port(인터페이스)와 adapter(구현)의 분리
- 레이어 책임이 명확하면 "이 코드 어디 둘까"가 자동 결정됨 ([rule 20](./20-package-and-dto-strategy.md))

## How to apply

### 1. 레이어별 책임
| 레이어 | 위치 | 책임 | 금지 |
|---|---|---|---|
| interfaces | `apps/stay-api/.../interfaces/api/v1/<domain>` | HTTP ↔ DTO 변환, `ApiResponse` 반환 | 비즈니스 규칙, 직접 Repository 호출 |
| application | `apps/stay-api/.../application/<domain>` | 유스케이스 흐름 조립, 트랜잭션 경계 | 도메인 규칙 직접 구현 |
| domain | `modules/domain/.../domain/<domain>` | Entity·VO·Domain Service·Repository port·도메인 예외 | spring-web 등 기술 의존 |
| infrastructure | `apps/stay-api/.../infrastructure/<domain>` | Repository adapter (JPA), 외부 연동 | 비즈니스 규칙 |

### 2. DIP — Repository port / adapter
- **port (인터페이스)**: `modules/domain` 에 정의 — `UserRepository`, `PropertyRepository`, `DailyRoomRepository`, `ReservationRepository`, `WishlistRepository`
- **adapter (구현)**: `apps/stay-api/.../infrastructure/<domain>` 에 — `UserRepositoryImpl` + `UserJpaRepository` (Spring Data JPA)
- Application Service 는 **port 타입으로만 주입**받는다. `*JpaRepository` 직접 주입 금지
- domain 모듈에 web 레이어 의존 금지 — `ErrorType` 이 `HttpStatus` 대신 `Int statusCode` 를 갖는 이유 ([rule 01](./01-package-and-modules.md))

### 3. Application Service 는 얇게 (orchestration only)
- 역할: ① 입력을 VO/도메인 객체로 변환 ② Repository 로 조회 ③ **도메인 메서드에 위임** ④ 저장 ⑤ 결과 DTO 변환
- 좋은 예 (위임 중심):
  ```kotlin
  @Transactional
  fun reserve(command: ReserveCommand): ReservationInfo {
      val dailyRooms = dailyRoomRepository.findAllByRoomTypeIdAndDates(...)
      dailyRooms.forEach { it.consumeOne() }          // 규칙은 도메인에
      val reservation = Reservation.confirm(...)       // 생성 의도는 정적 팩토리에
      return ReservationInfo.from(reservationRepository.save(reservation))
  }
  ```
- 나쁜 예: Service 안에서 `if (dailyRoom.reservedRooms >= dailyRoom.totalRooms) throw ...` — 규칙 누출 ([rule 18](./18-domain-modeling.md) §2)

### 4. Domain Service vs Application Service
| | Domain Service | Application Service |
|---|---|---|
| 위치 | `modules/domain` | `apps/stay-api/.../application` |
| 책임 | 여러 **도메인 객체 간** 규칙·계산 (무상태) | 유스케이스 흐름 (조회→위임→저장), 트랜잭션 |
| 의존 | 도메인 객체만 | Repository port, Domain Service, Clock 등 |
| 테스트 | L1/L2 (Spring X) | L2 (mock) / L3 (통합) |
- 도입 기준: 규칙이 **단일 Aggregate 에 못 담길 때만** Domain Service 신설. 한 객체에 담기면 그 객체의 메서드로 ([rule 18](./18-domain-modeling.md) §1)

### 체크리스트
- ✅ 의존 방향이 domain 을 향해서만 흐른다 (`modules/domain` 의 외부 의존 0)
- ✅ Repository 인터페이스는 domain, 구현은 infrastructure 에 있다
- ✅ Application Service 가 port 타입으로 주입받는다 (`*JpaRepository` 직접 주입 0)
- ✅ Application Service 메서드가 조회→위임→저장의 얇은 흐름이다 (규칙 if-문 누출 0)
- ✅ Domain Service 는 단일 객체에 못 담는 규칙에만 도입됐다

## References
- 의존 구조: `docs/design/03-class-diagram.md` (의존 방향 다이어그램, Repository port 목록)
- 실 사례: `modules/domain/.../user/UserRepository.kt` (port) ↔ `apps/stay-api/.../infrastructure/user/UserRepositoryImpl.kt` (adapter)
- 발제: `docs/curriculum/round-3.md` (Layered Architecture + DIP), `docs/curriculum/round-3-quest.md` (소프트웨어 아키텍처 체크리스트)
- 분리 결정: `docs/adr/ADR-001-modules-domain-separation.md`
- 짝 rule: [01 패키지·모듈](./01-package-and-modules.md), [18 도메인 모델링](./18-domain-modeling.md), [20 패키지·DTO 전략](./20-package-and-dto-strategy.md)
