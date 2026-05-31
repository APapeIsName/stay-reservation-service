# 01 — 요구사항 명세 (Requirements)

**Status**: `Draft (Round 2)`
**Lifecycle**: `Draft → Review → Approved (Round 2 PR) → Superseded (Round 3+ 결제 도입 시)`

> **단일 폴더 증강형** — 본 문서는 라운드별 분리 없이 점차 증강된다 (`docs/design/README.md`).
> Round 2 가 첫 작성. 새 도메인·새 흐름 추가 시 적절한 섹션에 누적, 덮어쓰기 X.
>
> **본 문서의 책임** — 유저 시나리오 + 기능 정의 + 요구사항 명세 + 정책 결정 + 리스크.
> 시퀀스/클래스/ERD 는 `02-sequence-diagrams.md` / `03-class-diagram.md` / `04-erd.md`.

---

## 1. Round 2 범위

### 1.1 포함
- **도메인 6 개**: Property · RoomType · DailyRoom · Wishlist · Reservation (+ User 는 Round 1 완성, 본 라운드는 헤더 인증만 추가)
- **API 표면**: 시나리오 (`docs/curriculum/round-2-scenario.md`) 의 모든 endpoint (대고객 + 어드민)
- **예약 상태 머신**: `CONFIRMED → CHECKED_IN → CHECKED_OUT / CANCELLED` (이번 라운드 PENDING 미사용 — § 6 Q1)
- **산출물**: `docs/design/01-requirements.md` ~ `04-erd.md` 4 건

### 1.2 제외 / 후속 라운드 이월
| 항목 | 사유 | 이월 라운드 |
|---|---|---|
| Payment | 시나리오 명시 "추후 추가" | 차후 라운드 (PENDING 활용 자연 연결) |
| Coupon | 시나리오 명시 "추후 추가" | 차후 라운드 |
| 인증/인가 | 시나리오 명시 — 헤더 전달만 | 별도 라운드 |
| 동시성 (더블부킹) | 시나리오 "나아가며" — *기능 동작 완성 후* | 후속 도전 라운드 |
| 멱등성·일관성·외부 결제 장애 | 동상 | 후속 도전 라운드 |
| 검색 성능 (read model, 캐시) | 동상 | 후속 도전 라운드 |
| 대실 (시간 단위 객실) | 시나리오 외 | 시장 확장 시 |

---

## 2. 유비쿼터스 언어 (Ubiquitous Language)

### 2.1 시나리오 도메인 용어 (원본 그대로)

| 용어 | 영문 | 설명 |
|---|---|---|
| 숙소 | Property | 호텔/펜션/모텔/리조트 단위. 위치·편의시설·정책 보유 |
| 객실 타입 | RoomType | 한 숙소가 판매하는 객실 카테고리 ("스탠다드 더블", "오션뷰 스위트") |
| 일자별 재고 | DailyRoomInventory* | 특정 날짜의 객실 타입별 판매 가능 수량 |
| 일자별 요금 | DailyRoomRate* | 특정 날짜의 객실 타입별 1박 요금 |
| 찜 | Wishlist | 유저가 숙소를 찜한 기록 |
| 예약 | Reservation | 체크인\~체크아웃 기간 동안 특정 객실 타입을 점유하는 계약 |
| 결제 | Payment | 예약에 대한 PG 결제 정보 (본 라운드 제외) |
| 쿠폰 | Coupon | 예약 결제 시 적용되는 할인권 (본 라운드 제외) |

> \* 본 프로젝트는 § 6 Q2 결정에 따라 **인벤토리와 요금을 한 테이블** 로 통합. 도메인 객체명은 **`DailyRoom`** 으로 확정 (C1 결정 — `docs/round-2/03-questions.md` Q5, 2026-05-30).

### 2.2 시나리오에 없는 보조 객체 (설계상 필요)

