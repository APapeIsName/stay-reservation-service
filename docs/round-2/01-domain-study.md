# Round 2 — 숙박 도메인 학습 정리

> **목적** — Round 2 본격 설계(`docs/design/01-requirements.md` 외 3건) 진입 전 도메인 이해를 정리한다.
> 이 문서는 *학습 노트* 이며 설계 산출물이 아니다. 결정·트레이드오프는 후속 단계 (`03-questions.md`, `docs/design/`) 에서 다룬다.
>
> **출처** — `docs/curriculum/round-2-scenario.md`, `docs/curriculum/round-2-quest.md` + 빅테크(Airbnb / Booking.com / 야놀자 / 여기어때) 사전 리서치.

---

## 1. 문제 상황 재정의 — "지금 어떤 문제를 풀고 있는가"

요구사항을 그대로 받아쓰지 않고, **왜 이런 요구사항이 등장했는지** 를 3관점으로 분해한다.

### 1.1 사용자 관점

> "도시·날짜·인원으로 마음에 드는 숙소를 빠르게 찾고, 가격을 미리 보고, 망설임 없이 잡고 싶다."

- **검색** 은 "구경" 모드 — 가격·위치·평점·찜수를 한 번에 비교
- **찜** 은 "이걸 나중에 다시 보겠다" 라는 인지적 북마크 (숙소 단위 — "이 호텔" 통째)
- **예약** 은 "확정된 미래의 자리" — 환불 정책에 따라 행동이 보수적/공격적으로 바뀜
- **취소** 는 "내 계획이 바뀌었다" — 위약금이 적을수록 충성도 ↑

### 1.2 비즈니스 관점

> "같은 객실이라도 날짜·시즌·요일에 따라 다른 상품처럼 팔린다. **공급(객실 수)** 과 **수요(예약 요청)** 가 *날짜 그리드* 위에서 만난다."

- 매출 = **(객실 단가 × 점유율 × 일수)** 의 합산
- 1실 더 못 팔면 그날 매출은 **영구 소실** (재고 유통기한 0). **더블부킹** 은 단순 버그가 아니라 *수익 + 신뢰* 동시 손실
- **취소 정책** 은 호텔 매출 보존 vs 사용자 자유의 trade-off. 정책의 *공정성* 자체가 마케팅 자산
- **검색 노출 = 매출** — 검색이 느리거나 가격이 부정확하면 이탈

### 1.3 시스템 관점

> "여러 사용자가 동시에 같은 (객실 타입, 날짜) 에 손을 뻗는다. **결제 외부 호출 / 일자별 재고 차감 / 예약 상태** 가 어긋나면 안 된다."

| # | 도전 | 본질 |
|---|---|---|
| 1 | 날짜 기반 재고 모델 | 같은 객실 타입이라도 5/10 과 5/11 은 **다른 SKU** |
| 2 | 더블부킹 방지 | 1실 남은 객실에 N 명 동시 결제 시도 — 동시성 |
| 3 | 검색 성능 | (도시, 날짜 N 일, 인원) 조합 검색이 빈번하지만 일자별 가격 합산은 **비싸다** |
| 4 | 예약 상태 머신 | `PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT / CANCELLED` — 각 전이가 *언제·왜* |
| 5 | PG 일관성 | 결제 성공/실패와 예약 확정/해제가 어긋나면 *돈은 빠졌는데 예약은 없다* |

> Round 2 본 라운드는 1·4 까지 설계 + 2·3·5 는 *후속 라운드 도전 과제* 로 명시 (시나리오 "나아가며" 섹션 인용).

---

## 2. 도메인 학습 — 시나리오 용어표 8 개

각 도메인마다 ① 정의 ② 핵심 속성 ③ 왜 이렇게 모델링하는가 ④ 빅테크 패턴 ⑤ 우리 시나리오 매핑.

### 2.1 Property (숙소)

**정의** — 물리적인 한 동(棟) 또는 한 부지 단위의 숙박 시설. "그랜드 하얏트 서울" 하나가 1 Property.

