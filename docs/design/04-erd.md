# 04 — ERD (LLD)

**Status**: `Draft (Round 2)`
**Lifecycle**: `Draft → Review → Approved (Round 2 PR) → Superseded (Round 3+ 결제·이벤트 도입 시)`

> **단일 폴더 증강형** — 라운드별 분리 없이 점차 증강 (`docs/design/README.md`).
> 본 문서는 **LLD 본체** 의 다른 축 — *영속 구조 (테이블·관계·인덱스·제약)*. 클래스 다이어그램 (`03-class-diagram.md`) 의 *영속 매핑*.
>
> **본 문서의 책임** — 테이블 스키마·복합키·인덱스·외래키·cascade·CHECK 제약.
> 도메인 객체 책임은 `03-class-diagram.md`, 런타임 흐름은 `02-sequence-diagrams.md`.

---

## 운영 방침

- 한 테이블 = 한 도메인 객체 (Entity) 또는 *VO 컬렉션의 영속화*
- *Aggregate 간 참조* 는 FK + `ON DELETE RESTRICT` (DB 무결성 + 도메인 안전성)
- *Aggregate 내부* 는 FK + `ON DELETE CASCADE` (cascade 자연)
- 복합키는 `@EmbeddedId` (E1·E2 잠정) — Kotlin `data class` 친화
- *VO 안의 컬렉션* 영속화는 § 5 의 E4 결정 사안
- 변경 이력 누적

---

## 0. 목차

| # | 섹션 | 내용 |
|---|---|---|
| 1 | 전체 ERD | 11 테이블 한눈에 |
| 2 | 테이블별 상세 | 도메인 → 테이블 매핑 + 인덱스 + 제약 |
| 3 | 인덱스 전략 | 검색·예약 조회 패턴 기반 |
| 4 | Cascade · FK 정책 | Aggregate 경계와 DB 무결성 |
| 5 | **E4 결정 사안** — VO 컬렉션 영속화 | 별도 테이블 / JSON 컬럼 / 혼합 |

---

## 1. 전체 ERD

```mermaid
erDiagram
    USERS ||--o{ WISHLIST : "has"
    USERS ||--o{ RESERVATION : "makes"

    PROPERTY ||--o{ ROOM_TYPE : "owns"
    PROPERTY ||--o{ PROPERTY_AMENITY : "has"
    PROPERTY ||--o{ REFUND_RULE : "has policy"
    PROPERTY ||--o{ WISHLIST : "wished"
    PROPERTY ||--o{ RESERVATION : "booked"

    ROOM_TYPE ||--o{ BED_ENTRY : "has"
    ROOM_TYPE ||--o{ DAILY_ROOM : "has"
    ROOM_TYPE ||--o{ RESERVATION : "selected"

    RESERVATION ||--o{ RESERVATION_PRICE_ENTRY : "snapshot"
    RESERVATION ||--o{ RESERVATION_REFUND_RULE : "snapshot"

    USERS {
        bigint id PK
        varchar login_id UK
        varchar password_hash
        varchar name
        varchar email
        varchar phone_number
        date birth_date
        timestamp created_at
        timestamp updated_at
    }

    PROPERTY {
        bigint id PK
        varchar name
        varchar city "INDEX"
        varchar detail_address
        varchar zip_code
        varchar property_type
        time check_in_time
        time check_out_time
        varchar representative_image_url
        varchar display_status "INDEX"
        bigint wish_count "default 0"
        timestamp created_at
        timestamp updated_at
    }

    PROPERTY_AMENITY {
        bigint property_id PK_FK
        varchar amenity PK
    }

    REFUND_RULE {
        bigint property_id PK_FK
        int days_before_check_in PK
        int refund_rate "0~100"
    }

    ROOM_TYPE {
        bigint id PK
        bigint property_id FK "INDEX"
        varchar name
        int standard_guest_count
        int max_guest_count
        int size_sqm "nullable"
        varchar view_type "nullable"
        varchar display_status
        timestamp created_at
        timestamp updated_at
    }

    BED_ENTRY {
        bigint room_type_id PK_FK
        varchar bed_type PK
        int count
    }

    DAILY_ROOM {
        bigint room_type_id PK_FK
        date date PK
        int total_rooms
        int reserved_rooms "default 0, CHECK"
        bigint price_per_night
        boolean closed "default false"
        timestamp created_at
        timestamp updated_at
    }

    RESERVATION {
        bigint id PK
        bigint user_id FK "INDEX(user_id, created_at)"
        bigint property_id FK
        bigint room_type_id FK
        date check_in
        date check_out
        varchar guest_name
        varchar guest_phone
        int guest_count
        bigint total_price
        varchar property_name_snapshot
        varchar room_type_name_snapshot
        varchar status "INDEX(status)"
        timestamp created_at "INDEX desc"
        timestamp confirmed_at "nullable"
        timestamp cancelled_at "nullable"
    }

    RESERVATION_PRICE_ENTRY {
        bigint reservation_id PK_FK
        date date PK
        bigint price_per_night
    }

    RESERVATION_REFUND_RULE {
        bigint reservation_id PK_FK
        int days_before_check_in PK
        int refund_rate
    }

    WISHLIST {
        bigint user_id PK_FK
        bigint property_id PK_FK
        timestamp created_at "INDEX(user_id, created_at desc)"
    }
```