| 용어 | 영문 | 책임 |
|---|---|---|
| 예약 기간 | DateRange / StayPeriod | `(checkIn, checkOut)` VO. 박 수 계산, 유효성 검증 |
| 예약자 정보 | GuestInfo | `(guestName, guestPhone, guestCount)` VO. 예약 스냅샷 |
| 취소 정책 | CancellationPolicy | 취소 시점별 환불 비율 (예: 체크인 7일 전 100% / 3일 전 50% / 당일 0%). Property 가 보유 |
| 가격 스냅샷 | PriceSnapshot | 예약 시점의 일자별 가격 + 합산 가격. Reservation 안에 임베디드 |
| 도시 | City | 검색용 도시 코드 (seoul / jeju 등). enum 또는 코드 |

---

## 3. 액터 + 외부 시스템

### 3.1 액터

| 액터 | 식별 헤더 | 권한 |
|---|---|---|
| 게스트 (비로그인) | — | 검색·상세·객실 조회 |
| 회원 (로그인) | `X-Loopers-LoginId`, `X-Loopers-LoginPw` | + 찜·예약·취소 |
| 어드민 | `X-Loopers-Ldap: loopers.admin` | 숙소·객실·재고·요금·예약 관리 |

> 인증 검증 로직은 본 라운드 미구현. 헤더 누락은 401, 잘못된 헤더는 403 으로 응답하는 *형식적 통과* 만.

### 3.2 외부 시스템

| 시스템 | 본 라운드 | 향후 |
|---|---|---|
| PG (결제) | 없음 | Payment 도입 시 (idempotency key 패턴) |
| Notification (SMS/메일) | 없음 | 예약 확정·취소·체크인 알림 |
| 검색 인덱스 (Elasticsearch 등) | 없음 (OLTP 직접 쿼리) | 검색 성능 라운드 |

---

## 4. 유저 시나리오 (자연어 흐름)

### 4.1 검색·발견

> 사용자는 여행 일정과 도시를 정하고, 어떤 숙소가 있는지 가격과 함께 둘러본다.

1. 사용자가 도시 `seoul`, 체크인 `2026-05-10`, 체크아웃 `2026-05-12`, 인원 `2` 로 검색
2. 시스템은 해당 도시의 *모든 Property* 중 *모든 객실 타입에 대해 max 인원 ≥ 2 + 체크인\~체크아웃-1 의 모든 날짜에 재고 ≥ 1* 인 것을 노출
3. 검색 결과에는 *해당 기간 합산 가격 (검색 기간 일자별 가격 합)* 표시
4. 사용자는 정렬 (`recommended` / `price_asc` / `rating_desc` / `wishes_desc`) 과 페이지 (`page`, `size`) 로 탐색
5. 마음에 드는 숙소 클릭 → 상세 화면 → 객실 타입 목록 → 객실 타입 상세

### 4.2 찜

> 사용자는 마음에 드는 숙소를 *날짜와 무관* 하게 모아둔다.

1. 로그인 사용자가 숙소 상세 화면에서 ❤️ 클릭 → `POST /api/v1/properties/{id}/wishes`
2. 이미 찜한 상태에서 다시 누르면 → `DELETE` (또는 토글 — 시나리오는 POST/DELETE 분리)
3. 사용자가 *내 찜 목록* 으로 보기 → `GET /api/v1/users/{userId}/wishes`
4. 찜은 *숙소 단위* (객실 타입 단위 X) — 사용자의 인지 단위에 맞춤

### 4.3 예약

> 사용자는 객실 타입을 정하고, 체크인/아웃 + 인원 + 예약자 정보로 즉시 예약을 확정한다 (본 라운드 결제 미포함).

1. 사용자가 객실 타입 상세에서 체크인/아웃 정함 → `POST /api/v1/reservations`
2. 요청 본문: `{propertyId, roomTypeId, checkIn, checkOut, guestCount, guestName, guestPhone}`
3. 시스템 처리 (단일 트랜잭션):
   - **검증** — RoomType 존재·displayStatus, guestCount ≤ maxGuestCount, checkOut > checkIn
   - **재고 확인** — 체크인\~체크아웃-1 의 모든 날짜에 daily_room 존재 + `total_rooms > reserved_rooms`
   - **재고 차감** — 해당 날짜들 `reserved_rooms += 1`
   - **가격 합산** — 해당 날짜들 `price_per_night` 합산 → totalPrice
   - **스냅샷 생성** — propertyName, roomTypeName, cancellationPolicy, priceSnapshot, guestInfo
   - **Reservation 생성** — status `CONFIRMED`, 위 스냅샷 함께