**핵심 속성**
- 식별: `id`, `name`
- 위치: `city` (seoul / jeju …), `address`, `(lat, lng)`
- 카테고리: `propertyType` (HOTEL / PENSION / MOTEL / RESORT)
- 편의시설: `amenities` (Wi-Fi, 주차장, 수영장, 조식 …)
- 정책: `checkInTime` (15:00), `checkOutTime` (11:00), `cancellationPolicy`
- 노출: `displayStatus`, `representativeImageUrl`

**왜 분리하나** — RoomType 과 분리하는 이유는 *위치·편의시설·정책* 은 객실이 바뀌어도 동일. 한 호텔에 30종 객실이 있어도 "주차 가능" 은 한 번만. **재사용 + 일관성**.

**빅테크**
- Booking.com — `Property → RoomType → RatePlan → RoomRate` 4단 계층 (호텔 도메인 표준)
- Airbnb — Property 개념 없음 (방 하나당 1 Listing, P2P 모델이라 평탄)
- → **우리 시나리오는 Booking 식**. RoomType 이 1급 도메인이라고 명시됨.

**우리 매핑** — `/api/v1/properties/search`, `/api-admin/v1/properties` 직접 대응. Aggregate Root 후보 1순위.

### 2.2 RoomType (객실 타입)

**정의** — 한 Property 안에서 *판매 가능한 객실 카테고리*. "스탠다드 더블", "오션뷰 스위트" 같은 *상품 라인업*. 같은 카테고리에 속한 실제 객실(101호, 102호…) 이 N 개 있다.

**핵심 속성**
- 식별: `id`, `propertyId`, `name` ("오션뷰 스위트")
- 수용: `standardGuestCount` (기준 인원, 보통 2), `maxGuestCount` (최대, 보통 4)
- 침대 구성: `bedConfiguration` ("퀸 1 + 싱글 1")
- 면적·뷰: `sizeSqm`, `viewType` (선택)
- 노출: `displayStatus`

**왜 분리하나** — 사용자가 *실제로 고르는 단위* 가 객실 타입이지 개별 호실 (101호) 이 아님. "오션뷰 스위트 1박" 만 결제하면 호텔이 알아서 비어 있는 호실을 배정. → **개별 호실은 모델링하지 않는다** (핵심 단순화).

**빅테크**
- Booking — RoomType + RatePlan 분리 (같은 객실 타입을 *환불불가 / 조식포함 / 기본* 등 여러 요금제로)
- Airbnb — RoomType 자체 없음 (Listing = 곧 방 한 칸)

**우리 매핑** — "기준 인원, 최대 인원, 침대 구성 포함" 명시. *예약 시 인원수가 최대 인원을 초과하면 안 됨* 검증 책임이 여기에 있음. RatePlan 은 시나리오에 없으니 도입하지 않음 (단순화).

### 2.3 DailyRoomInventory (일자별 재고)

**정의** — `(roomTypeId, date)` 단위로 *그날 팔 수 있는 객실 수*. **이 모델이 숙박 도메인의 심장**.

**핵심 속성**
- 식별: `(roomTypeId, date)` 복합 자연키 (또는 surrogate id + unique 인덱스)
- 수량: `totalRooms` (총 보유), `reservedRooms` (예약 점유), `availableRooms` (파생 또는 컬럼)
- 차단: `closed` (임시 휴실 등)

**왜 (roomTypeId, date) 단위인가** — 가장 핵심 질문.
- 같은 "오션뷰 스위트" 라도 *5/10* 의 재고와 *5/11* 의 재고는 **독립적으로 차감** 되어야 함
- 사용자 A가 5/10\~12 예약하면 5/10, 5/11 두 날짜의 inventory 가 동시에 줄어듦
- 사용자 B가 5/11\~13 예약 시도하면 5/11 inventory 만 충돌
- → *날짜를 1급 식별자로* 두지 않으면 "겹치는 기간" 계산이 매번 복잡한 range query 가 됨

