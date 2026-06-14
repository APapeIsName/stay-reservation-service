# ADR-003 — 일자별 재고·요금 한 테이블 `daily_room`

**Status**: Accepted
**Date**: 2026-05-28
**Round**: Round 2 (설계 단계)

> ⚠️ **재검토 트리거** — 가격 이력 분석 / 가격 변경 권한 분리 / 동시성 영역 분리가 *실제 요구* 가 되면 Booking.com 식 2 테이블 분리로 마이그레이션 (새 ADR 로 *대체*).

---

## Context

Round 2 시나리오 (`docs/curriculum/round-2-scenario.md`) 의 도메인 용어 표는 *일자별 재고* (DailyRoomInventory) 와 *일자별 요금* (DailyRoomRate) 를 **별도 객체** 로 정의한다:

| 용어 | 영문 | 설명 |
|---|---|---|
| 일자별 재고 | DailyRoomInventory | 특정 날짜의 객실 타입별 판매 가능 수량 |
| 일자별 요금 | DailyRoomRate | 특정 날짜의 객실 타입별 1박 요금 |

그러나 동시에 시나리오의 어드민 API 는 **두 정보를 한 묶음으로 받는다**:

```http
PUT /api-admin/v1/rooms/{roomTypeId}/inventory
{
  "ranges": [
    { "from": "2026-05-01", "to": "2026-05-31",
      "totalRooms": 10, "pricePerNight": 120000 },
    ...
  ]
}
```

→ 도메인 용어 표는 *2 개념* 이지만, API 표면은 *1 묶음*. 이를 어떻게 영속에 반영할지가 결정 필요.

### 추가 맥락 — 빅테크 패턴

- **Booking.com** — `Property → RoomType → RatePlan → RoomRate` 4단 계층 + 일자별 inventory/price 분리. *환불불가 / 조식포함 / 기본* 등 다중 요금제 (RatePlan) 운용
- **Airbnb** — Listing × Calendar (RoomType 자체 없음). 평탄 모델
- 우리 시나리오엔 RatePlan 개념 없음 → Booking.com 4단 중 *3단까지* 만 매핑 (Property → RoomType → DailyRoom)

## Decision

**일자별 재고와 일자별 요금을 단일 테이블 `daily_room` 으로 통합한다.**

테이블 스키마:

```sql
CREATE TABLE daily_room (
  room_type_id    BIGINT      NOT NULL,
  date            DATE        NOT NULL,
  total_rooms     INT         NOT NULL,
  reserved_rooms  INT         NOT NULL DEFAULT 0,
  price_per_night BIGINT      NOT NULL,   -- KRW
  closed          BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at      TIMESTAMP   NOT NULL,
  updated_at      TIMESTAMP   NOT NULL,

  PRIMARY KEY (room_type_id, date),
  CHECK (reserved_rooms <= total_rooms)
);
```

- **`(room_type_id, date)` 복합 자연키** — 산업 표준 (`docs/round-2/01-domain-study.md § 2.3` 빅테크 비교)
- 도메인 객체명은 **`DailyRoom`** — C1 결정으로 확정 (`docs/round-2/03-questions.md` Q5, 2026-05-30)
- DailyRoom 은 **별도 Aggregate** (Property Aggregate 와 분리) — 변경 빈도 극단적 차이

## Consequences

### ✅ 긍정적
- **어드민 API 자연 부합** — `PUT /rooms/{id}/inventory` 의 `{ranges: [{from, to, totalRooms, pricePerNight}]}` 가 *한 row 로 펼침*. 직관적
- **본 라운드 단순성** — 테이블 1개. JPA 매핑·Repository·테스트 모두 단순
- **검색 쿼리 단순화** — 검색 결과의 *합산 가격* 이 *단일 JOIN* 으로 끝 (`docs/design/02-sequence-diagrams.md § 1`)
- **차감 로직 단순** — 예약 생성 시 한 row 에서 *재고/가격 둘 다* 읽음. *차감 + 가격 합산* 이 한 쿼리 결과로 처리 (`docs/design/02-sequence-diagrams.md § 2`)
- **시점 일관성 자연** — 예약 시점에 *해당 row 의 가격* 을 스냅샷으로 보관. 가격 분리 시 *두 row 동시 조회 일관성* 별도 처리 필요
- **`(room_type_id, date)` 복합키** — 산업 표준 (`docs/round-2/01-domain-study.md § 4` 빅테크 공통 패턴 1)

### ⚠️ 부정적 / 비용
- **동시성 충돌 영역이 한 row 에 모임** — 재고 차감과 가격 변경이 *같은 row* 에서 발생. 두 변경의 동시성 영역이 분리되지 않음 (본 라운드는 동시성 본격 처리 미구현이라 표면화 안 됨)
- **가격 이력 관리 어려움** — `price_per_night` 변경 시 *과거 값* 추적 불가. 가격 분석·감사 대응 시 별도 history 테이블 필요
- **가격 변경과 재고 차감의 권한 분리 어려움** — 한 테이블 권한이 두 책임에 같이 적용. 권한 모델 도입 시 컬럼 레벨 권한 또는 분리 필요
- **RatePlan 도입 시 마이그레이션 비용** — Booking.com 식 환불불가/조식포함 RatePlan 도입 시 `daily_room` 을 `daily_room_inventory` + `daily_room_rate (rate_plan_id, ...)` 로 분리해야 함