4. 응답: 생성된 Reservation 상세

#### Acceptance Criteria (Gherkin) — 핵심 흐름

**시나리오 1: 예약 생성 성공 (체크아웃 당일 미차감 검증)**

```gherkin
Given 5/10~5/12 "오션뷰 스위트" daily_room.total_rooms=1, reserved_rooms=0, price_per_night=100,000
And 로그인 사용자 user-1 (정상)
When user-1 이 propertyId=P1, roomTypeId=R1, checkIn=5/10, checkOut=5/12, guestCount=2 로
     POST /api/v1/reservations
Then HTTP 201, Reservation.status=CONFIRMED
And  Reservation.totalPrice == 200,000   # = 100,000 × 2박
And  daily_room(R1, 5/10).reserved_rooms == 1
And  daily_room(R1, 5/11).reserved_rooms == 1
And  daily_room(R1, 5/12).reserved_rooms == 0    # 체크아웃 당일 미차감
```

**시나리오 2: 최대 인원 초과 거절**

```gherkin
Given roomType R1 의 maxGuestCount=2
When  user-1 이 guestCount=3 으로 POST /api/v1/reservations
Then  HTTP 400, ErrorType=GUEST_COUNT_EXCEEDED
And   daily_room 의 reserved_rooms 변동 없음
```

**시나리오 3: 재고 부족 거절 (트랜잭션 롤백 검증)**

```gherkin
Given 5/10 daily_room.total_rooms=1, reserved_rooms=1   # 포화
And   5/11 daily_room.total_rooms=1, reserved_rooms=0   # 가용
When  user-1 이 checkIn=5/10, checkOut=5/12 로 POST /api/v1/reservations
Then  HTTP 409, ErrorType=ROOM_UNAVAILABLE
And   daily_room(R1, 5/11).reserved_rooms == 0    # 부분 차감 없음, 전체 rollback
```

**시나리오 4: 권한 없는 예약 조회 거절**

```gherkin
Given user-1 의 예약 R-100 존재
When  user-2 가 GET /api/v1/reservations/R-100
Then  HTTP 403, ErrorType=ACCESS_DENIED
```

### 4.4 취소

> 사용자가 예약을 취소하면 정책에 따라 환불액이 결정된다 (본 라운드는 환불액 *계산* 만, 실제 환불 처리 없음).

1. 사용자가 `POST /api/v1/reservations/{id}/cancel`
2. 시스템 처리:
   - **권한** — 예약의 userId == 요청자
   - **상태 확인** — `CONFIRMED` 또는 `CHECKED_IN` 만 취소 가능 (CHECKED_OUT/CANCELLED 는 거절)
   - **환불액 계산** — 예약 스냅샷의 cancellationPolicy + 현재 시점으로 환불액 산출 (실 환불은 미구현, 응답에만)
   - **재고 복원** — 해당 날짜들 `reserved_rooms -= 1`
   - **상태 전이** — `CANCELLED`, `cancelledAt` 기록
3. 응답: 환불 예정액 + 취소 완료 상태

### 4.5 어드민 — 숙소·객실 운영

> 어드민은 숙소·객실 타입을 등록·수정·삭제하고, 일자별 재고와 요금을 관리한다.

1. **Property CRUD** — 이름·위치·편의시설·정책 (`POST/PUT/DELETE /api-admin/v1/properties`)
2. **RoomType CRUD** — 기준/최대 인원·침대 구성 (`POST/PUT/DELETE /api-admin/v1/rooms`). 소속 Property 는 *수정 불가*. Property 삭제 시 cascade
3. **일자별 재고/요금 등록** — `PUT /api-admin/v1/rooms/{id}/inventory` 에 `{ranges: [{from, to, totalRooms, pricePerNight}, ...]}` 묶음. 시스템이 일자별로 펼침
4. **예약 조회** — 전체 예약 목록·상세 (`GET /api-admin/v1/reservations`)

