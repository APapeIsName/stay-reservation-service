## 🎯 배경

**찜**하고, **쿠폰** 쓰고, 객실을 **예약** 및 **결제**하는 **감성 숙박 커머스**.

내가 좋아하는 숙소들을 한곳에 모아두고, 원하는 날짜에 객실을 잡아 떠나는 여행. 유저의 검색·찜·예약 행동은 모두 랭킹과 추천으로 연결돼요.

우린 야놀자·여기어때 같은 숙박 플랫폼의 핵심 흐름을 하나씩 직접 만들어갈 거예요.

---

## 🧭 서비스 흐름 예시

1. 사용자가 **회원가입**을 하고
2. 여러 도시·숙소를 둘러보고, 마음에 드는 숙소엔 **찜**을 누르죠.
3. 사용자는 **쿠폰을 발급**받고, 원하는 **체크인/체크아웃 날짜와 인원**을 입력해 객실을 **예약하고 결제**합니다.
4. 유저의 검색/찜/예약 기록은 모두 적재되고, 그 데이터는 이후 다양한 기능(인기 숙소, 추천, 랭킹)으로 확장될 수 있어요.

---

## 🏨 도메인 용어 (Ubiquitous Language)

| 용어 | 영문 | 설명 |
| --- | --- | --- |
| 숙소 | Property | 호텔/펜션/모텔/리조트 단위. 위치, 편의시설, 정책을 보유 |
| 객실 타입 | RoomType | 한 숙소가 판매하는 객실 카테고리 (예: "스탠다드 더블", "오션뷰 스위트") |
| 일자별 재고 | DailyRoomInventory | 특정 날짜의 객실 타입별 판매 가능 수량 |
| 일자별 요금 | DailyRoomRate | 특정 날짜의 객실 타입별 1박 요금 (성수기/비수기 변동) |
| 찜 | Wishlist | 유저가 숙소를 찜한 기록 |
| 예약 | Reservation | 체크인~체크아웃 기간 동안 특정 객실 타입을 점유하기로 한 계약 |
| 결제 | Payment | 예약에 대한 PG 결제 정보 |
| 쿠폰 | Coupon | 예약 결제 시 적용되는 할인권 |

---

## ✅ API 제안사항

- 대고객 기능은 `/api/v1` prefix 를 통해 제공합니다.
    
    ```markdown
    유저 로그인이 필요한 기능은 아래 헤더를 통해 유저를 식별해 제공합니다.
    인증/인가는 주요 스코프가 아니므로 구현하지 않습니다.
    유저는 타 유저의 정보(예약/찜)에 직접 접근할 수 없습니다.
    
    * **X-Loopers-LoginId** : 로그인 ID
    * **X-Loopers-LoginPw** : 비밀번호
    ```
    
- 어드민 기능은 `/api-admin/v1` prefix 를 통해 제공합니다.
    
    ```markdown
    어드민 기능은 아래 헤더를 통해 어드민(숙소 관리자 또는 플랫폼 운영자)을 식별해 제공합니다.
    
    * **X-Loopers-Ldap** : loopers.admin
    
    LDAP : Lightweight Directory Access Protocol
    중앙 집중형 사용자 인증, 정보 검색, 액세스 제어.
    -> 회사 사내 어드민
    ```
    

## ✅ 요구사항

## 👤 유저 (Users)

| **METHOD** | **URI** | **user_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

---

## 🏨 숙소 & 객실 (Properties / RoomTypes)

| **METHOD** | **URI** | **user_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api/v1/properties/search` | X | 숙소 검색 (도시 + 체크인/아웃 + 인원) |
| GET | `/api/v1/properties/{propertyId}` | X | 숙소 상세 조회 |
| GET | `/api/v1/properties/{propertyId}/rooms` | X | 숙소의 객실 타입 목록 + 가용 여부 |
| GET | `/api/v1/properties/{propertyId}/rooms/{roomTypeId}` | X | 객실 타입 상세 (요금/정책 포함) |

### ✅ 숙소 검색 쿼리 파라미터

| **파라미터** | **예시** | **설명** |
| --- | --- | --- |
| `city` | `seoul` / `jeju` | 도시 코드 (필수) |
| `checkIn` | `2026-05-10` | 체크인 날짜 (필수) |
| `checkOut` | `2026-05-12` | 체크아웃 날짜 (필수, > checkIn) |
| `guests` | `2` | 투숙 인원 (기본값 2) |
| `sort` | `recommended` / `price_asc` / `rating_desc` / `wishes_desc` | 정렬 기준 |
| `page` | `0` | 페이지 번호 (기본값 0) |
| `size` | `20` | 페이지당 숙소 수 (기본값 20) |

> 💡 정렬 기준은 선택 구현입니다.
> 
> 
> 필수는 `recommended`, 그 외는 `price_asc`, `rating_desc`, `wishes_desc` 정도로 제한해도 충분합니다.
> **검색 결과의 가격은 검색 기간(체크인~체크아웃)의 합산 요금**입니다. 일자별 요금이 다르므로 단순한 1박 가격이 아닙니다.
> 

---

## 🏷 숙소 & 객실 ADMIN

| **METHOD** | **URI** | **ldap_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/properties?page=0&size=20` | O | **등록된 숙소 목록 조회** |
| GET | `/api-admin/v1/properties/{propertyId}` | O | **숙소 상세 조회** |
| POST | `/api-admin/v1/properties` | O | **숙소 등록** (이름, 위치, 편의시설, 정책) |
| PUT | `/api-admin/v1/properties/{propertyId}` | O | **숙소 정보 수정** |
| DELETE | `/api-admin/v1/properties/{propertyId}` | O | **숙소 삭제** |
- 숙소 제거 시, 해당 숙소의 객실 타입들도 삭제되어야 함 |
| GET | `/api-admin/v1/rooms?page=0&size=20&propertyId={propertyId}` | O | **객실 타입 목록 조회** |
| GET | `/api-admin/v1/rooms/{roomTypeId}` | O | **객실 타입 상세 조회** |
| POST | `/api-admin/v1/rooms` | O | **객실 타입 등록**
- 소속 숙소는 이미 등록된 숙소여야 함
- 기준 인원, 최대 인원, 침대 구성 포함 |
| PUT | `/api-admin/v1/rooms/{roomTypeId}` | O | **객실 타입 정보 수정**
- 소속 숙소는 수정할 수 없음 |
| DELETE | `/api-admin/v1/rooms/{roomTypeId}` | O | **객실 타입 삭제** |
| PUT | `/api-admin/v1/rooms/{roomTypeId}/inventory` | O | **일자별 재고/요금 등록 또는 수정** |