### 해석 — 봐야 할 포인트

- **5 Aggregate Root + 6 보조 테이블 (VO 컬렉션·조인)** — 도메인 객체와 1:1 정합
- **DAILY_ROOM 의 복합 PK `(room_type_id, date)`** — ADR-003 의 직접 표현. 산업 표준
- **RESERVATION 의 스냅샷 분리** — `reservation_price_entry` + `reservation_refund_rule` 두 보조 테이블 (잠정 디폴트 — § 5 E4 결정 미확정)
- **WISHLIST 단순 조인** — C4 결정. 메서드 없는 단순 fact
- **모든 FK 에 인덱스** (PK 가 일부이거나 별도) — 검색·조회 성능 기본

---

## 2. 테이블별 상세

### 2.1 `users` (Round 1 완성, 본 라운드 참조만)

| 컬럼 | 타입 | 제약 | 매핑 |
|---|---|---|---|
| id | BIGINT | PK, AI | `User.id` |
| login_id | VARCHAR(20) | UNIQUE NOT NULL | `User.loginId.value` |
| password_hash | VARCHAR(60) | NOT NULL | `User.password.hashedValue` (BCrypt) |
| name | VARCHAR(50) | NOT NULL | `User.name.value` |
| email | VARCHAR(100) | NOT NULL | `User.email.value` |
| phone_number | VARCHAR(13) | NOT NULL | `User.phoneNumber.value` |
| birth_date | DATE | NOT NULL | `User.birthDate.value` |
| created_at, updated_at | TIMESTAMP | NOT NULL | BaseEntity |

> 본 라운드는 *추가 변경 없음*. 정확한 컬럼은 Round 1 의 `UserJpaRepository` 매핑 기준 (이 표는 개요).

### 2.2 `property` — Property Aggregate Root

| 컬럼 | 타입 | 제약 | 매핑 |
|---|---|---|---|
| id | BIGINT | PK, AI | `Property.id` |
| name | VARCHAR(100) | NOT NULL | `Property.name` |
| city | VARCHAR(20) | NOT NULL, **INDEX** | `Property.city` (enum 코드) |
| detail_address | VARCHAR(200) | NOT NULL | `Property.address.detailAddress` |
| zip_code | VARCHAR(10) | NOT NULL | `Property.address.zipCode` |
| property_type | VARCHAR(20) | NOT NULL | `Property.propertyType` (enum) |
| check_in_time | TIME | NOT NULL | `Property.checkInTime` |
| check_out_time | TIME | NOT NULL | `Property.checkOutTime` |
| representative_image_url | VARCHAR(500) | NULL | `Property.representativeImageUrl` |
| display_status | VARCHAR(20) | NOT NULL, **INDEX** | `Property.displayStatus` |
| wish_count | BIGINT | NOT NULL DEFAULT 0 | `Property.wishCount` (카운터 캐시) |
| created_at, updated_at | TIMESTAMP | NOT NULL | BaseEntity |