---

## 5. 기능 명세 (API endpoint 별)

### 5.1 대고객 API (`/api/v1`)

| Method | URI | 인증 | 본 라운드 | 비고 |
|---|---|---|---|---|
| POST | `/api/v1/users` | X | (Round 1 완성) | |
| GET | `/api/v1/users/me` | O | (Round 1 완성) | |
| PUT | `/api/v1/users/password` | O | (Round 1 완성) | |
| GET | `/api/v1/properties/search` | X | ✅ | city + checkIn + checkOut + guests + sort + page + size |
| GET | `/api/v1/properties/{propertyId}` | X | ✅ | 숙소 상세 |
| GET | `/api/v1/properties/{propertyId}/rooms` | X | ✅ | 객실 타입 목록. 가용 여부는 *checkIn/checkOut 쿼리 파라미터* 가 있을 때만 계산 |
| GET | `/api/v1/properties/{propertyId}/rooms/{roomTypeId}` | X | ✅ | 객실 타입 상세 |
| POST | `/api/v1/properties/{propertyId}/wishes` | O | ✅ | 이미 찜이면 멱등 (재호출 OK) |
| DELETE | `/api/v1/properties/{propertyId}/wishes` | O | ✅ | 미찜 상태에서 호출도 멱등 |
| GET | `/api/v1/users/{userId}/wishes` | O | ✅ | 본인만. 타 유저 접근 시 403 |
| POST | `/api/v1/reservations` | O | ✅ | 즉시 CONFIRMED (§ 6 Q1) |
| GET | `/api/v1/reservations?startAt=&endAt=` | O | ✅ | 본인 예약만 |
| GET | `/api/v1/reservations/{reservationId}` | O | ✅ | 본인 예약만 |
| POST | `/api/v1/reservations/{reservationId}/cancel` | O | ✅ | 본인 예약만, 정책 기반 환불액 계산 (실 환불 미구현) |

### 5.2 어드민 API (`/api-admin/v1`)

| Method | URI | 본 라운드 | 비고 |
|---|---|---|---|
| GET | `/api-admin/v1/properties` | ✅ | 페이징 |
| GET | `/api-admin/v1/properties/{propertyId}` | ✅ | 상세 |
| POST | `/api-admin/v1/properties` | ✅ | 이름·위치·편의시설·정책 |
| PUT | `/api-admin/v1/properties/{propertyId}` | ✅ | 정보 수정 |
| DELETE | `/api-admin/v1/properties/{propertyId}` | ✅ | RoomType + DailyRoom cascade |
| GET | `/api-admin/v1/rooms?propertyId=` | ✅ | RoomType 목록 |
| GET | `/api-admin/v1/rooms/{roomTypeId}` | ✅ | RoomType 상세 |
| POST | `/api-admin/v1/rooms` | ✅ | propertyId 필수. Property 가 존재해야 |
| PUT | `/api-admin/v1/rooms/{roomTypeId}` | ✅ | 소속 Property 수정 불가 |
| DELETE | `/api-admin/v1/rooms/{roomTypeId}` | ✅ | DailyRoom cascade |
| PUT | `/api-admin/v1/rooms/{roomTypeId}/inventory` | ✅ | `{ranges: [...]}` 일자별 펼침 |
| GET | `/api-admin/v1/reservations` | ✅ | 페이징 |
| GET | `/api-admin/v1/reservations/{reservationId}` | ✅ | 상세 |

### 5.3 예약 검색 쿼리 파라미터 명세

| 파라미터 | 타입 | 필수 | 기본값 | 검증 |
|---|---|---|---|---|
| `city` | string | O | — | enum (seoul / jeju / ...) |
| `checkIn` | date (ISO) | O | — | `>= today` |
| `checkOut` | date (ISO) | O | — | `> checkIn` |
| `guests` | int | X | 2 | `>= 1` |
| `sort` | enum | X | `recommended` | recommended / price_asc / rating_desc / wishes_desc |
| `page` | int | X | 0 | `>= 0` |
| `size` | int | X | 20 | `1 <= size <= 100` |

