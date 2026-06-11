# 도메인 모델링 — Entity / VO / Domain Service

## Rule
현실 개념과 비즈니스 규칙은 **도메인 객체에 캡슐화**한다. 각 개념을 **Entity · Value Object · Domain Service** 중 책임에 맞는 형태로 분류하고, 규칙이 서비스 계층으로 새어 나가지 않게 한다.

- **Entity**: 고유 ID 로 동일성을 판단하고 **상태 변화·연속성**을 가지는 객체 (`User`, `Property`, `Reservation`, `DailyRoom`)
- **Value Object**: "그 값이 무엇이냐"만 중요한 **불변(immutable)** 객체. 값 동등성으로 비교 (`Address`, `DateRange`, `GuestInfo`, 각종 `*Snapshot`)
- **Domain Service**: 한 객체에 담기 애매한 **여러 도메인 객체의 협력 로직**을 위임받아 처리. **무상태**(입력→출력 명확), 동일 도메인 경계 안에서만 협력

## Why
- 발제 핵심: "데이터가 아니라 **행위의 주체와 책임**". 규칙이 객체 안에 있어야 응집도·테스트 용이성·면접 설명력이 산다
- 규칙이 서비스에 흩어지면(절차지향) 중복·누락이 생기고, Spring 없는 단위 테스트가 어려워진다 ([rule 06](./06-validation-via-domain-vo.md), [rule 14](./14-test-strategy-tdd.md))
- 잘못된 예: `Product.likedUserIds.add()` 처럼 의미가 커질 개념을 컬렉션으로 욱여넣으면 확장이 막힘 → 별도 도메인(`Wishlist`)으로 격리

## How to apply

### 1. 분류 기준
| 질문 | Yes → |
|---|---|
| 고유 ID 로 추적하고 상태가 바뀌는가? | **Entity** (Aggregate Root 후보) |
| 값 자체가 의미이고 불변인가? | **Value Object** |
| 여러 도메인 객체를 무상태로 조율하는가? | **Domain Service** |
| 단지 "무언가 하는(doer)" 연산기인가? | 아래 *doer 경계* 참조 |

### 2. 규칙은 도메인 객체 안에 (Tell, Don't Ask)
- Service 가 도메인 필드를 직접 조작 ❌ → 도메인 메서드 위임 ✅
  - `dailyRoom.reservedRooms += 1` ❌ → `dailyRoom.consumeOne()` ✅ (음수/초과 불변식이 메서드 안)
  - `reservation.userId == userId` ❌ → `reservation.belongsTo(userId)` ✅
  - 위반 시 도메인 예외: `throw CoreException(ErrorType.CONFLICT, "...")` ([rule 09](./09-api-response-and-exception-mapping.md))
- 생성 의도는 정적 팩토리로: `Reservation.confirm(...)`, `Password.encrypt(...)` ([rule 08](./08-static-factory-and-clock-injection.md))

### 3. 휴리스틱 — "규칙이 여러 서비스에 반복되면 도메인으로 끌어올린다"
- 동일 검증/계산이 **2개 이상 Application Service** 에 나타나면, 그 규칙은 도메인 객체(또는 Domain Service)에 속할 신호다
- 예: "최대 인원 초과 거절"이 예약·견적 서비스 양쪽에 보이면 → `RoomType.canAccommodate(guestCount)` 로 통합

### 4. doer / Manager 경계 (발제 QNA)
- `~Manager`, `~Processor`, `~Helper` 류가 **고유 상태·도메인 의미 없이 연산만** 한다면 그것은 도메인 개념이 아니다
- 진짜 무상태 *협력*이면 → **Domain Service** 로 (도메인 경계 내 객체 조율)
- 단순 기술 유틸이면 → support/util 로 분리하되 **도메인 모델을 오염시키지 않는다**
- 위반 신호: 빈약한(anemic) 도메인 + 거대한 `XxxManager` 가 모든 규칙을 들고 있음

### 5. Aggregate 경계 (round-2 확정)
- **Aggregate Root 만 Repository 를 가진다.** 내부 Entity 는 Root 통해서만 변경
  - `Property` 가 Root, `RoomType` 은 내부 Entity → `property.addRoomType(...)` 로만 조작
- **Aggregate 간 참조는 ID 만.** 객체 참조 금지
  - `Reservation` 은 `propertyId`/`roomTypeId`/`userId` 만 보관, 객체는 안 들고 있음
- 시점 일관성이 필요한 값은 **스냅샷 VO** 로 복제 (`CancellationPolicySnapshot`, `PriceSnapshot`) — 원본 정책이 바뀌어도 예약은 당시 값 유지

### 6. 일자 기반 재고/요금 — `(room_type_id, date)` 단위 도메인 객체
- 재고·요금은 `(roomTypeId, date)` 복합키를 가진 **단일 `DailyRoom` Aggregate** 로 모델링 (ADR-003 — Inventory/Rate 분리 안 함)
- 재고 차감/복원·잔여 계산은 도메인 메서드: `consumeOne()` / `releaseOne()` / `availableRooms()` / `isAvailable()`
- **음수 방지는 도메인 레벨**: `consumeOne()` 안에서 `reservedRooms < totalRooms` 검증

### 체크리스트
- ✅ 새 개념을 Entity/VO/Domain Service 중 하나로 명시 분류했다
- ✅ 비즈니스 규칙이 Service if-문이 아니라 도메인 메서드 안에 있다 (Tell, Don't Ask)
- ✅ 동일 규칙이 여러 서비스에 중복되지 않는다 (중복이면 도메인으로 승격)
- ✅ Aggregate 간 참조가 ID 로만 이뤄진다
- ✅ 재고/요금이 `(roomTypeId, date)` 단위로 모델링되고 음수 방지가 도메인에 있다
- ✅ doer/Manager 가 도메인 모델을 오염시키지 않는다

## References
- 도메인 설계 본체: `docs/design/03-class-diagram.md` (4 Aggregate · VO · 메서드 시그니처)
- 결정: `docs/round-2/03-questions.md`, `docs/adr/ADR-003-single-daily-room-table.md`
- 발제: `docs/curriculum/round-3.md` (Entity/VO/Domain Service 표, doer QNA), `docs/curriculum/round-3-quest.md` (도메인 & 객체 설계 전략)
- 짝 rule: [06 검증 VO](./06-validation-via-domain-vo.md), [07 Domain↔JPA](./07-domain-jpa-integration.md), [08 정적 팩토리·Clock](./08-static-factory-and-clock-injection.md), [19 레이어드·DIP](./19-layered-architecture-dip.md)