**왜 체크아웃 당일은 차감 안 하나** — 체크아웃 당일은 *오전에 나가는 날* 이라 같은 날 다른 사람이 그날 체크인 가능. 즉 "투숙하는 밤(night)" 단위로 재고를 본다. 5/10\~5/12 예약은 *5/10 밤, 5/11 밤* = 2박 = 2일치 재고만 점유.

**빅테크**
- Booking — 정확히 `(property, room_type, date)` 그리드. 채널 매니저가 OTA 전체에 실시간 동기
- Airbnb — Listing × Calendar (RoomType 없으니 평탄)
- → **모두 `(상품단위 × 날짜)` 그리드**. 산업 표준.

**우리 매핑** — 어드민 API `PUT /api-admin/v1/rooms/{roomTypeId}/inventory` 가 `{ranges: [{from, to, totalRooms, ...}]}` 로 받아 일자별로 펼침. 더블부킹 방지의 1차 방어선.

### 2.4 DailyRoomRate (일자별 요금)

**정의** — `(roomTypeId, date)` 단위로 *그날 1박 요금*. 성수기·주말·특가에 따라 요동.

**핵심 속성** — `roomTypeId`, `date`, `pricePerNight` (KRW)

**왜 DailyRoomInventory 와 별도(또는 같이)** — 정책 선택 사안:
- (a) **한 테이블에 합치기** — `daily_room (room_type_id, date, total_rooms, reserved_rooms, price_per_night)`. 가장 단순. 어드민 API 가 inventory + price 를 한 번에 받으니 자연
- (b) **분리** — `daily_room_inventory` + `daily_room_rate`. 가격만 자주 바뀌고 재고는 거의 안 바뀌는 경우 유리. 동시성 충돌 영역 분리

→ 후속 결정 사안.

**왜 일자별인가** — "1박 평균 가격, 합산 가격" 을 검색 결과에 표시해야 하므로 *날짜별로 다를 수 있다는 전제* 가 필수. 평일/주말, 비수기/성수기 변동.

**빅테크** — 모두 일자별 가격. Booking 은 RatePlan 별로 가격이 또 나뉨. Airbnb 는 호스트 수동 설정 + Aerosolve(ML) 추천.

### 2.5 Wishlist (찜)

**정의** — *유저 × 숙소* 의 다대다 관계. 사용자가 "이 호텔 좋네" 라고 찜한 기록.

**핵심 속성** — `userId`, `propertyId`, `createdAt`

**왜 숙소 단위인가 (객실 타입 단위 X)** — 사용자의 인지가 "이 호텔 좋다" 수준에서 형성됨. 어떤 객실을 살지는 *날짜 정하고 결정*. 객실 타입 단위로 찜하면 너무 깊이 들어가야 함. → **UX 친화적 단순화**.

**엔티티 형태 후보**
- (a) 단순 조인 테이블 — `(user_id, property_id)` PK + `created_at`
- (b) Wishlist Aggregate — 향후 폴더링·메모 등 확장 대비 (Airbnb 의 "여행 위시리스트" 처럼 컬렉션 단위)

→ 시나리오는 컬렉션 개념 없음. 단순형이 자연.

**빅테크**
- Airbnb — Wishlist 가 *컬렉션* (여러 위시리스트, 친구와 공유, 협업) — 매우 풍부
- Booking — 단순 saved hotel
- 야놀자 / 여기어때 — 단순 찜
- → **우리는 시나리오상 단순형이면 충분**.

**검색 정렬과의 연결** — `sort=wishes_desc` (찜 많은 순) 가 검색 쿼리 파라미터에 있음. 즉 *property.wishCount* 라는 **카운터 캐시** 가 필요. 매번 COUNT 면 검색 느려짐.

### 2.6 Reservation (예약)

**정의** — *체크인\~체크아웃 기간 동안 특정 RoomType 1실을 점유* 하기로 한 계약. 시스템에서 가장 변화가 많은 객체 (상태 머신).