**인덱스**: `(city)`, `(display_status)`, `(city, display_status)` 복합 (검색 1차 필터)

### 2.3 `property_amenity` — Property.amenities 컬렉션

| 컬럼 | 타입 | 제약 |
|---|---|---|
| property_id | BIGINT | PK, FK → property(id) ON DELETE CASCADE |
| amenity | VARCHAR(20) | PK (enum 코드) |

> JPA 매핑: `@ElementCollection` + `@CollectionTable(name = "property_amenity")`

### 2.4 `refund_rule` — Property.cancellationPolicy.rules (현재 정책)

| 컬럼 | 타입 | 제약 |
|---|---|---|
| property_id | BIGINT | PK, FK → property(id) ON DELETE CASCADE |
| days_before_check_in | INT | PK (예: 7, 3, 1, 0) |
| refund_rate | INT | NOT NULL, CHECK (0\~100) |

> 정책 예시: `(7, 100), (3, 50), (1, 20), (0, 0)` — 체크인 7일 전 100% 환불, 3일 전 50%, …
> **이름 의도** — Property 의 *현재* 정책. Reservation 의 *스냅샷* 은 별도 `reservation_refund_rule` (§ 2.10)

### 2.5 `room_type` — Property Aggregate 내부 Entity

| 컬럼 | 타입 | 제약 | 매핑 |
|---|---|---|---|
| id | BIGINT | PK, AI | `RoomType.id` |
| property_id | BIGINT | NOT NULL, FK → property(id) ON DELETE CASCADE, **INDEX** | (Property aggregate boundary) |
| name | VARCHAR(50) | NOT NULL | `RoomType.name` |
| standard_guest_count | INT | NOT NULL | `RoomType.standardGuestCount` |
| max_guest_count | INT | NOT NULL | `RoomType.maxGuestCount` |
| size_sqm | INT | NULL | `RoomType.sizeSqm` |
| view_type | VARCHAR(20) | NULL | `RoomType.viewType` |
| display_status | VARCHAR(20) | NOT NULL | `RoomType.displayStatus` |
| created_at, updated_at | TIMESTAMP | NOT NULL | BaseEntity |

**인덱스**: `(property_id)` — Property 경유 조회 (C2 의 `findByRoomTypeId` 도 이 인덱스 활용)

### 2.6 `bed_entry` — RoomType.bedConfiguration.entries 컬렉션

| 컬럼 | 타입 | 제약 |
|---|---|---|
| room_type_id | BIGINT | PK, FK → room_type(id) ON DELETE CASCADE |
| bed_type | VARCHAR(20) | PK (enum 코드) |
| count | INT | NOT NULL |

### 2.7 `daily_room` — DailyRoom Aggregate Root (ADR-003)

| 컬럼 | 타입 | 제약 | 매핑 |
|---|---|---|---|
| room_type_id | BIGINT | PK, FK → room_type(id) ON DELETE CASCADE | `DailyRoom.roomTypeId` |
| date | DATE | PK | `DailyRoom.date` |
| total_rooms | INT | NOT NULL | `DailyRoom.totalRooms` |
| reserved_rooms | INT | NOT NULL DEFAULT 0, **CHECK (0 ≤ reserved_rooms ≤ total_rooms)** | `DailyRoom.reservedRooms` |
| price_per_night | BIGINT | NOT NULL | `DailyRoom.pricePerNight` |
| closed | BOOLEAN | NOT NULL DEFAULT FALSE | `DailyRoom.closed` |
| created_at, updated_at | TIMESTAMP | NOT NULL | BaseEntity |