### 🔄 후속 영향
- **DailyRoom 도메인 객체의 책임 통합** — `DailyRoom.consumeOne()`, `DailyRoom.releaseOne()`, `DailyRoom.priceFor(date)` 같은 메서드가 *한 객체* 에 모임 (`docs/design/02-sequence-diagrams.md § 2.2` self-call)
- **검색·예약 시퀀스의 트랜잭션 경계 단순화** — N일치 row 만 조회·UPDATE 하면 됨. 두 테이블이면 *JOIN 또는 두 번 조회* 필요
- **ERD 가 단순** — `04-erd.md` 에서 1 테이블로 표현. RoomType 1:N DailyRoom (room_type_id FK)
- **Reservation 의 일자별 점유 표현** — 별도 점유 테이블 없이 `daily_room.reserved_rooms` 카운터로 표현. *어떤 예약이 어떤 날짜를 점유했는지* 직접 추적 불가 → 예약 취소 시 *해당 날짜 row 의 reserved_rooms 감소* 만으로 처리

## Alternatives Considered

### 대안 B — 두 테이블 분리 (Booking.com 식)
- 모양:
  ```sql
  CREATE TABLE daily_room_inventory (
    room_type_id, date, total_rooms, reserved_rooms, closed,
    PK (room_type_id, date)
  );
  CREATE TABLE daily_room_rate (
    room_type_id, date, price_per_night,
    PK (room_type_id, date)
  );
  ```
- 장점:
  - 가격 변경 / 재고 차감의 *동시성 영역 분리*
  - 가격 변경 권한과 재고 변경 권한 분리 자연
  - RatePlan 도입 시 `daily_room_rate (rate_plan_id, ...)` 로 확장 자연
  - 가격 이력 관리에 history 테이블 추가 자연
- 단점:
  - 어드민 API `{ranges: [...]}` 펼침이 *2 테이블 upsert* 가 됨 (트랜잭션 일관성 추가)
  - 검색 쿼리에 *2 JOIN* 또는 *2 회 조회 후 in-memory 결합*
  - 예약 시 *재고와 가격을 따로 조회* — 동시성 시 두 row 의 시점 일관성 별도 보장
  - 도메인 객체 분리 → `DailyRoomInventory.consumeOne()` 과 `DailyRoomRate.priceFor()` 가 *두 객체* 협력
- 거절 이유: **본 라운드 단순성 > 미래 동시성·이력 관리 정밀성**. 가격 이력 분석 / 권한 분리 / RatePlan 이 *실제 요구* 가 되면 마이그레이션. *마이그레이션 비용* 도 큰 편이 아님 (단방향 splitting)

### 대안 C — 한 테이블 + 가격 변경 이력 별도 테이블
- 모양: `daily_room` (현재 가격) + `daily_room_rate_history (changed_at, old_price, new_price, ...)`
- 장점:
  - 본 라운드 단순성 + 가격 이력 추적
  - 가격 분석·감사 대응 자연
- 단점:
  - 본 라운드에 history 관리 요구 없음 → *과한 정도*
  - history 갱신 트리거 (애플리케이션 / DB 트리거) 결정 필요
  - 이력 무결성 검증 부담
- 거절 이유: 본 라운드 over-engineering. history 가 *실제 요구* 가 되면 *그때* 추가하면 됨

### 대안 D — 별도 점유 테이블 (`reservation_inventory_consumption`)
- 모양: `daily_room` (가용성) + `reservation_inventory_consumption (reservation_id, room_type_id, date)` UNIQUE
- 장점:
  - *어떤 예약이 어떤 날짜를 점유했는지* 직접 추적
  - **DB unique constraint 로 더블부킹 강력 방어** (산업 모범)
  - 취소 시 *해당 예약의 점유 row 만* 삭제 → 명확
- 단점:
  - 모델 복잡도 ↑
  - 본 라운드는 *동시성 본격 처리 미구현* (Q4 보류) 이라 unique constraint 활용 시점 미정
- 거절 이유: 후속 라운드 동시성 본격 도입 시 검토 가치 있음. 본 라운드는 채택 안 함

## Related

- 결정 누적: [`docs/round-2/03-questions.md` Q2](../round-2/03-questions.md)
- 도메인 학습: [`docs/round-2/01-domain-study.md` § 2.3 DailyRoomInventory + § 2.4 DailyRoomRate](../round-2/01-domain-study.md)
- 요구사항 명세: [`docs/design/01-requirements.md` § 6 Q2](../design/01-requirements.md)
- 시퀀스: [`docs/design/02-sequence-diagrams.md § 1, § 2`](../design/02-sequence-diagrams.md)
- ERD (예정): `docs/design/04-erd.md`
- 빅테크 비교: [`docs/round-2/01-domain-study.md § 4`](../round-2/01-domain-study.md) — Booking.com 4단 계층 / Airbnb 평탄
- 산업 패턴: [`docs/round-2/01-domain-study.md` 산업 공통 패턴 1](../round-2/01-domain-study.md) — *"(객실단위, 날짜) 그리드 인벤토리"*

---

## 면접 답변 템플릿

> "어드민 API 가 inventory + price 를 *한 묶음* 으로 받는 점 (`{ranges: [{from, to, totalRooms, pricePerNight}]}`), 시나리오상 본 라운드는 *기능 동작* 까지가 범위 (동시성·검색 성능은 후속 라운드 도전) 인 점을 들어 `(room_type_id, date)` 복합키의 **단일 `daily_room` 테이블** 로 시작했습니다.
>
> Booking.com 은 4단 계층 (Property → RoomType → RatePlan → RoomRate) + 일자별 inventory/price 분리지만, 우리 시나리오엔 RatePlan 개념이 없어 3단까지만 매핑합니다.
>
> 가격 변경 권한 분리 / 가격 이력 분석 / 동시성 영역 분리 / 환불불가·조식포함 같은 RatePlan 이 *실제 요구* 가 되면 Booking.com 식 2 테이블 분리로 마이그레이션 가능합니다. 마이그레이션 비용도 *단방향 splitting* 이라 크지 않습니다."