**핵심 속성**
- 식별: `id`, `userId`
- 스냅샷: `propertyId`, `roomTypeId` + *당시의 이름·정책·가격 스냅샷*
- 기간: `checkIn`, `checkOut`, `nights` (파생)
- 인원: `guestCount`, `guestName`, `guestPhone` (예약자 정보)
- 금액: `totalPrice` (일자별 가격 합산 스냅샷)
- 상태: `status` (PENDING / CONFIRMED / CHECKED_IN / CHECKED_OUT / CANCELLED)
- 정책: `cancellationPolicySnapshot`
- 시간: `createdAt`, `confirmedAt`, `cancelledAt` …

**왜 스냅샷이 중요한가** — 예약 후 호텔이 객실 이름을 바꿔도 *내 예약 상세* 에는 예약 당시 본 그대로가 떠야 함. 정책도 마찬가지 (취소 정책이 바뀌어도 *예약 시점 정책* 적용). → **시점 일관성**.

**상태 머신** — 시나리오 명시:

```
PENDING ─┬─→ CONFIRMED ──→ CHECKED_IN ──→ CHECKED_OUT
         │       │
         │       └─→ CANCELLED   (체크인 전 취소, 정책에 따라 환불)
         │
         └─→ CANCELLED            (PENDING 단계 취소 — 결제 미완 등)
```

- **PENDING** — 결제 진행 중 (PG 응답 대기)
- **CONFIRMED** — 결제 완료 → 객실 확보 확정
- **CHECKED_IN / CHECKED_OUT** — 실제 투숙 (관리자 또는 시스템 자동 전이)
- **CANCELLED** — 취소. 환불액은 *취소 정책 + 취소 시점* 에 따라

**예약 생성 시 보장되어야 할 동작** (시나리오 명시)
1. 체크인\~체크아웃 사이 모든 날짜 일자별 재고 차감 (체크아웃 당일 X)
2. 더블부킹 방지
3. 인원수 ≤ 최대 인원

**빅테크**
- Airbnb — PENDING (호스트 24h 승인 대기) → CONFIRMED. 결제 = Pre/RPC/Post DAG (idempotency)
- Booking — 즉시 확정 일반적
- 야놀자 / 여기어때 — 즉시 확정 일반적 ("결제 즉시 100% 예약 확정")
- → **호스트 승인이 있냐 없냐** 에 따라 PENDING 의미가 달라짐. 우리는 호스트 승인 흐름 없음 → *결제 진행 중 = PENDING* 정도로 해석.

### 2.7 Payment (결제) — *추후 추가*

**정의** — Reservation 에 대한 PG 결제 정보. 외부 시스템 호출 결과를 보관.

**핵심 속성** — `id`, `reservationId`, `amount`, `pgProvider`, `pgTxId`, `status`, `idempotencyKey`, `paidAt`

**왜 별도 객체인가** — 결제는 *외부 호출* + *재시도 가능* + *부분 환불 등 다건* 발생 가능. Reservation 1:N Payment 가 미래 확장에 자연.

**시나리오** — 이번 라운드 *추후 추가*. 설계 문서엔 자리만 표시, 본격 모델링은 다음 라운드.

### 2.8 Coupon (쿠폰) — *추후 추가*

**정의** — 예약 결제 시 적용되는 할인권.

이번 라운드 out-of-scope. ERD / 클래스 도면에 *향후 확장 자리* 만 표시.

---

## 3. 도메인 관계 한눈에

```
User ──(1:N)── Wishlist ──(N:1)── Property ──(1:N)── RoomType
                                                          │
                                                          ├─(1:N)── DailyRoomInventory
                                                          └─(1:N)── DailyRoomRate

User ──(1:N)── Reservation ──(N:1)── RoomType
                  │
                  ├─(스냅샷) GuestInfo · CancellationPolicy · totalPrice
                  └─(1:N) Payment  (추후 라운드)
```

### Aggregate 경계 후보 (잠정)