---

## 6. 정책 결정 (questions.md Q1\~Q4)

본 절은 `docs/round-2/03-questions.md` 의 결정 요약. 상세 트레이드오프는 그쪽 참조.

### Q1. 예약 확정 시점 — **즉시 CONFIRMED**
- `POST /reservations` 가 검증·차감·CONFIRMED 까지 단일 트랜잭션
- PENDING 은 enum 자리만 정의, 본 라운드 미사용. 결제 도입 시 활용
- 한국 OTA 패턴 (야놀자/여기어때 = 결제 즉시 확정) 부합

### Q2. 일자별 재고·요금 — **한 테이블 `daily_room`**
- `(room_type_id, date)` 복합 자연키
- `total_rooms`, `reserved_rooms`, `price_per_night`, `closed`
- 어드민 API `PUT /rooms/{id}/inventory` 가 한 묶음으로 받으니 자연

### Q3. Property–RoomType Aggregate — **한 Aggregate (Property AR)**
- `PropertyRepository` 만, RoomType 컬렉션 동반 로드/저장
- 어드민 RoomType *변경·조회 모두 Property 경유* (`PropertyRepository.findByRoomTypeId` 보조 메서드). CQRS 도입 X — `docs/round-2/03-questions.md` Q6 (C2 결정, 2026-05-30)
- DailyRoom 은 *별도 Aggregate* (변경 빈도 극단적)

### Q4. 더블부킹 동시성 표현 — **보류 (잠정 디폴트)**
- 시퀀스에는 `@Transactional` 경계만
- ERD 에는 `reserved_rooms <= total_rooms` CHECK + 복합키만
- 동시성 전략(낙관/비관/unique)은 § 9 Risk 에 *선택지* 만 정리, 결정은 후속 라운드 또는 시퀀스 그릴 때 재질문

---

## 7. 예약 상태 머신

```
                  POST /reservations
                  (검증 + 차감 + 스냅샷)
                          │
                          ▼
                     [CONFIRMED] ◀────┐
                          │           │
        (관리자/배치)     │           │ (자동 전이는 본 라운드 미구현,
                          │           │  상태 자리만 정의)
                          ▼           │
                     [CHECKED_IN]     │
                          │           │
                          ▼           │
                    [CHECKED_OUT]     │
                                      │
   POST /cancel          ┌────────────┘
   (재고 복원 +          │
    환불액 계산)         │
                          ▼
                    [CANCELLED]


[PENDING]  ◇ enum 자리만 정의, 본 라운드 미사용 (결제 도입 시 활용)
```