> 숙소·객실 정보 중 고객과 어드민에게 제공되어야 할 정보에 대해 고민해보세요.
특히 **일자별 재고와 요금**은 검색 결과와 직결되므로 데이터 모델 설계가 중요합니다.
> 

**일자별 재고/요금 등록 요청 예시**

```json
{
  "ranges": [
    {
      "from": "2026-05-01",
      "to":   "2026-05-31",
      "totalRooms": 10,
      "pricePerNight": 120000
    },
    {
      "from": "2026-07-15",
      "to":   "2026-08-15",
      "totalRooms": 10,
      "pricePerNight": 220000
    }
  ]
}
```

---

## ❤️ 찜 (Wishlist)

| **METHOD** | **URI** | **user_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/properties/{propertyId}/wishes` | O | 숙소 찜 등록 |
| DELETE | `/api/v1/properties/{propertyId}/wishes` | O | 숙소 찜 취소 |
| GET | `/api/v1/users/{userId}/wishes` | O | 내가 찜한 숙소 목록 조회 |

> 찜은 **객실 단위가 아니라 숙소 단위**로 관리합니다.
사용자는 일반적으로 "이 호텔 좋네"라고 찜하지, 특정 객실 타입을 찜하지 않습니다.
> 

---

## 🧾 예약 (Reservations)

| **METHOD** | **URI** | **user_required** | **설명** |
| --- | --- | --- | --- |
| POST | `/api/v1/reservations` | O | 예약 요청 |
| GET | `/api/v1/reservations?startAt=2026-01-31&endAt=2026-02-10` | O | 유저의 예약 목록 조회 |
| GET | `/api/v1/reservations/{reservationId}` | O | 단일 예약 상세 조회 |
| POST | `/api/v1/reservations/{reservationId}/cancel` | O | 예약 취소 (정책에 따라 환불 처리) |

**요청 예시:**

```json
{
  "propertyId": 1024,
  "roomTypeId": 5520,
  "checkIn": "2026-05-10",
  "checkOut": "2026-05-12",
  "guestCount": 2,
  "guestName": "홍길동",
  "guestPhone": "010-1234-5678"
}
```

> **결제**는 과정 진행 중, **추가로 개발**하게 됩니다!
**예약 정보**에는 당시의 숙소·객실·정책 정보가 스냅샷으로 저장되어야 합니다.
**예약 시에 다음 동작이 보장되어야 합니다 :**
> 
- 체크인 ~ 체크아웃 사이의 모든 날짜에 대한 **일자별 재고 차감** (체크아웃 당일은 차감 X)
- **더블부킹 방지** (동일 객실 타입의 같은 날짜에 재고 이상의 예약이 들어가면 안 됨)
- 인원수가 객실의 최대 인원을 초과하면 안 됨

> 
> 

---

## 🧾 예약 ADMIN

| **METHOD** | **URI** | **ldap_required** | **설명** |
| --- | --- | --- | --- |
| GET | `/api-admin/v1/reservations?page=0&size=20` | O | 예약 목록 조회 |
| GET | `/api-admin/v1/reservations/{reservationId}` | O | 단일 예약 상세 조회 |

---

### 📡 나아가며

> ⚙️ **모든 기능의 동작을 개발한 후에 동시성(더블부킹), 멱등성, 일관성, 느린 검색, 동시 예약, 외부 결제 장애 등 실제 숙박 서비스에서 발생하는 문제들을 해결하게 됩니다.**
> 
> 
> 숙박 도메인에서 가장 큰 도전은 다음과 같아요.
> 
> - **날짜 기반 재고 모델** : 같은 객실 타입이라도 5월 10일과 5월 11일은 다른 재고 단위
> - **더블부킹 방지** : 1실 남은 객실에 동시에 여러 명이 결제 시도
> - **검색 성능** : 도시/날짜/인원 조합 검색은 매우 빈번하지만 비용이 큼
> - **예약 상태 머신** : `PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT` (또는 `CANCELLED`)
> - **PG 연동** : 결제 성공/실패와 예약 확정/해제의 일관성