| Aggregate Root | 포함 | 분리 이유 |
|---|---|---|
| **Property** | RoomType 포함? 또는 별도? | RoomType 의 변경 빈도·수명을 보고 결정 |
| **DailyRoomInventory** | 단독 | 변경 빈도 매우 높음·동시성 영역 |
| **DailyRoomRate** | 단독 또는 Inventory 와 통합 | 후속 결정 |
| **Wishlist** | 단독 (조인 엔티티) | 단순 |
| **Reservation** | GuestInfo · CancellationPolicySnapshot 포함 | 시점 일관성 |
| **Payment** | 단독 (Reservation 참조) | 추후 라운드 |

---

## 4. 빅테크 비교 요약 (도메인별 핵심만)

| 도메인 | Airbnb | Booking.com | 야놀자/여기어때 (추정) |
|---|---|---|---|
| Property/RoomType | Listing 평탄화 (RoomType 없음) | RoomType 1급 + RatePlan 조합 | 호텔은 RoomType, 모텔/펜션은 단일 상품 혼재 |
| Daily Inventory | Listing × date Calendar. Reservation 과 같은 DB (ACID) | `(property, room_type, date)` 그리드. CMS 가 채널 실시간 동기 | 결제 즉시 확정 → 인벤토리·결제 단일 트랜잭션 |
| Reservation 상태 | PENDING (호스트 24h) → CONFIRMED → CHECKED_IN → CHECKED_OUT, EXPIRED/DECLINED/CANCELLED | 즉시 확정. NO_SHOW 별도 | 한국 OTA = 즉시 확정 |
| 검색 | scatter-gather + Elasticsearch + Embedding-Based Retrieval | Availability Search Engine + Ranking Platform | MongoDB 키워드 허브 + Elasticsearch (여기어때) |
| Cancellation | 정책 타입 (Flexible/Moderate/Strict) Listing 에 binding | RoomRate 레벨 policy_id + Prepayment 분리 | 비공개 |
| Payment | Orpheus (idempotency + row lock + Pre/RPC/Post DAG) | OTA 표준. 호텔 직접 수금 / Booking 대납 둘 다 | 결제정산 팀 별도, 예약 강결합 (추정) |

### 산업 공통 패턴 5가지
1. `(객실단위, 날짜)` **그리드 인벤토리** — 모두 채택
2. **검색 캐시 vs 예약 시점 SoR 분리** — "검색은 fast & approximate, 예약은 exact & transactional"
3. **Idempotency key 패턴** — double-booking·double-payment 방지 핵심
4. **정책 객체로 분리** — Cancellation / Restriction 을 별도 카탈로그 객체로
5. **검색은 별도 read-optimized 스토어** — OLTP 는 SoR, 검색은 ES 등

### 우리 프로젝트에 적용 가능 (Agent 정리 기준)

| 채택 강도 | 항목 |
|---|---|
| 🟢 즉시 채택 | `(property_id, room_type_id, date)` 복합키 / Daily Inventory + Reservation 같은 DB·트랜잭션 / Cancellation Policy 별도 객체 / Reservation enum + sealed class 상태 머신 / 멀티모듈 경계 |
| 🟡 조건부 | RatePlan 1급화 (호텔 위주면) / Derived KV (Redis 충분) |
| 🔴 과함 | Orpheus 수준 자체 idempotency 프레임워크 / Lambda 아키텍처 / ML 동적 가격 / Embedding 검색 / Payment product-agnostic 완전 분리 |
| 🇰🇷 한국 특화 | 대실 → `Stay Period` VO 로 추상화하면 추후 확장 쉬움 / 결제 즉시 확정 패턴 자연 |

---

## 5. 이번 라운드 범위·제외 명세

### 5.1 포함 (Round 2 Design)
- 6 개 핵심 도메인: Property / RoomType / DailyRoomInventory / DailyRoomRate / Wishlist / Reservation
- API 표면: 시나리오 `✅ 요구사항` 표 전체 (Users 제외, 회원은 Round 1 완성)
- 어드민 API: Property / RoomType CRUD + 일자별 재고·요금 등록
- 예약 상태 머신 (PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT / CANCELLED)
- 산출물: `docs/design/01-requirements.md` ~ `04-erd.md` 4개

