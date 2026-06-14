# Round 2 — 작업 중 떠오른 질문

> 설계 단계에서 결정된 핵심 정책을 누적하는 Q&A 로그.
> 면접 자산: "왜 이렇게 설계했냐?" 에 답할 수 있는 근거 모음.
>
> 각 항목에는 **출처 표기**(질의자: 사용자 / 제안자: Claude) 를 명시해 누가 화두를 던졌는지 구분한다.
> 결정·트레이드오프는 본 문서에 누적, 본격 설계 산출물은 `docs/design/` 에 단일 폴더 증강형으로 기록.

---

## Q1. (2026-05-28) 예약 확정 시점 — `POST /reservations` 의 의미는?

**제안자**: Claude (Skill 3️⃣ 핵심 정책 결정 질문 4건 중 첫 번째)

### 맥락
시나리오 예약 상태 머신은 `PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT / CANCELLED`. 그러나 결제 도메인은 *추후 추가* 명시 (`docs/curriculum/round-2-scenario.md`). 결제가 없는 이번 라운드에서 `POST /reservations` 의 종착 상태가 무엇이어야 하는가.

### 검토했던 선택지

| 선택지 | 의미 | 트레이드오프 |
|---|---|---|
| **A. 즉시 CONFIRMED** | 검증 + 일자별 재고 차감 + CONFIRMED 까지 한 트랜잭션 | 한국 OTA 표준. 단순. 결제 도입 시 *결제 트랜잭션 안에 차감을 다시 끼워 넣어야* 함 |
| B. PENDING 생성 → (mock confirm) → CONFIRMED | 2단계. 시퀀스가 결제 도입에 자연 연결 | 이번 라운드에 mock confirm 호출이 *허구의 흐름* 으로 들어감. 시나리오 머신과 정확 일치 |
| C. 혼합 — 이번엔 즉시, enum 에만 PENDING 자리 | 단순 + 미래 자리 예약 | 절충안 |

### Round 2 결정 — **A. 즉시 CONFIRMED**

**사용자 답변**: 2026-05-28 — "즉시 CONFIRMED (한국 OTA 식)".

이유:
1. **시나리오 부합** — 결제가 본 라운드 out-of-scope 인 이상, PENDING 으로 두고 mock 으로 confirm 하는 흐름은 *허구의 트랜잭션 분리* 가 됨
2. **한국 OTA 패턴 일치** — 야놀자/여기어때 모두 "결제 즉시 100% 예약 확정" (`docs/round-2/01-domain-study.md` § 4)
3. **단순성** — `POST /reservations` 한 호출에 검증·차감·확정 단일 트랜잭션 → 일관성 보장이 *명시적*
4. **재검토 비용 낮음** — 결제 도입 라운드에서 PENDING 활용 패턴으로 *부가적 확장* 은 가능 (Airbnb 식 호스트 승인 등)

### 영향

- **시퀀스 다이어그램 (`docs/design/02-sequence-diagrams.md`)**: 예약 생성 시퀀스가 `Controller → ReservationService → (RoomType 조회 + 일자별 재고 차감 + Reservation save + CONFIRMED set)` 단일 `@Transactional` 경계
- **상태 머신**: enum 에 PENDING 정의는 두되, *결제 도입 시 활용 자리* 로 주석 표시. CONFIRMED 가 *진입 상태* (initial state)
- **`docs/design/01-requirements.md`**: 예약 정책 섹션에 "이번 라운드: 즉시 CONFIRMED / 결제 도입 시 PENDING 단계 도입 검토" 명시
- **취소 흐름 단순화**: PENDING 취소 케이스 분기 불필요. CANCELLED 진입은 CONFIRMED 또는 CHECKED_IN 에서만

### 재검토 트리거
- **결제 도입 라운드** 진입 시 — 결제 트랜잭션 안에 차감을 어떻게 끼울지 (B 안 재논의 가치)
- 호스트 승인 워크플로 도입 (Airbnb 식 PENDING 24h)
- 좌석/객실 *임시 점유* (timed hold) 같은 사전 점유 패턴 도입