**인덱스**: PK `(room_type_id, date)` 자체. 추가로 `(date)` 만의 인덱스는 검색 패턴 *(여러 room_type 의 같은 date 조회)* 필요시 — 우리 검색은 `room_type_ids IN (...) AND date BETWEEN ...` 이므로 PK 의 첫 컬럼이 `room_type_id` 라 *역방향 인덱스* 가치 검토 필요

**CHECK 제약** — 도메인 메서드 `consumeOne()` 도 검증, DB CHECK 도 검증 → 이중 방어층

### 2.8 `reservation` — Reservation Aggregate Root

| 컬럼 | 타입 | 제약 | 매핑 |
|---|---|---|---|
| id | BIGINT | PK, AI | `Reservation.id` |
| user_id | BIGINT | NOT NULL, FK → users(id) ON DELETE RESTRICT, **INDEX** | `Reservation.userId` |
| property_id | BIGINT | NOT NULL, FK → property(id) ON DELETE RESTRICT | `Reservation.propertyId` |
| room_type_id | BIGINT | NOT NULL, FK → room_type(id) ON DELETE RESTRICT | `Reservation.roomTypeId` |
| check_in | DATE | NOT NULL | `Reservation.period.checkIn` |
| check_out | DATE | NOT NULL, CHECK (check_out > check_in) | `Reservation.period.checkOut` |
| guest_name | VARCHAR(50) | NOT NULL | `Reservation.guestInfo.guestName.value` |
| guest_phone | VARCHAR(13) | NOT NULL | `Reservation.guestInfo.guestPhone.value` |
| guest_count | INT | NOT NULL | `Reservation.guestInfo.guestCount` |
| total_price | BIGINT | NOT NULL | `Reservation.totalPrice` |
| property_name_snapshot | VARCHAR(100) | NOT NULL | `Reservation.propertyNameSnapshot` |
| room_type_name_snapshot | VARCHAR(50) | NOT NULL | `Reservation.roomTypeNameSnapshot` |
| status | VARCHAR(20) | NOT NULL, **INDEX** | `Reservation.status` (enum) |
| created_at | TIMESTAMP | NOT NULL | `Reservation.createdAt` |
| confirmed_at | TIMESTAMP | NULL | `Reservation.confirmedAt` |
| cancelled_at | TIMESTAMP | NULL | `Reservation.cancelledAt` |

**인덱스**:
- `(user_id, created_at DESC)` — 내 예약 목록 조회 (`/reservations?startAt=&endAt=`)
- `(user_id, status)` — 상태별 조회
- `(status, created_at)` — 어드민 전체 조회

**역방향 제약** — `property_id FK ON DELETE RESTRICT` 가 *시나리오의 "Property 삭제 cascade"* 와 충돌 가능. 해결: Property 삭제 시 Service 단에서 *해당 Property 의 활성 예약 (status in CONFIRMED/CHECKED_IN) 없음* 검증 → 없으면 삭제 진행 → DB cascade 동작. 활성 예약 있으면 거절.

### 2.9 `reservation_price_entry` — Reservation.priceSnapshot 스냅샷 (E4 잠정)

| 컬럼 | 타입 | 제약 |
|---|---|---|
| reservation_id | BIGINT | PK, FK → reservation(id) ON DELETE CASCADE |
| date | DATE | PK |
| price_per_night | BIGINT | NOT NULL |

> 한 Reservation 의 박 수 = N row. 체크인\~체크아웃-1 의 각 날짜 + 그날 1박 가격.

### 2.10 `reservation_refund_rule` — Reservation.cancellationPolicySnapshot 스냅샷 (E4 잠정)