### 5.2 제외 / 차회 이월
- **Payment** — 시나리오 명시 "추후 추가" → 자리만 표시
- **Coupon** — 시나리오 명시 "추후 추가"
- **동시성 (더블부킹), 멱등성, 검색 성능, PG 장애 대응** — 시나리오 "나아가며" 명시 *후속 라운드*
- **인증/인가** — 헤더 전달 (`X-Loopers-LoginId` / `X-Loopers-Ldap`) 만, 검증 로직 없음

### 5.3 Round 2 Checklist (시나리오 인용)
- [ ] 숙소 / 객실 타입 / 일자별 재고 / 일자별 요금 / 찜 / 예약 도메인 모두 포함
- [ ] 기능 요구사항이 유저 중심 (체크인/아웃, 인원수, 도시 등)
- [ ] 시퀀스 다이어그램에서 책임 객체가 드러남
- [ ] 예약 시퀀스가 *체크인\~체크아웃 사이 모든 일자별 재고 차감* 흐름 포함
- [ ] 클래스 구조가 도메인 설계를 잘 표현
- [ ] ERD 가 데이터 정합성 고려 (`(room_type_id, date)` 복합키, 인덱스 등)
- [ ] 예약 상태 머신이 ERD·시퀀스에 반영

---

## 6. 결정해야 할 것들 (질문은 별도 단계)

본 문서에서는 *목록* 만. 실제 질문·답변·결정은 `docs/round-2/03-questions.md` 에 누적 (Round 1 패턴).

### 6.1 정책 (Policy)
- 예약 확정 시점 — 결제 추후 추가 상황에서 즉시 CONFIRMED 인가, PENDING 단계 두는가
- 취소 정책 — 체크인 N일 전 환불 비율 룰 구체
- 인원수 초과 시 — 단순 거절? 추가 요금?
- 객실 가용 여부 조회 — 어떤 날짜 기준?

### 6.2 경계 (Boundary)
- DailyRoomInventory vs DailyRoomRate 통합 / 분리
- 재고 차감 책임 — Reservation 도메인 vs 별도 InventoryService
- 검색 데이터 소스 — OLTP 직접 vs read model
- Property aggregate 범위 — RoomType 포함 / 분리

### 6.3 확장 (Future)
- 결제 도입 시점 — 시퀀스에 점선으로 그릴까, 완전히 뺄까
- 쿠폰 — ERD 자리만 비울까, 무시할까
- 대실 가능성 — `Stay Period` VO 추상화 여부

---

## 7. 다음 단계 (예정)

| 순서 | 단계 | 산출물 |
|---|---|---|
| ① | *현재* — 본 학습 정리 | `docs/round-2/01-domain-study.md` |
| ② | 정책·경계·확장 결정 질문 (사용자 답변) | `docs/round-2/03-questions.md` (질의자/제안자 표기 누적) |
| ③ | 요구사항·정책 정리 | `docs/design/01-requirements.md` |
| ④ | 시퀀스 다이어그램 (검색 / 예약 생성 / 예약 취소) | `docs/design/02-sequence-diagrams.md` |
| ⑤ | 클래스 다이어그램 (도메인 책임·의존 방향) | `docs/design/03-class-diagram.md` |
| ⑥ | ERD (복합키·인덱스·관계의 주인) | `docs/design/04-erd.md` |
| ⑦ | 잠재 리스크 (동시성·일관성·트랜잭션 비대화) — *선택지* 형태 | `docs/design/01-requirements.md` 의 § Risk |

---

## 참고

- 시나리오 원본 — `docs/curriculum/round-2-scenario.md` (frozen)
- 과제 명세 — `docs/curriculum/round-2-quest.md` (frozen)
- 설계 폴더 운영 정책 — `docs/design/README.md`
- 분석 도구 — `.claude/skills/requirements-analysis/SKILL.md`