### 면접 답변 템플릿
> "결제가 본 라운드 out-of-scope 라, PENDING 으로 두고 mock confirm 하는 흐름은 허구의 트랜잭션 분리가 된다고 판단해 즉시 CONFIRMED 로 갔습니다. 야놀자·여기어때 같은 한국 OTA 가 결제 즉시 확정 패턴이라 시나리오와도 일관됩니다. 결제 도입 시점에 PENDING 단계 도입은 부가 확장으로 가능 — Airbnb 식 호스트 승인이 필요한 경우엔 PENDING 24h 패턴까지 자연 확장됩니다."

### 출처
- 사용자 답변: 2026-05-28
- 빅테크 비교: [`01-domain-study.md` § 4 빅테크 비교](./01-domain-study.md#4-빅테크-비교-요약-도메인별-핵심만)
- 시나리오 원본: `docs/curriculum/round-2-scenario.md` "나아가며" (PG 일관성 = 후속 라운드 도전)

---

## Q2. (2026-05-28) DailyRoomInventory + DailyRoomRate — 한 테이블? 분리?

**제안자**: Claude (Skill 3️⃣ 핵심 정책 결정 질문 4건 중 두 번째)

### 맥락
일자별 재고와 일자별 요금이 *같은 (roomTypeId, date) 단위* 라는 점에서 한 테이블에 합칠 수도, 책임 분리로 두 테이블로 갈 수도 있음. 어드민 API 가 `{ranges: [{from, to, totalRooms, pricePerNight}]}` 한 묶음으로 받는 점도 신호.

### 검토했던 선택지

| 선택지 | 모양 | 트레이드오프 |
|---|---|---|
| **A. 한 테이블 `daily_room`** | `(room_type_id, date, total_rooms, reserved_rooms, price_per_night)` | 단순. 어드민 입력과 자연 부합. 동시성 충돌 영역이 한 row 에 모임 |
| B. 두 테이블 분리 (Booking.com 식) | `daily_room_inventory` + `daily_room_rate` | 가격 변경 / 재고 차감 동시성 영역 분리. 가격 이력 관리 자연. 쿼리·코드 복잡도 ↑ |
| C. 한 테이블 + 가격 변경 이력 별도 | `daily_room` + `daily_room_rate_history` | 가격 분석·감사 대응. 이번 라운드엔 과함 |

### Round 2 결정 — **A. 한 테이블 `daily_room`**

**사용자 답변**: 2026-05-28 — "한 테이블 daily_room (단순)".

이유:
1. **어드민 API 자연 부합** — `PUT /api-admin/v1/rooms/{roomTypeId}/inventory` 가 `{from, to, totalRooms, pricePerNight}` 묶음으로 받음. 펼치면 그대로 한 row
2. **이번 라운드 단순성** — 시나리오 도전 (동시성·검색 성능) 은 후속 라운드 명시. 한 테이블에서 시작이 합리
3. **검색 쿼리 단순화** — 검색 결과의 *합산 가격* 계산이 단일 JOIN 으로 끝
4. **분리는 후속 트리거** — 가격 이력·가격 변경 권한 분리·동시성 영역 분리가 *실제 요구* 가 되면 그때 B/C 로 마이그레이션 가능

### 영향

- **ERD (`docs/design/04-erd.md`)**: 테이블 1개
  ```
  daily_room
    PK: (room_type_id, date)        ← 복합 자연키
    total_rooms        INT NOT NULL
    reserved_rooms     INT NOT NULL DEFAULT 0
    price_per_night    BIGINT NOT NULL  (KRW)
    closed             BOOLEAN NOT NULL DEFAULT FALSE
    created_at, updated_at
  CHECK (reserved_rooms <= total_rooms)
  ```
- **클래스 다이어그램 (`docs/design/03-class-diagram.md`)**: `DailyRoom` 또는 `DailyRoomInventory` (이름은 후속 결정 — 가격까지 들었으니 `DailyRoom` 이 자연) Aggregate
- **검색 쿼리**: Property + RoomType + DailyRoom JOIN, `date BETWEEN checkIn AND checkOut - 1` 으로 박 단위 합산
- **차감 로직**: 예약 생성 시 *체크인\~체크아웃-1* 의 daily_room row 들에 `reserved_rooms += 1`. 한 row 안에서 *재고/가격 둘 다* 읽음 → 일관성 손쉬움

### 재검토 트리거
- **가격 이력 분석** 요구 등장 (호텔 측 가격 정책 감사, 사용자 가격 변동 알림)
- 재고 차감과 가격 변경이 *동시 동시성 영역* 으로 충돌 빈도 ↑
- 가격 변경 권한과 재고 변경 권한이 *조직적으로 분리* 됨 (요금 관리자 vs 객실 관리자)
- RatePlan 1급화 결정 (Booking.com 식 다중 요금제)

### 면접 답변 템플릿
> "어드민 API 가 inventory + price 를 한 묶음으로 받는 점, 시나리오상 본 라운드는 기능 동작까지가 범위 (동시성·검색 성능은 후속 라운드 도전) 인 점을 들어 `(room_type_id, date)` 복합키의 단일 `daily_room` 테이블로 시작했습니다. 가격 변경 권한 분리·가격 이력 분석·동시성 영역 분리가 실제 요구가 되면 Booking.com 식 두 테이블 분리로 마이그레이션 가능합니다."

### 출처
- 사용자 답변: 2026-05-28
- 빅테크 비교: [`01-domain-study.md` § 2.3 / § 2.4 / § 4](./01-domain-study.md#23-dailyroominventory-일자별-재고)
- 시나리오 원본: `docs/curriculum/round-2-scenario.md` 어드민 일자별 재고/요금 등록 요청 예시

---

## Q3. (2026-05-28) Property 와 RoomType 의 Aggregate 경계는?

**제안자**: Claude (Skill 3️⃣ 핵심 정책 결정 질문 4건 중 세 번째)

### 맥락
DDD Aggregate 경계 결정. Property 와 RoomType 의 관계(1:N)는 명확하나, 한 Aggregate 로 묶을지 분리할지가 클래스 다이어그램·Repository 구조·트랜잭션 경계를 좌우.

### 검토했던 선택지

| 선택지 | 모양 | 트레이드오프 |
|---|---|---|
| A. 별도 Aggregate Root | `PropertyRepository` + `RoomTypeRepository` 각자 | 가장 유연. 어드민 RoomType 단건 CRUD 가 자연. 일관성은 application 계층이 보장 |
| **B. Property Aggregate 가 RoomType 포함** | `PropertyRepository` 만, RoomType 컬렉션 동반 로드/저장 | 호텔 단위 묶음 일관성 강함. 어드민 RoomType 단건 CRUD 시 Property 경유 |
| C. 위 (b) + DailyRoom 만 분리 | Property + RoomType 한 묶음, DailyRoom 별도 | 절충 |

### Round 2 결정 — **B. Property Aggregate 가 RoomType 까지 포함**

**사용자 답변**: 2026-05-28 — "Property Aggregate 가 RoomType 까지 포함".

이유:
1. **불변식의 자연 위치** — "한 Property 안에 같은 이름의 RoomType 중복 불가" 같은 *호텔 단위 불변식* 이 Property Aggregate 안에 자연
2. **호텔 단위 묶음 사고** — 사용자가 "이 호텔" 단위로 인지·찜하듯, 시스템도 호텔 단위 일관성을 1급으로 다룸 (현실 모델 부합)
3. **삭제 cascade 자연** — 시나리오 "숙소 제거 시 해당 숙소의 객실 타입들도 삭제" 명시. Aggregate 한 묶음이면 자연
4. **DailyRoom 은 분리** (암묵적 — Q2 의 한 테이블 결정과 별개) — DailyRoom 은 변경 빈도가 극단적으로 높아 Property Aggregate 에 묶이면 성능 부담

### 영향

- **클래스 다이어그램 (`docs/design/03-class-diagram.md`)**:
  ```
  Property (AR)
    ├─ id, name, city, address, …
    ├─ checkInTime, checkOutTime
    ├─ cancellationPolicy
    └─ roomTypes: List<RoomType>        ← 내부 컬렉션
       └─ RoomType (entity, not AR)
           ├─ id, name
           ├─ standardGuestCount, maxGuestCount
           ├─ bedConfiguration
           └─ ...
  ```
- **Repository (`modules/domain`)**: `PropertyRepository` 1개. `RoomTypeRepository` 는 *없다* (또는 read-only 보조 인터페이스로만)
- **어드민 API 처리 (부수 결정)** — `POST /api-admin/v1/rooms`, `PUT /api-admin/v1/rooms/{id}` 가 *Property 를 경유* 해 처리:
  ```
  POST /api-admin/v1/rooms { propertyId, ... }
    → PropertyService.addRoomType(propertyId, command)
        → property = propertyRepository.findById(propertyId)
        → property.addRoomType(...)            ← 불변식 검증
        → propertyRepository.save(property)
  ```
- **DailyRoom 은 별도 Aggregate** — Property/RoomType 묶음과 분리. 차감 시 `dailyRoomRepository` 직접 조회/수정. 일관성은 *예약 트랜잭션* 이 보장
- **삭제 cascade** — Property 삭제 시 RoomType 자동 삭제는 JPA `cascade = CascadeType.ALL, orphanRemoval = true` 로 자연. DailyRoom 도 cascade 또는 별도 정리 트리거 결정 필요 (후속)
- **ERD (`docs/design/04-erd.md`)** — `property` ─< `room_type` (FK property_id, ON DELETE CASCADE) ─< `daily_room` (FK room_type_id) ─< `reservation` (FK room_type_id, 또는 reservation 스냅샷)

### 작은 부수 결정 — `RoomTypeRepository` 노출 여부

어드민 RoomType 단건 조회 (`GET /api-admin/v1/rooms/{id}`) 와 목록 (`GET /api-admin/v1/rooms?propertyId=...`) 이 *조회 전용* 으로 자주 호출. Aggregate 일관성과 무관한 read.

**잠정 권장**:
- *변경* 은 무조건 Property 경유 (`PropertyService.addRoomType / updateRoomType / removeRoomType`)
- *조회* 만 보조 `RoomTypeQueryRepository` (또는 `RoomTypeReader`) 노출 — CQRS 의 작은 적용

이 부수 결정은 ERD/클래스 다이어그램 작성 단계에서 확정.

### 재검토 트리거
- RoomType 단건 변경이 *Property 와 무관한 빈도* 로 폭증 (예: 가격·정책·이미지 자주 변경)
- RoomType 이 Property 와 *수명 주기 다름* 으로 판명 (예: RoomType 만 별도 운영팀 관리)
- 한 Property 의 RoomType 수가 수십 개 이상으로 늘어 컬렉션 로드 비용 ↑

### 면접 답변 템플릿
> "Property 와 RoomType 을 한 Aggregate 로 묶었습니다. (1) '같은 이름 RoomType 중복 금지' 같은 불변식이 호텔 단위에 자연, (2) 사용자도 시스템도 호텔 단위로 인지하는 도메인 특성, (3) 시나리오에 '숙소 삭제 시 객실 타입 cascade' 가 명시 — 세 가지가 한 묶음을 가리켰습니다. 다만 DailyRoom(일자별 재고·요금)은 변경 빈도가 극단적으로 높아 별도 Aggregate 로 두고, 어드민 RoomType 단건 조회는 보조 Query Repository 로 CQRS 의 작은 적용을 했습니다. 변경은 항상 Property 경유, 조회는 직접."

### 출처
- 사용자 답변: 2026-05-28
- 시나리오 원본: `docs/curriculum/round-2-scenario.md` — "숙소 제거 시 해당 숙소의 객실 타입들도 삭제" / 어드민 RoomType API 표

---

## Q4. (2026-05-28, **보류**) 더블부킹 — 이번 라운드 설계 문서에 어디까지 표현?

**제안자**: Claude (Skill 3️⃣ 핵심 정책 결정 질문 4건 중 네 번째)

### 맥락
시나리오 "나아가며" 가 *동시성·멱등성·일관성 = 모든 기능 동작 개발 후 도전* 으로 명시. 그렇다면 이번 라운드 *설계 문서* 에서 더블부킹 방지를 (a) 트랜잭션 경계만 표시, (b) `@Version` 낙관적 락까지 ERD/시퀀스에 반영, (c) `SELECT FOR UPDATE` 비관적 락까지 시퀀스에 명시 — 어느 깊이로 그릴지.

### 보류 사유
**사용자 답변 미수령** — 4건 묶음 중 본 항목만 응답 미포함. 

본격 설계 진입 (`docs/design/02-sequence-diagrams.md` 작성) 시점에 재질문. 그 시점이 *실제로 시퀀스를 그리며 결정* 하는 게 자연 — 추상적으로 미리 결정하기보단 다이어그램을 그려보며 결정.

### 잠정 디폴트 (재질문 전까지)
- 시퀀스에는 `@Transactional` 경계만 명시
- ERD 에는 `daily_room.reserved_rooms` 컬럼 + `CHECK (reserved_rooms <= total_rooms)` 만
- 동시성 전략 자체는 `docs/design/01-requirements.md` § Risk 에 *선택지* 형태로 (낙관/비관/unique 의 트레이드오프) 정리, *결정은 후속 라운드*

### 재질문 트리거
- `docs/design/02-sequence-diagrams.md` 예약 생성 시퀀스 그리는 시점
- 또는 사용자가 더 빨리 결정하고 싶을 때

### 출처
- Skill 3️⃣ 핵심 정책 결정 질문 4건 중 4번째 (답변 미수령)
- 시나리오 원본: `docs/curriculum/round-2-scenario.md` "나아가며" — 동시성은 후속 라운드 도전

---

## Q5. (2026-05-30) DailyRoom Aggregate 명명 — `DailyRoom` vs `DailyRoomInventory`

**제안자**: Claude (③ `03-class-diagram.md` 진입 전 결정 사안 C1)

### 맥락
시나리오 용어표는 *일자별 재고 (DailyRoomInventory)* 와 *일자별 요금 (DailyRoomRate)* 를 별도 객체로 정의. 그러나 Q2 / ADR-003 로 한 테이블 `daily_room` 통합 → 도메인 객체명도 통일 필요.

### 결정 — **`DailyRoom`**
- 시나리오 용어표 그대로 두면 *재고만* 의미가 부각, *가격까지 통합* 한 객체 의미 부정확
- `DailyRoom` 이 *(객실 타입, 날짜) 단위의 모든 정보* 의미 자연
- ADR-003 의 통합 테이블 결정과 일관

### 영향
- `03-class-diagram.md § 3` — `DailyRoom` 단일 Aggregate
- `04-erd.md § 2.7` — `daily_room` 테이블 → `DailyRoom` 도메인 객체 매핑
- 시퀀스 다이어그램의 `dailyRoom.consumeOne()` 등 — 그대로 일관

### 출처
- 사용자 답변: 2026-05-30 *"C1 - DailyRoom"*
- 관련: ADR-003, `docs/round-2/01-domain-study.md § 2.3, § 2.4`

---

## Q6. (2026-05-30) RoomType 단건 조회 — CQRS / 별도 RoomTypeReader 도입?

**제안자**: Claude (③ 결정 사안 C2)

### 맥락
Q3 결정으로 Property Aggregate 가 RoomType 포함. 어드민 API 의 *RoomType 단건 조회* (`GET /api-admin/v1/rooms/{id}`) 처리 시 매번 Property aggregate 전체 (모든 RoomType 컬렉션) 로드는 비효율 — *읽기 모델 분리* 고려 여지.

### 검토 선택지
- (a) **`RoomTypeReader` (CQRS 의 가벼운 적용)** — 변경=Property 경유, 조회만 별도 Reader 인터페이스
- (b) **`PropertyRepository` 단일 + `findByRoomTypeId(id): Property?` 보조 메서드** — 채택

### 결정 — (b) CQRS 도입 X
- **사용자 입장 (2026-05-30)** — *"난 CQRS 적용할 생각이 없는데"*
- Aggregate 일관성 단순 — Repository 1개로 변경·조회 모두 처리
- 학습 단계 인지 부담 ↓
- 단건 조회 시 형제 RoomType 도 함께 로드되는 비효율은 본 라운드 학습용엔 무관

### 영향
- `03-class-diagram.md § 6` — `PropertyRepository` 단일. `RoomTypeReader` 정의 안 함
- `01-requirements.md § 6 Q3` — 잠정 CQRS 표현 (2026-05-31 검수에서 정정됨)

### 재검토 트리거
- 단건 RoomType 조회 부하 급증 + 형제 RoomType 로드가 성능 병목으로 측정
- 후속 라운드에서 *읽기 모델 분리* 가 본격 도입될 때

### 출처
- 사용자 답변: 2026-05-30

---

## Q7. (2026-05-30) 예약 스냅샷 표현 — VO 묶음 vs 평탄 필드 vs 별도 Entity

**제안자**: Claude (③ 결정 사안 C3)

### 맥락
Reservation 에 *예약 시점 스냅샷* (cancellationPolicy, propertyName, roomTypeName, priceSnapshot) 저장 필요 (§ 9.4 시점 일관성). 표현 방식 선택.

### 검토 선택지
- (a) Reservation 에 평탄 필드 — 필드 15+, 정책 로직이 Reservation 누수 → 비대화
- (b) **VO 묶음** (`CancellationPolicySnapshot`, `PriceSnapshot`, `GuestInfo`) — 채택
- (c) 별도 Entity (`@Entity` + ID) — 불변 데이터에 ID 별도는 낭비, 과함

### 결정 — (b) VO 묶음
- Round 1 의 `@Embeddable` VO 패턴 답습
- 정책 데이터 + 계산 메서드 (`refundAmount`) 응집
- `Property.cancellationPolicy` (현재) vs `Reservation.cancellationPolicySnapshot` (예약 시점) **별도 타입 분리** — Round 1 Q6 (RawPassword/Password) 교훈 답습

### 영향
- `03-class-diagram.md § 4` — 스냅샷 4 종 VO 명시
- `04-erd.md § 2.9, § 2.10` — `reservation_price_entry`, `reservation_refund_rule` 별도 스냅샷 테이블

### 재검토 트리거
- 정책 종류 다양화 / 스냅샷 필드 수 너무 적어 VO 비용 > 가치 / 정책 로직이 *Reservation 외 다른 도메인* 에서 필요

### 출처
- 사용자 답변: 2026-05-30 *"잘 모르겠긴 한데 일단 VO 묶음으로?"*

---

## Q8. (2026-05-30) Wishlist 표현 — 단순 조인 엔티티 vs Wishlist Aggregate

**제안자**: Claude (③ 결정 사안 C4)

### 맥락
시나리오는 찜을 *숙소 단위* 단순 등록·취소. Airbnb 식 "여러 위시리스트 + 친구 공유" 같은 컬렉션 개념 없음.

### 결정 — **단순 조인 엔티티**
- `Wishlist (user_id, property_id, created_at)` 복합 PK
- DDD 관점에서 *연관의 사실* 만 표현. 도메인 메서드 없음
- `wishCount` 카운터 캐시는 Property 측

### 영향
- `03-class-diagram.md § 5` — Aggregate Root 가 아닌 단순 join entity
- `04-erd.md § 2.11` — `wishlist (user_id, property_id, created_at)` 테이블

### 출처
- 사용자 답변: 2026-05-30 *"단순 조인이 낫지 않나?"*
- 관련: `docs/round-2/01-domain-study.md § 2.5`

---

## Q9. (2026-05-30) 클래스 다이어그램 작성 깊이

**제안자**: Claude (③ 결정 사안 C5)

### 결정
**Aggregate Root + 핵심 entity·VO + 도메인 메서드 시그니처까지**. 보일러 (getter/setter, equals/hashCode) 생략.

### 영향
- `03-class-diagram.md` 의 모든 Mermaid `classDiagram` 블록이 이 깊이로 작성
- 빅테크 모범 사례 (`docs/round-2/04-bigtech-sequence-diagrams-research.md` § 6 *메서드는 핵심 도메인 메서드만*) 부합

### 출처
- 사용자 답변: 2026-05-30 *"응 잠정 디폴트대로"*

---

## Q10. (2026-05-30) E4 — VO 컬렉션 영속화 전략

**제안자**: Claude (④ `04-erd.md` 작성 중 결정 사안 E4)

### 맥락
5 VO 컬렉션 (Property.amenities, Property.cancellationPolicy.rules, RoomType.bedConfiguration.entries, Reservation.priceSnapshot.entries, Reservation.cancellationPolicySnapshot.rules) 의 영속화 방식. RDB 의 1NF 원칙상 *별도 테이블 vs JSON 컬럼* 중 선택.

### 검토 선택지
- (a) **별도 테이블 (`@ElementCollection + @CollectionTable`)** — 채택
- (b) JSON 컬럼 (`@JdbcTypeCode(SqlTypes.JSON)`) — 컬렉션을 한 컬럼에 직렬화
- (c) 혼합 (스냅샷만 별도, 나머지 JSON)

### 결정 — (a) Round 1 구조 일관성 유지
- **Round 1 패턴 일관** — 모든 단일 VO 는 `@Embeddable` + `@Embedded`. 본 라운드의 5 VO 컬렉션도 같은 패턴의 *자연 확장* (단일 → 컬렉션)
- JPA 표준 (`@ElementCollection`) — 추가 라이브러리 0
- 분석 쿼리 자연 — 향후 `amenity LIKE ...` 같은 확장 가능
- 스냅샷 *시점 일관성* 을 row 단위로 검증 가능

### 단점 (인지)
- 테이블 수 ↑ (5 보조 테이블)
- JPA `@ElementCollection` 의 변경 시 *delete-all + insert* — 본 라운드 컬렉션 변경 빈도 낮아 무관

### 영향
- `04-erd.md § 5` — 결정 사안 → 확정으로 갱신
- 11 테이블 구조 확정 (`property_amenity`, `refund_rule`, `bed_entry`, `reservation_price_entry`, `reservation_refund_rule` 보조)

### 재검토 트리거
- VO 컬렉션 *변경 빈도* 급증 → JSON 전환 검토
- 분석 쿼리 패턴이 완전히 사라짐 → JSON 간단성 이득

### 출처
- 사용자 답변: 2026-05-30 *"(i) Round 1 구조 유지 — 단일 VO는 @Embedded, 컴포지션은 별도 테이블 (@ElementCollection)"*

---

## Q11. (2026-05-30) Property 삭제 시 과거 예약 처리 — FK 정책

**제안자**: Claude (④ 작성 중 부수 결정)

### 맥락
시나리오 — *"숙소 제거 시 해당 숙소의 객실 타입들도 삭제"*. 그러나 과거 CHECKED_OUT/CANCELLED 예약이 *남아 있을 때* Property 삭제 어떻게?

### 검토 선택지
- (a) **모든 예약 (과거 포함) 있으면 삭제 불가 (FK RESTRICT)** — 채택
- (b) 예약.property_id SET NULL + 스냅샷 (`property_name_snapshot`) 으로 정보 보존
- (c) 별도 historical 테이블로 이관

### 결정 — (a) RESTRICT
- 단순. 학습용 적정
- 호텔 폐업 시나리오 도입 시 (b) SET NULL 패턴 재검토 — *부수 결정 트리거*

### 영향
- `04-erd.md § 4.1` — `reservation` 의 property_id/room_type_id/user_id 모두 `ON DELETE RESTRICT`
- `04-erd.md § 4.2` — Property 삭제 시퀀스 명시 (Service 단 사전 검증 → DB cascade)

### 출처
- 사용자 답변: 2026-05-30

---

> 새 질문은 아래에 `## Q12. (날짜) ...` 형식으로 추가. **질의자/제안자** 표기 잊지 말 것.
> 본격 설계 진입 시점에 Q4 재질문 + 취소 정책·검색 데이터 소스 등 후속 결정 누적.