| 컬럼 | 타입 | 제약 |
|---|---|---|
| reservation_id | BIGINT | PK, FK → reservation(id) ON DELETE CASCADE |
| days_before_check_in | INT | PK |
| refund_rate | INT | NOT NULL, CHECK (0\~100) |

> `refund_rule` (Property 의 *현재* 정책) 과 *컬럼 구조 동일* 하지만 *별도 테이블* — 시점 일관성 (§ 9.4) 보장. Property.cancellationPolicy 가 나중에 바뀌어도 *이 예약의 환불액* 은 본 테이블 row 들로 계산.

### 2.11 `wishlist` — 단순 조인 엔티티 (C4 결정)

| 컬럼 | 타입 | 제약 |
|---|---|---|
| user_id | BIGINT | PK, FK → users(id) ON DELETE CASCADE |
| property_id | BIGINT | PK, FK → property(id) ON DELETE CASCADE |
| created_at | TIMESTAMP | NOT NULL |

**인덱스**: PK + `(user_id, created_at DESC)` — 내 찜 목록 조회 (`/users/{userId}/wishes`)

**카운터 갱신** — `wishlist` INSERT/DELETE 시 `property.wish_count` increment/decrement. 본 라운드는 동일 트랜잭션 동기 갱신, 동시성은 후속 라운드 (Q4 보류).

---

## 3. 인덱스 전략

검색·조회 쿼리 패턴 기반:

| 쿼리 패턴 | 인덱스 | 활용 |
|---|---|---|
| `property WHERE city = ?` | `(city)` | 검색 1차 필터 |
| `property WHERE city = ? AND display_status = ?` | `(city, display_status)` | 노출 필터 결합 |
| `room_type WHERE property_id = ?` | `(property_id)` | Property 경유 RoomType 조회 |
| `daily_room WHERE room_type_id IN (...) AND date BETWEEN ?` | PK `(room_type_id, date)` | 검색 일자별 일괄 |
| `reservation WHERE user_id = ? AND created_at BETWEEN ?` | `(user_id, created_at)` | 내 예약 목록 |
| `reservation WHERE user_id = ? AND status = ?` | `(user_id, status)` | 상태별 |
| `reservation WHERE status = ? ORDER BY created_at` | `(status, created_at)` | 어드민 전체 |
| `wishlist WHERE user_id = ? ORDER BY created_at` | `(user_id, created_at)` | 내 찜 목록 |
| `property ORDER BY wish_count DESC` | `(wish_count DESC)` | 검색 정렬 `wishes_desc` |

> 본 라운드는 *추정 기반*. 실제 운영 트래픽 측정 후 *EXPLAIN ANALYZE* 로 검증 필요 (후속 라운드).

---

## 4. Cascade · FK 정책

### 4.1 Aggregate 경계와 FK 의 관계

DDD 의 *Aggregate boundary 안에서 객체 참조, 밖에선 ID 참조* 원칙은 *코드 레벨*. DB 레벨에서는 **FK + ON DELETE 정책** 으로 *무결성 + 비즈니스 룰* 표현.

| 관계 | FK | ON DELETE | 이유 |
|---|---|---|---|
| `property` → `room_type` | property_id FK | CASCADE | 시나리오 명시 |
| `property` → `property_amenity` | property_id FK | CASCADE | Aggregate 내부 VO 컬렉션 |
| `property` → `refund_rule` | property_id FK | CASCADE | Aggregate 내부 |
| `room_type` → `bed_entry` | room_type_id FK | CASCADE | Aggregate 내부 |
| `room_type` → `daily_room` | room_type_id FK | CASCADE | 별도 Aggregate 지만 *RoomType 삭제 시 DailyRoom 도 무의미* |
| `users` → `reservation` | user_id FK | **RESTRICT** | 예약 있는 사용자 삭제 방지 (본 라운드 User 삭제 없음) |
| `property` → `reservation` | property_id FK | **RESTRICT** | 활성 예약 있는 Property 삭제 방지 (Service 단 사전 검증 필요) |
| `room_type` → `reservation` | room_type_id FK | **RESTRICT** | 동상 |
| `reservation` → `reservation_price_entry` | reservation_id FK | CASCADE | Aggregate 내부 스냅샷 |
| `reservation` → `reservation_refund_rule` | reservation_id FK | CASCADE | Aggregate 내부 스냅샷 |
| `users` → `wishlist` | user_id FK | CASCADE | 사용자 삭제 시 찜도 |
| `property` → `wishlist` | property_id FK | CASCADE | Property 삭제 시 찜도 |