> 👉 정식 Mermaid `stateDiagram-v2` 도식은 [`02-sequence-diagrams.md § 4 Reservation 상태 다이어그램`](./02-sequence-diagrams.md#4-reservation-상태-다이어그램--stripe-패턴) 참조 (Stripe PaymentIntent 패턴 — 시퀀스와 상태도의 짝).

### 전이 규칙

| from | to | 트리거 | 부수 효과 |
|---|---|---|---|
| (initial) | CONFIRMED | `POST /reservations` 성공 | 일자별 재고 차감 + 가격/정책 스냅샷 |
| CONFIRMED | CHECKED_IN | (본 라운드 미구현, 추후) | — |
| CHECKED_IN | CHECKED_OUT | (본 라운드 미구현, 추후) | — |
| CONFIRMED | CANCELLED | `POST /reservations/{id}/cancel` | 재고 복원 + 환불액 계산 |
| CHECKED_IN | CANCELLED | `POST /reservations/{id}/cancel` | 재고 복원 (해당 날짜) + 환불액 (정책 따라 적음) |
| CHECKED_OUT | (terminal) | — | 취소 불가 |
| CANCELLED | (terminal) | — | 재취소 불가 |

---

## 8. 비기능 요구사항

| 항목 | 결정 |
|---|---|
| 시간대 | KST (Asia/Seoul) 고정 |
| 통화 | KRW (정수, 소수점 없음) |
| 날짜 표현 | ISO `yyyy-MM-dd` (`LocalDate.parse` STRICT 모드) |
| 시각 표현 | ISO `yyyy-MM-dd'T'HH:mm:ss` (`LocalDateTime`) |
| 응답 형식 | 보존 골격 `ApiResponse<T>` envelope (Round 1) |
| 페이지 디폴트 | `page=0`, `size=20`, max `size=100` |
| 시점 일관성 | Reservation 은 *예약 시점* 의 propertyName / roomTypeName / cancellationPolicy / priceSnapshot 을 *스냅샷* 으로 보관 (Property 변경 후에도 예약 상세는 예약 당시 표시) |
| 응답 한국어 메시지 | `CoreException.message` 통과 (Round 1 패턴) |

### 학습 메트릭 — 본 라운드 완료 기준

본 프로젝트는 *학습·면접 자산* 성격이므로 비즈니스 KPI 대신 다음을 충족 기준으로 한다:

- ✅ Round 2 Checklist (§ 10) 7항목 모두 충족
- ✅ `docs/design/` 4 산출물 (`01-requirements.md` ~ `04-erd.md`) 완성
- ✅ `docs/round-2/03-questions.md` 정책 결정 누적 (Q1~Q4, 보류 포함)
- ✅ 모든 다이어그램이 *이유 → 다이어그램 → 해석* 3단 구조 (Skill 5️⃣ 6️⃣)
- ✅ 잠재 리스크가 *선택지* 형태로 제시 (Skill 7️⃣)
- ✅ PR 본문에서 본 폴더 4 파일을 직접 참조 가능

---

## 9. 잠재 리스크 (Risk) — *선택지 형태*

> Skill 7️⃣ 단계 — 현재 설계가 가질 수 있는 위험을 숨기지 않고, 해결책은 *선택지* 로.

### 9.1 더블부킹 (동시성) — **본 라운드 보류, 후속 도전**

| 선택지 | 모양 | 트레이드오프 |
|---|---|---|
| A. 낙관적 락 (`@Version`) | `daily_room.version` + Hibernate 자동 충돌 감지 | 단순. 충돌 시 사용자 재요청 필요. 학습 가치 ↑ |
| B. 비관적 락 (`SELECT FOR UPDATE`) | 차감 대상 row 들 트랜잭션 내 row lock | 안전. throughput ↓. 데드락 회피 (날짜 정렬 순) 필요 |
| C. DB unique constraint + 예약 점유 테이블 | `reservation_inventory_consumption (room_type_id, date, reservation_id)` UNIQUE | 가장 강력. 모델 복잡 |
| D. Redis 분산 락 | Redisson 등 | 외부 시스템 의존 추가. 본 프로젝트엔 과함 |

→ 본 라운드는 *위험 인지* 까지. 후속 라운드에서 본격 도입.

### 9.2 트랜잭션 비대화 — **본 라운드 단순 동거**

`POST /reservations` 의 단일 트랜잭션 안에:
1. RoomType 조회 (Property aggregate 로드 포함)
2. daily_room N 일치 조회·차감
3. Reservation INSERT
4. (추후) Payment 외부 호출

→ N 이 커지면 (장기 투숙 30일 등) 트랜잭션 시간 ↑ + (4) 도입 시 외부 호출 동안 락 보유.

**선택지**:
- A. 본 라운드는 단일 트랜잭션 유지, N 한계는 *허용 박 수 제한* (예: `nights <= 30`) 으로 회피
- B. 결제 도입 라운드에서 Saga / Outbox 패턴으로 분리

### 9.3 검색 성능 — **본 라운드 OLTP 직접 쿼리**

`/properties/search` 가 *N일치 daily_room 합산 + Property JOIN + 정렬* 을 모든 호출마다 수행. N=2 라도 (Property 100 × 2일 = 200 row) 정렬·집계.

**선택지**:
- A. 본 라운드 OLTP 직접 (인덱스 `(room_type_id, date)` 활용)
- B. Property 에 카운터 캐시 (wishCount, minPrice 등) 비정규화
- C. 후속 라운드 별도 read model (Elasticsearch / Materialized View)

### 9.4 정책 변경 영향 — **스냅샷으로 차단**

호텔이 cancellationPolicy 를 변경해도 *기존 예약* 은 *예약 시점 정책* 으로 환불 (§ 8 시점 일관성).

**위험**: 스냅샷 누락 시 모든 예약이 *최신 정책* 으로 계산되어 사용자 분쟁.

**방어**:
- 예약 생성 시 `cancellationPolicySnapshot` 필드 *반드시 채움*
- 테스트로 강제 (Property.cancellationPolicy 변경 후 기존 Reservation.refundAmount 가 변동하지 않음을 검증)

### 9.5 PG 일관성 — **본 라운드 결제 미도입으로 회피, 후속 도전**

결제 도입 시 *결제 성공 + 예약 미확정* 또는 *결제 실패 + 재고 점유* 발생 가능.

**선택지**:
- A. 결제 트랜잭션 안에 모든 것 (Q1 의 즉시 CONFIRMED 패턴 유지)
- B. PENDING 으로 임시 점유 + 결제 콜백 시 CONFIRMED (Booking/Airbnb 식)
- C. Outbox + 보상 트랜잭션

→ 결제 도입 라운드에서 본격 결정. 본 라운드 Q1 결정과 자연 연결.

---

## 10. Round 2 Checklist (시나리오 인용)

본 라운드 산출물 완성 시 *모두 충족* 되어야 함:

- [ ] 숙소 / 객실 타입 / 일자별 재고 / 일자별 요금 / 찜 / 예약 도메인 모두 포함 (`03-class-diagram.md` + `04-erd.md`)
- [ ] 기능 요구사항이 유저 중심 (체크인/아웃, 인원수, 도시) — § 4, § 5
- [ ] 시퀀스 다이어그램에서 책임 객체가 드러남 (`02-sequence-diagrams.md`)
- [ ] 예약 시퀀스가 *체크인\~체크아웃 사이 모든 일자별 재고 차감* 흐름 포함
- [ ] 클래스 구조가 도메인 설계를 잘 표현
- [ ] ERD 가 데이터 정합성 고려 (`(room_type_id, date)` 복합키, 인덱스)
- [ ] 예약 상태 머신이 ERD·시퀀스에 반영 (§ 7)

---

## 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-05-28 | 첫 작성 (Round 2) | Property·RoomType·DailyRoom·Wishlist·Reservation 6 도메인 요구사항 정리. Q1\~Q3 정책 결정 반영, Q4 보류 |
| 2026-05-28 | Status·Lifecycle 라벨 도입 / § 4.3 Acceptance Criteria(Gherkin) 4건 추가 / § 8 학습 메트릭 추가 | 빅테크 요구사항 정리 관행 리서치 결과 적용 (출처: `docs/round-2/02-bigtech-requirements-research.md`) |
| 2026-05-29 | § 7 상태 머신에 `02-sequence-diagrams.md § 4` Mermaid 도식 참조 링크 추가 | 빅테크 시퀀스 다이어그램 리서치 — Stripe 패턴 (시퀀스 + 상태도 짝) 적용 (출처: `docs/round-2/04-bigtech-sequence-diagrams-research.md`) |
| 2026-05-31 | § 2.1 footnote — DailyRoom 명명 확정 표기 / § 6 Q3 부수 결정 — CQRS 도입 X 로 갱신 | 전체 일관성 검수에서 C1 (DailyRoom 명명) / C2 (CQRS X) 결정 미반영 발견. `docs/round-2/03-questions.md` Q5/Q6 누적과 함께 정정 |

---

## 참고

- 본 라운드 학습 노트: `docs/round-2/01-domain-study.md`
- 정책 결정 누적: `docs/round-2/03-questions.md`
- 시나리오 원본: `docs/curriculum/round-2-scenario.md` (frozen)
- 과제 명세: `docs/curriculum/round-2-quest.md` (frozen)
- 운영 방침: `docs/design/README.md`
- 분석 도구: `.claude/skills/requirements-analysis/SKILL.md`