### 4.2 Property 삭제 시퀀스 (활성 예약 검증)

```
Admin DELETE /api-admin/v1/properties/{id}
  → PropertyService.delete(propertyId)
    → reservationRepository.existsActiveByPropertyId(propertyId, [CONFIRMED, CHECKED_IN])
    → 있으면 → CoreException(CONFLICT, "활성 예약이 있어 삭제 불가")
    → 없으면 → propertyRepository.delete(property)
      → DB: property 삭제 → room_type cascade → daily_room cascade
                          → property_amenity cascade
                          → refund_rule cascade
                          → wishlist cascade
                          → reservation FK RESTRICT (활성 예약 0개라 통과,
                             과거 CHECKED_OUT/CANCELLED 예약은 *property_id NULL 처리* 또는
                             *historical reservation 분리* 별도 결정)
```

> ⚠️ **부수 결정 필요** — 과거 CHECKED_OUT/CANCELLED 예약이 있을 때 Property 삭제 시 어떻게? 옵션:
> - (a) RESTRICT — 모든 예약 (과거 포함) 있으면 삭제 불가
> - (b) SET NULL — property_id 만 NULL 처리, 스냅샷 (`property_name_snapshot`) 으로 정보 보존
> - (c) 별도 historical 테이블로 이관
>
> 본 라운드 결정 (2026-05-30 사용자 확인): **(a) RESTRICT**. 학습 단계 단순성 우선. 호텔 폐업 시나리오 도입 시 (b) SET NULL + 스냅샷 활용으로 재검토.

---

## 5. E4 결정 — VO 컬렉션 영속화 전략 (확정)

본 ERD 의 **6 보조 테이블** (`property_amenity`, `refund_rule`, `bed_entry`, `reservation_price_entry`, `reservation_refund_rule`, `wishlist`) 중 `wishlist` 를 제외한 5개는 *도메인 객체의 VO 컬렉션* 을 영속화한 것. 영속화 방식 3 옵션:

### 5.1 선택지 비교

| 측면 | A. 별도 테이블 (잠정) | B. JSON 컬럼 | C. 혼합 |
|---|---|---|---|
| 모양 | `bed_entry`, `refund_rule`, `reservation_price_entry`, `reservation_refund_rule`, `property_amenity` 각 별도 | `room_type.bed_configuration JSON`, `property.cancellation_policy JSON`, `reservation.price_snapshot JSON`, `reservation.cancellation_policy_snapshot JSON`, `property.amenities JSON` | 분석 가치 있는 것 (예: `reservation_price_entry`) 만 별도, 나머지 JSON |
| 테이블 수 | 11 | 6 | ~8 |
| JPA 매핑 | `@ElementCollection` (표준, 인터페이스 친화) | `@Convert(JsonConverter)` 또는 Hibernate 6 의 `@JdbcTypeCode(SqlTypes.JSON)` | 혼합 |
| 분석 쿼리 (SQL JOIN, WHERE) | ✅ 자연 (`WHERE amenity = 'WIFI'`) | ⚠️ JSON 함수 (`JSON_CONTAINS`, `->`) — 인덱스 어려움 | 부분 ✅ |
| 추가 라이브러리 | 없음 (JPA 표준) | Hibernate 6 의 JSON 지원 또는 jackson 컨버터 | 양쪽 |
| 코드 단순성 | ⚠️ 5 보조 테이블 매핑 | ✅ JSON 직렬화 하나로 | ⚠️ 양쪽 |
| 컬렉션 변경 비용 | ⚠️ delete-all + insert (N+1 위험) | ✅ 단일 UPDATE | 혼합 |
| 시점 일관성 (스냅샷) | ✅ FK CASCADE 로 자연 | ✅ Reservation row 한 묶음 | ✅ |
| 학습 가치 | ✅ JPA `@ElementCollection`·`@CollectionTable`·`@OrderColumn` 학습 | ✅ Hibernate JSON 매핑·JSON 함수 학습 | ✅ 두 패턴 모두 |

### 5.2 결정 — A (별도 테이블, 2026-05-30 사용자 확인)

**Round 1 구조 일관성 유지** — 단일 VO 는 `@Embedded`, 컬렉션은 `@ElementCollection + @CollectionTable` 별도 테이블.

**이유**:
- **Round 1 패턴 일관** — `LoginId`, `Password`, `Name` 등 모든 VO 가 `@Embeddable` data class. 본 라운드의 5 VO 컬렉션도 같은 패턴의 *자연 확장* (단일 → 컬렉션)
- **JPA 표준** (`@ElementCollection`) — 추가 라이브러리 0
- **분석 쿼리 자연** — 검색 정렬 `amenity LIKE ...` 같은 향후 확장 가능
- **스냅샷** (`reservation_price_entry`, `reservation_refund_rule`) 도 *시점 일관성* 을 row 단위로 검증 가능

**단점 (인지)**:
- 테이블 수 ↑ (5 보조 테이블)
- JPA `@ElementCollection` 의 변경 시 *delete-all + insert* 부담 — 본 라운드는 컬렉션 변경 빈도 낮음 (어드민 정책 변경 정도)

### 5.3 재검토 트리거 (향후)

- VO 컬렉션의 *변경 빈도* 가 급증 → JSON 컬럼 전환 검토
- 분석 쿼리 패턴이 *완전히 사라짐* → JSON 간단성 이득
- Hibernate 6 JSON 매핑의 안정성 검증 후

---

## 6. 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-05-30 | 첫 작성 (Round 2) | LLD 영속 구조 — 11 테이블 + 인덱스 + Cascade/FK 정책. E1\~E3·E5·E6 잠정 디폴트 적용, E4 는 결정 사안으로 표시 |
| 2026-05-30 | E4 확정 (별도 테이블 + `@ElementCollection`) + Property 삭제 부수 결정 확정 ((a) RESTRICT) | 사용자 결정 — Round 1 의 `@Embedded` VO 패턴 일관성 우선. JSON 컬럼은 향후 재검토 트리거로 분리 |
| 2026-05-31 | E4 / Property 삭제 정책 결정을 `docs/round-2/03-questions.md` Q10·Q11 로 영구 누적 | 전체 일관성 검수 — 결정의 영구 기록 가치를 위해 questions.md 누적 (chat 결정만 있던 상태 보정) |

---

## 7. 참고

- HLD 본체: [`00-overview.md`](./00-overview.md) — 시스템 컨텍스트·NFR·트레이드오프 종합
- 요구사항: [`01-requirements.md`](./01-requirements.md) — 정책 결정 Q1\~Q3
- Runtime View: [`02-sequence-diagrams.md`](./02-sequence-diagrams.md) — 객체 협업
- 클래스 (LLD): [`03-class-diagram.md`](./03-class-diagram.md) — 본 ERD 의 도메인 매핑
- ADR: [`docs/adr/`](../adr/) — ADR-001/002/003
- 결정 누적: [`docs/round-2/03-questions.md`](../round-2/03-questions.md)
- 운영 정책: [`docs/design/README.md`](./README.md)
