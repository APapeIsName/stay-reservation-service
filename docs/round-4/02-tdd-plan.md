# Round 4 — 트랜잭션·동시성·쿠폰 TDD 작업 계획 (v1)

> **목적**
> (A) 무엇을 만들지 = 구현 대상 컴포넌트 인벤토리 (쿠폰 2 Aggregate + 예약 변경 + 구간별 락 + 어드민 API)
> (B) 무엇을 검증할지 = 컴포넌트별 "입력 → 예상 답변" 테스트 케이스 카탈로그
> (C) 어떤 순서로 = 의존 방향 기준 통합 사이클 순서 (도메인 L1 → application L2 → infra/락 L3 → API L4 → 동시성 L3)
>
> **선행 문서:** [`docs/round-4/03-questions.md`](./03-questions.md) (Q1~Q7 — 락 전략·쿠폰 도메인·어드민 결정), [`docs/adr/ADR-004-lock-strategy.md`](../adr/ADR-004-lock-strategy.md) (구간별 차등 락 — 정본), ADR-002 (즉시 CONFIRMED 유지 — Q4), ADR-003 (단일 `daily_room`)
> **결정 누적:** [`03-questions.md`](./03-questions.md)
>
> **Round 4 한 줄 정의:** "한 예약 트랜잭션 안의 세 동시성 구간(재고·쿠폰·찜)에 작업 모양 기준으로 다른 락 전략을 적용하고, 쿠폰 할인을 예약 금액 계산에 통합한다." 핵심은 **B.5 동시성 L3** (실 MySQL + `ExecutorService`/`CountDownLatch`) — 이번 라운드의 학습 목표.

---

## A. 구현 대상 — 컴포넌트 인벤토리 (요약)

상세 인벤토리는 Part B 의 각 절 머리에 있다. 모든 도메인 코드는 `modules/domain` (rule 01), Application 은 `apps/stay-api` (rule 19/20). 카탈로그 ID 는 Round 3 의 `PRP-`/`DR-`/`RSV-`/`SAS-`/`PSVC-`/`WSVC-`/`RSVC-` 와 **충돌하지 않는 신규 prefix** 를 쓴다.

| # | 컴포넌트 | 핵심 클래스 | 카탈로그 | 등급 |
|---|---|---|---|---|
| B.1 | Coupon Aggregate (템플릿) | `Coupon`(AR), `CouponType`(enum FIXED/RATE), `CouponRepository`(port) | `CPN-` 11건 | L1 |
| B.2 | CouponIssue Aggregate (발급분) | `CouponIssue`(AR, `@Version` 낙관락), `CouponIssueStatus`(enum AVAILABLE/USED), `CouponIssueRepository`(port) | `CIS-` 13건 | L1/L2 |
| B.3 | 예약 변경 — Reservation / PriceSnapshot 확장 | `PriceSnapshot`(3금액 분해), `Reservation`(+ `couponId`), `ReserveCommand`(+ `couponId`) | `RSV2-` 13건 | L1/L2 |
| B.4 | Application — CouponService + ReservationService 쿠폰적용 | `CouponService`·`IssueCommand`·`CouponInfo`·`MyCouponInfo` + `ReservationService.reserve` 쿠폰 경로 (Fake Repository) | `CSVC-` 11건 / `RSVC2-` 10건 | L2 |
| B.5 | 동시성 (실 MySQL — Round 4 핵심) | `DailyRoomRepository.findForReserve`(비관락 finder) · `CouponIssue` `@Version` · `Property.wish_count` 원자적 UPDATE · `wishlist` UNIQUE — `ExecutorService`/`CountDownLatch` 하니스 | `CC-` 14건 | L3 |
| B.6 | 어드민 API | `AdminCouponV1Controller`(CRUD + 발급내역) · `AdminAuthInterceptor`(`X-Loopers-Ldap` 스텁) · 고객 발급/내쿠폰 컨트롤러 | `ADM-` 12건 | L4 |

### 의존 흐름 (예약 1건 — 쿠폰 적용 + 구간별 락 — ADR-004 반영)

```
ReservationService.reserve(userId, ReserveCommand(+couponId?))
  → DateRange / GuestInfo VO 화                                       // 검증 발동 (rule 06)
  → PropertyRepository.findById → property.findRoomType(roomTypeId)
  → dailyRoomRepository.findForReserve(roomTypeId, stayDates)          // ★ 비관락 SELECT ... FOR UPDATE, ORDER BY date ASC (재고 구간 — ADR-004 §1)
  → stayAvailabilityService.validateAvailability(period, dailyRooms)   // 기간 완전성·가용성 선검증
  → val priceBeforeDiscount = stayAvailabilityService.quote(period, dailyRooms).totalPrice()
  → couponId?.let {                                                    // ★ 쿠폰 구간 (낙관락 — ADR-004 §2)
        val issue = couponIssueRepository.findById(it)                 // 미존재 → COUPON_NOT_FOUND
        require(issue.belongsTo(userId))                               // 위반 → COUPON_NOT_OWNED
        issue.markUsed(now)                                            // AVAILABLE 가드 + 만료 파생 → USED (@Version 충돌 시 409)
        val coupon = couponRepository.findById(issue.couponId)
        discountAmount = coupon.calculateDiscount(priceBeforeDiscount) // minOrderAmount 미달 → COUPON_MIN_ORDER_NOT_MET
     }
  → val priceSnapshot = PriceSnapshot(entries, priceBeforeDiscount, discountAmount, finalPrice)   // 3분해
  → Reservation.confirm(userId, property, roomType, couponId, period, guestInfo, priceSnapshot, now)  // couponId 는 식별 인자군에 묶음 (§4.4 시그니처 단일 근거)
  → stayAvailabilityService.consumeAll(period, dailyRooms)             // all-or-nothing 차감
  → ReservationRepository.save(reservation)
  ← ReservationInfo.from(reservation)
```

세 동시성 구간은 같은 `@Transactional` 안에서 공존한다 — **재고=비관락, 쿠폰=낙관락, 찜=원자적 증가+유니크** (행·기법 상이로 무충돌, ADR-004).

### 설계 결정 메모

- **D-C1 락 전략은 ADR-004 가 정본** — 세 구간의 전략은 "동시 요청들에게 무엇이 일어나야 하는가"로 갈렸다: 재고는 *일부 거절*(비관락), 쿠폰은 *정확히 1명*(낙관락), 찜은 *전원 성공*(원자적 증가). 본 카탈로그는 ADR-004 §1~§3 을 그대로 검증한다 (B.5). **세 문서(02-tdd-plan / 03-questions / ADR-004)는 락 표기가 반드시 일치해야 한다** — `daily_room` 은 비관락(@Version 없음!), `coupon_issue` 만 `@Version`, `property.wish_count` 는 원자적 UPDATE.
- **D-C2 쿠폰 2 Aggregate + ID 참조** (Q5) — `Coupon`(템플릿, 어드민 CRUD) + `CouponIssue`(발급분, 유저 소유·독립 락 단위) 분리. `CouponIssue` 는 `couponId` **ID 참조만** (rule 18 §5). 대조: Property↔RoomType 은 1 Aggregate(유한 내부 entity 중첩), Coupon↔CouponIssue 는 2 Aggregate(유저별 무한 증식·독립 락). 할인 계산은 단일 객체에 담겨 `Coupon.calculateDiscount()` 도메인 메서드 — Domain Service 불필요 (rule 19 §4).
- **D-C3 EXPIRED 는 저장 안 함 — 파생 판정** (정본) — `CouponIssueStatus` 저장 값은 `AVAILABLE`·`USED` **2개뿐**. `EXPIRED` 는 조회/사용 시 `now > coupon.expiredAt` 로 파생 판정 (배치 없음). `MyCouponInfo` 응답에서만 3상태(AVAILABLE/USED/EXPIRED)로 노출. → 잠정 재검토 트리거: Part E.1 (EXPIRED 파생 시점).
- **D-C4 PriceSnapshot 3분해 — `totalPrice` 의 의미 명확화** (정본) — 기존 `entries: List<DailyPriceEntry>` 는 유지하면서 세 금액 추가: `priceBeforeDiscount`(= Σ entries.pricePerNight, 할인 전) / `discountAmount` / `finalPrice`(= priceBeforeDiscount − discountAmount, 0 floor — 최종 결제액). `Reservation.totalPrice` 는 `finalPrice`(최종 결제액)로 의미 명확화. 쿠폰 미적용 시 `discountAmount == 0`, `finalPrice == priceBeforeDiscount`.
- **D-C5 즉시 CONFIRMED 유지** (Q4 / ADR-002) — 쿠폰·재고는 `reserve()` 단일 트랜잭션 내 동기 처리. PG 결제는 범위 밖이라 PENDING 미도입 (도입 시 CONFIRMED 로 올릴 트리거가 없어 영원히 PENDING 에 갇힘). `ReservationStatus.PENDING` 은 enum 자리만 유지.
- **D-C6 prefix 정리** — 예약 변경(B.3) 은 기존 `RSV-`(Round 3) 와 구분해 **`RSV2-`** 사용. 예약 서비스 쿠폰 경로(B.4) 는 기존 `RSVC-` 와 구분해 **`RSVC2-`**. 카탈로그 ID 검색은 항상 하이픈 포함 (`RSV2-` vs `RSVC2-` vs `RSV-`/`RSVC-`).

---

## B. 검증 카탈로그 — 컴포넌트별 "입력 → 예상 답변"

형식은 Round 1~3 과 동일: **(ID · Given · When · Then)** + `@Tag` (rule 17). ID 는 테스트 `@DisplayName` 접두로 사용 (rule 14). Then 안의 "가정 (specGap ...)" 표기는 Part E 의 잠정 결정 항목 — 해당 Cycle Red 진입 전 확정한다.

### B.1 Coupon Aggregate — `Coupon`(AR) · `CouponType`(enum) · 할인 계산

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (enum) | `CouponType` | `FIXED`(정액 — value=할인 원), `RATE`(정률 — value=퍼센트 정수) 2종. 할인 계산 분기 기준 | Unit (L1, @Tag unit) |
| Domain (Aggregate Root) | `Coupon` | 쿠폰 템플릿 AR (`modules/domain/.../coupon/Coupon.kt`). `name`·`type`·`value`·`minOrderAmount: Long?`·`expiredAt: LocalDateTime` 보유. 핵심 메서드 `calculateDiscount(preDiscountAmount): Long` — FIXED `min(value, pre)` / RATE `floor(pre × value / 100)` / minOrderAmount 미달 시 CoreException(COUPON_MIN_ORDER_NOT_MET). 락 없음 (읽기 위주·어드민 수정만 — `@Version` 없음, ADR-004) | Unit (L1, @Tag unit) |
| Domain (Port) | `CouponRepository` | 포트 인터페이스 — `findById`/`save`/`findAll(page)`/`deleteById` (어드민 CRUD). 인터페이스 자체 테스트 X — 구현체는 영속 단계 | (인터페이스 — 테스트 대상 아님) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| CPN-01 | type=FIXED, value=5000, minOrderAmount=null, expiredAt=2026-12-31T23:59, preDiscountAmount=30000 | coupon.calculateDiscount(30000) | 반환값 == 5000 (정액 — value 그대로) | unit |
| CPN-02 | type=FIXED, value=50000 (할인액 > 주문액), preDiscountAmount=30000 | coupon.calculateDiscount(30000) | 반환값 == 30000 — min(value, pre) 로 주문액 상한 클램프 (할인이 결제액 초과 불가) | unit |
| CPN-03 | type=FIXED, value=30000 == preDiscountAmount=30000 (경계 — 전액 할인) | coupon.calculateDiscount(30000) | 반환값 == 30000 (할인 후 0원 — finalPrice 0 floor 의 전제) | unit |
| CPN-04 | type=RATE, value=10 (10%), preDiscountAmount=30000 | coupon.calculateDiscount(30000) | 반환값 == 3000 (floor(30000 × 10 / 100)) | unit |
| CPN-05 | type=RATE, value=10, preDiscountAmount=99999 (끝전 발생) | coupon.calculateDiscount(99999) | 반환값 == 9999 — floor(99999 × 10 / 100 == 9999.9) 끝전 내림 (specGap C-1) | unit |
| CPN-06 | type=RATE, value=100 (100% — 경계 상한), preDiscountAmount=30000 | coupon.calculateDiscount(30000) | 반환값 == 30000 (전액 할인 — RATE 상한) | unit |
| CPN-07 | type=RATE, value=33, preDiscountAmount=10000 | coupon.calculateDiscount(10000) | 반환값 == 3300 (floor(10000 × 33 / 100 == 3300.0) — 정확히 떨어지는 케이스) | unit |
| CPN-08 | type=FIXED, value=5000, minOrderAmount=50000, preDiscountAmount=49999 (1원 미달) | coupon.calculateDiscount(49999) | CoreException(COUPON_MIN_ORDER_NOT_MET, statusCode 매핑 BAD_REQUEST) — 최소 결제금액 미달 (specGap C-2) | unit |
| CPN-09 | type=FIXED, value=5000, minOrderAmount=50000, preDiscountAmount=50000 (정확히 충족 — 경계) | coupon.calculateDiscount(50000) | 반환값 == 5000 — 경계 당값은 충족 포함 (>= 비교, specGap C-2) | unit |
| CPN-10 | type=RATE, value=10, minOrderAmount=null (조건 없음), preDiscountAmount=0 | coupon.calculateDiscount(0) | 반환값 == 0 (할인 대상 0원 — floor(0) == 0). minOrderAmount null 이면 미달 검사 생략 | unit |
| CPN-11 | CouponType enum | CouponType.values() | 2종 (FIXED, RATE) 정의됨 — 할인 분기 enum 자리 가드. EXPIRED 등 상태값은 CouponType 이 아니라 CouponIssueStatus 소관 (혼동 가드) | unit |

> **만료 주의** — `Coupon.expiredAt` 자체로 인한 "만료 거절" 은 `calculateDiscount` 가 아니라 **`CouponIssue.markUsed(now)`/`isUsable(now, expiredAt)`** 가 담당한다 (B.2). `calculateDiscount` 는 금액 계산만 — 만료는 사용 시점 가드 (소유·시점 책임 분리, Q5).

### B.2 CouponIssue Aggregate — `CouponIssue`(AR, 낙관락) · `CouponIssueStatus`(enum) · 단일사용

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (enum) | `CouponIssueStatus` | **저장 값 2종** — `AVAILABLE`, `USED`. `EXPIRED` 는 enum 에 두지 않거나(잠정) 두더라도 **저장 안 함** — 조회/사용 시 `now > coupon.expiredAt` 파생 판정 (D-C3, specGap C-3) | Unit (L1, @Tag unit) |
| Domain (Aggregate Root) | `CouponIssue` | 발급분 AR (`modules/domain/.../coupon/CouponIssue.kt`). `couponId`(ID 참조)·`userId`·`status`·`issuedAt`·`usedAt: LocalDateTime?`·`version: Long`(`@Version`) 보유. 핵심: `belongsTo(userId): Boolean` / `markUsed(now)` (AVAILABLE 가드 위반 시 COUPON_ALREADY_USED→CONFLICT, 만료 시 COUPON_EXPIRED) / `isUsable(now, expiredAt): Boolean`. 정적 팩토리 `issue(couponId, userId, now)` 로 생성 (rule 08). **낙관락 단위 — `@Version` 은 coupon_issue 에만** (ADR-004 §2) | Unit (L1 가드 + L2 — markUsed 의 상태 전이는 빠른 단위, @Tag unit/slow-unit) |
| Domain (Port) | `CouponIssueRepository` | 포트 — `findById`/`save`/`findByUserId(page)`/`findByCouponId(page)`(어드민 발급내역). 구현체는 영속 단계 (`@Version` 충돌은 L3) | (인터페이스 — 테스트 대상 아님) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| CIS-01 | couponId=10, userId=1, now=2026-06-17T10:00 | CouponIssue.issue(10, 1, now) | 정상 생성 — couponId == 10, userId == 1, status == AVAILABLE, issuedAt == now, usedAt == null, version == 0 (초기) | unit |
| CIS-02 | issue(couponId=10, userId=1) | issue.belongsTo(1L) / issue.belongsTo(2L) | belongsTo(1L) == true, belongsTo(2L) == false (소유 판단 — RSVC2-04 의 타유저 거절 전제) | unit |
| CIS-03 | AVAILABLE 발급분, expiredAt=2026-12-31T23:59, now=2026-06-17T10:00 (만료 전) | issue.isUsable(now, expiredAt) | true — AVAILABLE + 미만료 | unit |
| CIS-04 | AVAILABLE 발급분, expiredAt=2026-06-16T23:59, now=2026-06-17T10:00 (만료 후) | issue.isUsable(now, expiredAt) | false — 만료 파생 판정 (status 는 여전히 AVAILABLE 이지만 사용 불가, D-C3) | unit |
| CIS-05 | expiredAt=2026-12-31T23:59 (정확히 경계 직전), now == expiredAt (동일 시각) | issue.isUsable(now, expiredAt) | true — now <= expiredAt 까지 사용 가능 가정 (만료는 expiredAt **초과**부터, specGap C-3) | unit |
| CIS-06 | USED 발급분 (이미 사용), expiredAt 미만료, now | issue.isUsable(now, expiredAt) | false — 이미 사용된 발급분은 만료 여부와 무관하게 사용 불가 | unit |
| CIS-07 | AVAILABLE 발급분, expiredAt=2026-12-31, now=2026-06-17T10:00 (만료 전) | issue.markUsed(now) — expiredAt 은 어떻게 전달? (specGap C-3: markUsed 가 expiredAt 을 인자로 받는가 / Coupon 동반 검증을 Service 가 선행하는가) | status == USED, usedAt == now (정상 전이) | slow-unit |
| CIS-08 | 이미 USED 인 발급분 | issue.markUsed(now2) 재호출 (이중 사용) | CoreException(COUPON_ALREADY_USED → CONFLICT). status == USED 유지, usedAt == 최초 사용 시각 (가드가 변이보다 먼저 — RSV cancel 이중 가드 패턴 답습) | slow-unit |
| CIS-09 | AVAILABLE 발급분, 만료된 쿠폰 (now > expiredAt) | issue.markUsed(now) (만료 사용 시도) | CoreException(COUPON_EXPIRED → BAD_REQUEST 또는 CONFLICT — specGap C-3 매핑 확정) — 만료 발급분 사용 거절. status == AVAILABLE 유지 | slow-unit |
| CIS-10 | AVAILABLE 발급분 markUsed(now) 성공 직후 | issue.status / issue.usedAt 재확인 | status == USED 영속, usedAt == now — 멱등 아님 (재호출은 CIS-08) | slow-unit |
| CIS-11 | CouponIssueStatus enum | CouponIssueStatus.values() | **AVAILABLE, USED 2종만** (EXPIRED 부재 — 저장 안 하는 파생 상태, D-C3). 응답 노출의 3상태는 MyCouponInfo 소관 (CSVC-09) | unit |
| CIS-12 | issue(couponId=10, userId=1, now) — version 필드 | 생성 직후 issue.version | version == 0 — `@Version` 초기값 (낙관락 단위, ADR-004 §2). 증가는 영속/충돌 시점(L3, CC-08) | unit |
| CIS-13 | belongsTo + markUsed 조합 — userId=1 발급분, 호출자 userId=1, AVAILABLE, 미만료 | belongsTo(1) 통과 → markUsed(now) | 정상 USED — 소유·상태·만료 3가드 통과 경로 (RSVC2-01 정상 경로의 도메인 단위 분해) | slow-unit |

### B.3 예약 변경 — `Reservation`(+couponId) · `PriceSnapshot`(3금액 분해) · `ReserveCommand`

> **Round 3 자산 확장**: `PriceSnapshot`·`Reservation`·`ReserveCommand`/`ReserveRequest` 는 Round 3 에 이미 존재 (`modules/domain/.../reservation/`). 본 절은 **기존 시그니처를 깨지 않으면서** 쿠폰 3금액·`couponId` 를 증보한다 — Round 3 의 RSV-/PS- 카탈로그는 회귀로 그대로 통과해야 한다 (행위 무변경 증명).

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (VO) | `PriceSnapshot` | 기존 `entries: List<DailyPriceEntry>` 유지 + **세 금액 추가**: `priceBeforeDiscount`(= Σ entries.pricePerNight) / `discountAmount` / `finalPrice`(= priceBeforeDiscount − discountAmount, 0 floor). 정적 팩토리 `of(entries, discountAmount)` 로 3금액 자동 도출 잠정 (specGap C-4: 생성 시그니처). 기존 `totalPrice()` 는 `finalPrice` 와 일치하도록 정렬 | Unit (L1, @Tag unit) |
| Domain (Aggregate) | `Reservation` | + `couponId: Long?` 필드 (감사 추적 — 어떤 쿠폰이 적용됐는지). `confirm(...)` 시그니처에 `couponId: Long?` 추가 (NULLABLE — 미적용 시 null). `totalPrice` 는 `priceSnapshot.finalPrice`(최종 결제액)로 의미 명확화. 기존 cancel·전이·refundAmount 메서드 불변 | Unit (L1 — 실객체 픽스처) |
| Application (DTO) | `ReserveCommand` | + `couponId: Long?` (NULLABLE — 미적용 시 생략). String/원시 타입·웹 무지 (rule 20) | 값 보유 — RSVC2 경유 |
| Interfaces (DTO) | `ReserveRequest` (`ReservationV1Dto`) | + `couponId: Long?` HTTP 직렬화 계약. Controller 가 `ReserveCommand` 로 변환 | E2E (ADM 절 인접 — RSV2 직접 대상 아님) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| RSV2-01 | entries=[(7/1,100000),(7/2,200000)], discountAmount=0 (쿠폰 미적용) | PriceSnapshot.of(entries, 0) | priceBeforeDiscount == 300000, discountAmount == 0, finalPrice == 300000, totalPrice() == 300000 (3금액 일관) | unit |
| RSV2-02 | entries=[(7/1,100000),(7/2,200000)], discountAmount=50000 | PriceSnapshot.of(entries, 50000) | priceBeforeDiscount == 300000, discountAmount == 50000, finalPrice == 250000, totalPrice() == 250000 | unit |
| RSV2-03 | entries=[(7/1,100000)], discountAmount=100000 (할인 == 주문액 — 경계) | PriceSnapshot.of(entries, 100000) | finalPrice == 0 (0 floor 경계 — 전액 할인), priceBeforeDiscount == 100000, discountAmount == 100000 | unit |
| RSV2-04 | entries=[(7/1,100000)], discountAmount=150000 (할인 > 주문액 — 비정상 입력) | PriceSnapshot.of(entries, 150000) | finalPrice == 0 (음수 floor 0) — 단, 정상 경로에선 calculateDiscount 의 min 클램프(CPN-02)로 도달 불가. 방어적 floor 가정 (specGap C-4) | unit |
| RSV2-05 | priceBeforeDiscount=0 도출되는 빈 entries | PriceSnapshot.of(emptyList(), 0) | CoreException(BAD_REQUEST) — 기존 PS-04 회귀 (빈 스냅샷 불가) 유지 | unit |
| RSV2-06 | 기존 Round 3 호출부 — entries 만으로 생성하던 코드 경로 (회귀) | PriceSnapshot(entries) 또는 of(entries, 0) | totalPrice() == Σ entries — Round 3 PS-02/03 회귀 통과 (3분해 도입이 기존 의미 불변 증명) | unit |
| RSV2-07 | RSV-01 픽스처(Round 3) + couponId=null, priceSnapshot(discountAmount=0) | Reservation.confirm(..., couponId = null, ...) | 정상 생성 — couponId == null, totalPrice == 300000 (== finalPrice), status == CONFIRMED. 기존 RSV-01 회귀 동치 | unit |
| RSV2-08 | RSV-01 픽스처 + couponId=77, priceSnapshot(discountAmount=50000, finalPrice=250000) | Reservation.confirm(..., couponId = 77, ...) | couponId == 77, totalPrice == 250000 (최종 결제액 — finalPrice 단일 출처), priceSnapshot.priceBeforeDiscount == 300000, .discountAmount == 50000 | unit |
| RSV2-09 | RSV2-08 예약 | reservation.priceSnapshot 의 3금액 단언 | priceBeforeDiscount(300000) − discountAmount(50000) == finalPrice(250000) == totalPrice — 감사 추적 3분해 일관 (발제 "결제 금액 정합성") | unit |
| RSV2-10 | couponId=77 로 confirm 한 CONFIRMED 예약, now=2026-06-25 (체크인 6일 전) | reservation.cancel(now) | 정상 CANCELLED — 환불은 **totalPrice(finalPrice=250000)** 기준 cancellationPolicySnapshot 위임 (할인 후 실결제액 기준 환불, specGap C-5) | unit |
| RSV2-11 | couponId=null 예약 (쿠폰 미적용) | reservation.cancel(now) | 정상 CANCELLED — couponId null 경로도 기존 cancel 불변 (회귀) | unit |
| RSV2-12 | ReserveCommand(propertyId, roomTypeId, checkIn, checkOut, guestCount, guestName, guestPhone, couponId=77) | command.couponId | == 77 (값 보유 — NULLABLE 필드 추가가 기존 필드 불변) | unit |
| RSV2-13 | ReserveCommand(..., couponId=null) (쿠폰 생략) | command.couponId | == null — 쿠폰 미적용 경로의 입력 표현 (RSVC2-07 의 전제) | unit |

### B.4 Application — `CouponService` (발급·내쿠폰) + `ReservationService.reserve` 쿠폰 적용 (Fake, L2)

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Application | `CouponService` | `issue(IssueCommand): CouponInfo` — Coupon 존재 확인 → `CouponIssue.issue(couponId, userId, now)` → save (발급, 시퀀스: 고객 발급 API). `myCoupons(userId, page): List<MyCouponInfo>` — CouponIssueRepository.findByUserId + 각 발급분의 Coupon 조인 → status 파생(AVAILABLE/USED/EXPIRED) 노출 (D-C3). Clock 주입 (rule 08) | Unit L2 (Fake Repository + Clock.fixed, slow-unit) |
| Application | `ReservationService` (쿠폰 경로 증보) | `reserve(userId, command(+couponId?))` — 기존 재고 흐름에 쿠폰 구간 삽입: couponId 있으면 CouponIssue 조회·belongsTo·markUsed·Coupon.calculateDiscount → PriceSnapshot 3분해. 쿠폰 없으면 discountAmount=0 경로. 단일 트랜잭션 (ADR-004 — 재고 비관락 + 쿠폰 낙관락 공존) | Unit L2 (Fake Repository + Clock.fixed, slow-unit) |
| Application (DTO) | `IssueCommand` | 발급 입력 (couponId, userId — 원시 타입). rule 20 | 값 보유 — CSVC 경유 |
| Application (DTO) | `CouponInfo` | 발급 결과 출력 — `CouponInfo.from(issue, coupon)` 평탄화 (couponIssueId, couponId, name, type, value, status, issuedAt). 도메인 객체 노출 차단 (rule 20) | CSVC-01 매핑 검증 경유 |
| Application (DTO) | `MyCouponInfo` | 내 쿠폰 목록 항목 — couponIssueId, name, type, value, minOrderAmount, expiredAt, **status(AVAILABLE/USED/EXPIRED 파생)**, usedAt. status 는 `now > coupon.expiredAt && AVAILABLE` 이면 EXPIRED 로 도출 (D-C3) | CSVC-07~10 경유 |
| Test Fixture | `FakeCouponRepository` | CouponRepository port 인메모리 구현 (MutableMap) — findById/save/findAll. Round 3 의 Fake 패턴 답습 | 자체 테스트 X — Service 픽스처 |
| Test Fixture | `FakeCouponIssueRepository` | CouponIssueRepository port 인메모리 구현 — findById/save/findByUserId/findByCouponId. **L2 에선 `@Version` 충돌을 재현하지 않음** (충돌은 실 DB 가 강제 — CC-08 L3 소관) | 자체 테스트 X — Service 픽스처 |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| CSVC-01 | Fake 에 Coupon C1(id=10, "여름 5천원", FIXED, 5000, expiredAt=2026-12-31), now=2026-06-17 | couponService.issue(IssueCommand(couponId=10, userId=1)) | CouponInfo 반환 — couponId == 10, name == "여름 5천원", status == AVAILABLE, issuedAt == now. FakeCouponIssueRepository 에 1건 저장 | slow-unit |
| CSVC-02 | Fake 에 couponId=999 미존재 | issue(IssueCommand(999, userId=1)) | CoreException(COUPON_NOT_FOUND → NOT_FOUND), 발급분 저장 0건 | slow-unit |
| CSVC-03 | C1 존재, 같은 유저 userId=1 이 두 번 발급 요청 | issue(10,1) 두 번 호출 | 잠정: 2건 발급 허용 (같은 템플릿 복수 발급 가능 — 1유저 1쿠폰 제약 미명세, specGap C-6). 각 CouponIssue 독립 (다른 id) | slow-unit |
| CSVC-04 | C1 의 expiredAt=2026-06-16 (이미 만료), now=2026-06-17 | issue(10, 1) (만료 쿠폰 발급 시도) | 잠정: 발급 거절 CoreException(COUPON_EXPIRED) 또는 발급은 허용하되 사용 시 거절 (specGap C-7 — 발급 시점 만료 검사 여부) | slow-unit |
| CSVC-05 | IssueCommand(couponId=10, userId=1) | command.couponId / command.userId | == 10 / == 1 (값 보유) | slow-unit |
| CSVC-06 | CouponInfo.from(issue, coupon) — issue(AVAILABLE), coupon(FIXED, 5000) | from 평탄화 결과 | 도메인 객체(CouponIssue/Coupon) 미노출, 원시 필드만 (rule 20) — type == FIXED, value == 5000 | slow-unit |
| CSVC-07 | Fake 에 userId=1 의 발급분 3건 — C1(AVAILABLE·미만료), C2(USED), C3(AVAILABLE·**만료**) | couponService.myCoupons(userId=1, page=0) | 3건 반환 — status: C1 == AVAILABLE, C2 == USED, **C3 == EXPIRED(파생)** (D-C3 — 저장은 AVAILABLE 이지만 now > expiredAt 으로 EXPIRED 노출) | slow-unit |
| CSVC-08 | userId=2 의 발급분 0건 | myCoupons(userId=2, page=0) | 빈 목록 (예외 아님) | slow-unit |
| CSVC-09 | C3(AVAILABLE·만료) 1건, now=만료 후 | myCoupons → C3 의 MyCouponInfo.status | == EXPIRED — 파생 로직이 응답 계층에서 작동 (배치 없음, D-C3). DB 의 coupon_issue.status 는 여전히 AVAILABLE (CIS-11 정합) | slow-unit |
| CSVC-10 | userId=1 발급분 5건, page size=2 | myCoupons(userId=1, page=0) → page=1 → page=2 | page0 2건, page1 2건, page2 1건 (페이지네이션 — 마지막 초과는 빈 목록) | slow-unit |
| CSVC-11 | C2(USED) 1건 | myCoupons → C2.status | == USED (만료 여부 무관 — USED 가 우선, CIS-06 정합) | slow-unit |
| RSVC2-01 | Fake: Property/RoomType/DailyRoom 가용 2박(합산 300000) + Coupon C1(FIXED,50000,minOrder=null) + userId=1 의 AVAILABLE 발급분 issue77, now 미만료 | reserve(userId=1, command(couponId=77)) | 정상 예약 — ReservationInfo.totalPrice == 250000 (300000 − 50000), 발급분 issue77.status == USED (markUsed 위임), DailyRoom reservedRooms 차감 | slow-unit |
| RSVC2-02 | RSVC2-01 픽스처에서 command.couponId=null | reserve(userId=1, command(couponId=null)) | 정상 예약 — totalPrice == 300000 (할인 0), 어떤 CouponIssue 도 markUsed 안 됨 (쿠폰 미적용 경로) | slow-unit |
| RSVC2-03 | RSVC2-01 픽스처, couponId=999 (미존재 발급분) | reserve(userId=1, command(couponId=999)) | CoreException(COUPON_NOT_FOUND → NOT_FOUND), 예약 저장 0건, 재고 비차감 (트랜잭션 롤백 의미 — Fake 에선 save 미호출 단언) | slow-unit |
| RSVC2-04 | issue77 의 소유주가 userId=2, 호출자 userId=1 | reserve(userId=1, command(couponId=77)) | CoreException(COUPON_NOT_OWNED → FORBIDDEN), 예약 저장 0건 (belongsTo 위임 — CIS-02) | slow-unit |
| RSVC2-05 | issue77 가 이미 USED (이전 예약에서 사용) | reserve(userId=1, command(couponId=77)) | CoreException(COUPON_ALREADY_USED → CONFLICT), 예약 저장 0건 (markUsed AVAILABLE 가드 — CIS-08) | slow-unit |
| RSVC2-06 | Coupon C1 의 expiredAt 이 now 이전 (만료), issue77 는 AVAILABLE | reserve(userId=1, command(couponId=77)) | CoreException(COUPON_EXPIRED), 예약 저장 0건 (만료 파생 거절 — CIS-09) | slow-unit |
| RSVC2-07 | Coupon C1(minOrderAmount=500000), 주문액 300000 (미달) | reserve(userId=1, command(couponId=77)) | CoreException(COUPON_MIN_ORDER_NOT_MET → BAD_REQUEST), 예약 저장 0건 (calculateDiscount minOrder 가드 — CPN-08) | slow-unit |
| RSVC2-08 | Coupon C1(RATE, 10%), 주문액 300000 | reserve(userId=1, command(couponId=77)) | totalPrice == 270000 (300000 − floor(30000)), priceSnapshot.discountAmount == 30000 | slow-unit |
| RSVC2-09 | Coupon C1(FIXED, 500000 > 주문액 300000) | reserve(userId=1, command(couponId=77)) | totalPrice == 0 (할인 클램프 min(500000,300000)==300000 → finalPrice 0, CPN-02 정합), 예약 정상 저장 | slow-unit |
| RSVC2-10 | 재고 매진 + 유효 쿠폰 (재고 실패가 쿠폰보다 먼저) | reserve(userId=1, command(couponId=77)) | CoreException(CONFLICT — 재고 부족). 쿠폰 markUsed **미호출** (재고 선검증이 쿠폰 사용보다 먼저 — 흐름 순서 단언, 의존 흐름 다이어그램 정합) | slow-unit |

> **L2 의 동시성 무재현 주의** — Fake Repository 는 `@Version` 충돌·비관락 대기·유니크 위반을 재현하지 않는다. RSVC2/CSVC 는 **단일 스레드 오케스트레이션 정확성**(호출 순서·할인 계산·실패 매핑)만 검증한다. 동시성 정합은 B.5 (실 MySQL) 가 단독으로 책임진다.

### B.5 동시성 — 실 MySQL + `ExecutorService`/`CountDownLatch` (L3, Round 4 핵심)

#### 인벤토리

| 계층 | 대상 | 책임 | 테스트 범위 |
|---|---|---|---|
| Infrastructure | `DailyRoomRepository.findForReserve` (port) ↔ `DailyRoomJpaRepository` (adapter) | **예약 전용 잠그는 finder** — `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `ORDER BY date ASC` (데드락 회피). port 는 의도만, 락 어노테이션은 adapter 에 (DIP — rule 19, ADR-004 §1). 검색·상세 finder(`findByRoomTypeAndDateBetween`)는 락 없이 유지 | Integration (L3, @Tag integration) |
| Domain/Infra | `CouponIssue.version` (`@Version`) | 낙관락 단위 — 같은 발급분 동시 markUsed 시 한 명만 커밋, 진 쪽 `OptimisticLockException` → 409 (ADR-004 §2) | Integration (L3) |
| Infrastructure | `Property.wish_count` 원자적 UPDATE | `UPDATE property SET wish_count = wish_count + 1 WHERE id=?` (취소 `- 1 WHERE wish_count > 0`). 상대 증가 — lost update 원천 불가 (ADR-004 §3). `WishlistService.add/remove` 가 도메인 `incrementWish()` 대신 원자적 쿼리 위임 | Integration (L3) |
| Infrastructure | `wishlist` UNIQUE(user_id, property_id) | 같은 유저 더블파이어 중복 INSERT 방어 — 행 락으로 못 막는 신규 INSERT 를 제약 계층에서 차단 (ADR-004 §3, Q3). 앱 선검사 2층 (rule 10) | Integration (L3) |
| Test Harness | `ConcurrencyTestSupport` | `ExecutorService`(고정 풀) + `CountDownLatch`(동시 출발 게이트) + 결과 수집(예외/성공 카운트). 각 스레드를 latch 로 일제히 release → 최대 경합 재현. `@SpringBootTest` + Testcontainers MySQL | (하니스 — 자체 테스트 X) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| CC-01 | DailyRoom (roomTypeId=20, 1박, totalRooms=1, reservedRooms=0), 동일 객실·일자에 N=10 스레드 동시 예약 | 10 스레드 reserve() 동시 발사 (CountDownLatch 일제 출발) | **성공 정확히 1건**, 나머지 9건 CoreException(CONFLICT — 재고 부족) 409. reserved_rooms == 1 (더블부킹 0). 비관락 검증 (ADR-004 §1) | integration |
| CC-02 | DailyRoom totalRooms=5, reservedRooms=0, N=20 스레드 동시 예약 | 20 스레드 동시 reserve() | 성공 정확히 5건, 실패 15건. reserved_rooms == 5 (오버부킹 0 — 한도 정확) | integration |
| CC-03 | 두 객실 기간이 **겹치는** 다일자 예약 — A=[7/1,7/2,7/3], B=[7/2,7/3,7/4] (7/2·7/3 공유), 각 totalRooms=1, 두 묶음을 교차로 동시 발사 | A·B 예약 스레드 동시 실행 | **데드락 없이** 정합 — ORDER BY date ASC 로 모두 작은 날짜부터 잠궈 사이클 불가 (ADR-004 §1). 둘 중 하나만 성공 또는 둘 다 성공(겹침 일자 재고 충분 시) — DeadlockLoserDataAccessException 0건 | integration |
| CC-04 | 다일자 예약 — 마지막 날짜만 매진(7/3 reserved==total), 7/1·7/2 는 여유, N 스레드 동시 | 동시 reserve(period=7/1~7/4) | 전건 실패(CONFLICT) + **7/1·7/2 재고 비차감** (all-or-nothing — consumeAll 선검증, SAS-11 의 동시성판). 부분 차감 0 | integration |
| CC-05 | 같은 발급분 issue77(AVAILABLE), 같은 유저가 N=5 스레드로 동시 예약(같은 쿠폰 사용) | 5 스레드 동시 reserve(couponId=77) | **issue77 정확히 1회 USED**, 나머지 4건 실패 (OptimisticLockException → 409 또는 COUPON_ALREADY_USED). usedAt 단일 시각. 낙관락 검증 (ADR-004 §2) | integration |
| CC-06 | 같은 쿠폰으로 동시 사용 패자 트랜잭션 | CC-05 의 실패 스레드 결과 | 패자의 예약은 저장 안 됨 + 재고 비차감 (쿠폰 충돌 시 트랜잭션 롤백 — 쿠폰·재고 동일 트랜잭션 정합). 더블유즈 0 | integration |
| CC-07 | 같은 숙소 P1(wish_count=0), N=50 명 서로 다른 유저가 동시 찜 | 50 스레드 동시 wishlistService.add() | wish_count == 50 (원자적 +1 — lost update 0, ADR-004 §3). wishlist row 50건 | integration |
| CC-08 | P1(wish_count=10), N=10 명이 동시 찜 취소(찜 보유 상태) | 10 스레드 동시 remove() | wish_count == 0 (원자적 −1, `WHERE wish_count > 0` 하한 가드 — 음수 0건) | integration |
| CC-09 | 같은 유저 U1 이 같은 숙소 P1 을 N=10 스레드로 동시 찜(더블파이어) | 10 스레드 동시 add(U1, P1) | **wishlist row 정확히 1건** (UNIQUE(user_id, property_id) — DataIntegrityViolationException 은 멱등 처리되거나 1건만 성공), wish_count == 1 (중복 카운트 부풀림 0, ADR-004 §3 / Q3) | integration |
| CC-10 | U1·U2·U3 이 각자 P1 을 동시 찜 + U1 은 더블파이어 (혼합) | 혼합 동시 발사 | wishlist row 3건(U1·U2·U3 각 1건), wish_count == 3 — 유저별 유니크 + 카운터 정확 동시 성립 | integration |
| CC-11 | DailyRoom totalRooms=3, 동시 예약 10건 + 그 중 일부 취소 동시 발사 (consume·release 교차) | reserve·cancel 혼합 동시 | reserved_rooms 가 최종 성공 예약 수와 정확히 일치 (release 도 ASC 정렬 락 — reserve 와 동일 순서로 데드락 회피, ADR-004 §1) | integration |
| CC-12 | 검색/상세 읽기 경로(`findByRoomTypeAndDateBetween`)가 예약 비관락과 동시 | 예약 진행 중 동일 객실 상세 조회 | 읽기 경로는 **락 없이 진행** (FOR UPDATE 미적용 — finder 분리, ADR-004 §1). 조회가 예약 락 대기에 묶이지 않음 (읽기 핫 경로 보호) | integration |
| CC-13 | minOrderAmount 충족 쿠폰 + 같은 발급분 동시 사용 + 재고도 동시 경합 (쿠폰·재고 락 공존) | 동시 reserve(couponId 공유, 재고 한정) | 쿠폰 1회 USED **그리고** 재고 한도 내 — 두 락(낙관·비관)이 같은 트랜잭션에서 무충돌 공존 (ADR-004 — 행·기법 상이) | integration |
| CC-14 | 환경 분류 가드 — Docker 미가용 시 본 절 전체 | `clean ktlintCheck build -x test` + L1/L2 회귀 | 컴파일·lint·assemble 통과 / L3 실행 차단 → **"코드 회귀 아님" 분리 보고** (rule 14 환경 이슈 분류 — `DockerClientProviderStrategy` / `Could not find a valid Docker environment` 식별) | integration |

> **환경 차단 보고 원칙 (rule 14 / 17)** — 본 절은 Testcontainers MySQL 필수. 로컬 Docker Desktop 비호환([[testcontainers-docker-desktop-incompat]]) 시 **컴파일 통과 / 실행 차단** 을 분리 보고한다. `AssertionError`·`Unresolved reference` 면 코드 회귀, `Could not find a valid Docker environment` 면 환경 의존 실패. CI 정상 환경(또는 환경 정상화 후) 에서 CC-01~13 실행이 본 라운드 학습 목표의 완결.

### B.6 어드민 API — `AdminCouponV1Controller` (CRUD + 발급내역) · 헤더 인증 인터셉터 (L4)

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Interfaces (admin) | `AdminCouponV1Controller` | `/api-admin/v1/coupons` — POST(생성)·GET(목록·페이지)·GET/{id}(상세)·PUT/{id}(수정)·DELETE/{id}(삭제) + GET/{couponId}/issues(발급내역·페이지). `ApiResponse` envelope (rule 09). stay-api 내 경로 분리 (Q6 — 모듈 미분리) | E2E (L4, @Tag e2e) |
| Interfaces (admin) | `AdminAuthInterceptor` | `X-Loopers-Ldap` 헤더 스텁 검사 — 값 없음·불일치 시 CoreException(UNAUTHORIZED → 401). `/api-admin/**` 경로에만 적용 (Q6 — LDAP 실연동 미수행, 스텁) | E2E (L4) |
| Interfaces (v1) | `CouponV1Controller` (고객) | POST `/api/v1/coupons/{couponId}/issue`(발급) · GET `/api/v1/users/me/coupons`(내 쿠폰 — status 파생 포함). `X-USER-ID` 헤더 (기존 컨벤션 유지, Q6) | E2E (L4) |
| Interfaces (DTO) | `AdminCouponV1Dto` / `CouponV1Dto` | Request/Response — String/원시 타입, `V1` 버저닝 (rule 20). 도메인 객체 미노출 | E2E 경유 |
| support/error | `ErrorType.UNAUTHORIZED` (신설) | 어드민 인증 실패 401 (Q6). 쿠폰 에러 5종(COUPON_NOT_FOUND·ALREADY_USED·EXPIRED·NOT_OWNED·MIN_ORDER_NOT_MET)도 신설 — advice 매핑 (rule 09) | (ErrorType — CPN/CIS/RSVC2 가 간접 검증) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| ADM-01 | 유효한 X-Loopers-Ldap 헤더, CreateCouponRequest(name="가을 1만원", type=FIXED, value=10000, minOrderAmount=50000, expiredAt) | POST /api-admin/v1/coupons | 200 + ApiResponse.data.couponId 발급, DB 에 coupon 1건. meta.result == SUCCESS | e2e |
| ADM-02 | X-Loopers-Ldap 헤더 **없음**, 동일 생성 요청 | POST /api-admin/v1/coupons | 401 UNAUTHORIZED (인터셉터 차단 — 컨트롤러 미도달, Q6). coupon 저장 0건 | e2e |
| ADM-03 | X-Loopers-Ldap 헤더 **불일치 값**, 생성 요청 | POST /api-admin/v1/coupons | 401 UNAUTHORIZED (스텁 검증 실패) | e2e |
| ADM-04 | coupon 3건 사전 등록, 유효 헤더 | GET /api-admin/v1/coupons?page=0&size=2 | 200 + items 2건 + pagination(전체 3, 다음 페이지 존재). 페이지네이션 계약 | e2e |
| ADM-05 | couponId=10 등록, 유효 헤더 | GET /api-admin/v1/coupons/10 | 200 + data.name·type·value·minOrderAmount·expiredAt 정확 노출 | e2e |
| ADM-06 | couponId=999 미존재, 유효 헤더 | GET /api-admin/v1/coupons/999 | 404 COUPON_NOT_FOUND (advice 매핑) | e2e |
| ADM-07 | couponId=10 등록, UpdateRequest(value=15000) | PUT /api-admin/v1/coupons/10 | 200 + 재조회 시 value == 15000. (어드민 수정이 기발급분에 소급되는지는 PriceSnapshot 으로 차단 — Q5 메모) | e2e |
| ADM-08 | couponId=10 등록 | DELETE /api-admin/v1/coupons/10 | 200 + 재조회 GET /10 → 404. coupon 삭제 | e2e |
| ADM-09 | couponId=10 에 발급분 3건(유저 1·2·3) 존재, 유효 헤더 | GET /api-admin/v1/coupons/10/issues?page=0&size=10 | 200 + issues 3건 (userId·status·issuedAt 노출) + 페이지네이션 | e2e |
| ADM-10 | 고객 X-USER-ID=1, couponId=10 존재 | POST /api/v1/coupons/10/issue | 200 + data.couponIssueId 발급, coupon_issue 1건(status AVAILABLE). 고객 발급 경로 (CSVC-01 의 E2E) | e2e |
| ADM-11 | X-USER-ID=1 의 발급분 — AVAILABLE 1·USED 1·만료(파생 EXPIRED) 1 | GET /api/v1/users/me/coupons | 200 + 3건, status: AVAILABLE/USED/**EXPIRED**(파생 노출 — D-C3, CSVC-09 의 E2E). DB coupon_issue.status 는 AVAILABLE/USED 만 | e2e |
| ADM-12 | 환경 분류 가드 — Docker 미가용 시 본 절 전체 | `clean ktlintCheck build -x test` | 컴파일·lint 통과 / L4 실행 차단 → "코드 회귀 아님" 분리 보고 (rule 14 — B.5 와 동일 원칙) | e2e |

---

## C. 통합 사이클 순서 (도메인 L1 → application L2 → infra/락 L3 → API L4 → 동시성 L3)

의존 방향 순. 각 사이클은 rule 14/15 의 Red→Green→Refactor + 단계별 독립 승인. **[게이트]** 표시는 해당 사이클 Red 진입 전 확정해야 하는 결정 (Part E). 동시성(B.5)을 **마지막에 배치**한 이유: 검증 대상(reserve·찜·쿠폰 경로) 이 도메인·application·infra 로 먼저 완성돼야 동시성 하니스가 실제 흐름을 발사할 수 있기 때문 (단위→통합→동시성).

| Cycle | 대상 | 카탈로그 | 등급 | 게이트 |
|---|---|---|---|---|
| 1 | `CouponType` enum + `Coupon.calculateDiscount` (FIXED/RATE/끝전/minOrder) | CPN-01~11 | L1 | **[C-1·C-2]** 끝전 내림 / minOrder 경계 |
| 2 | `CouponIssueStatus` enum + `CouponIssue.issue`/`belongsTo`/`isUsable` | CIS-01~06·11·12 | L1 | **[C-3]** EXPIRED 파생·경계 |
| 3 | `CouponIssue.markUsed` (AVAILABLE 가드·만료·이중사용) | CIS-07~10·13 | L1/L2 | **[C-3]** markUsed 의 expiredAt 전달·매핑 |
| 4 | `PriceSnapshot` 3금액 분해 (+ Round 3 회귀) | RSV2-01~06 | L1 | **[C-4]** 생성 시그니처·음수 floor |
| 5 | `Reservation` + `couponId` (+ Round 3 RSV 회귀) | RSV2-07~13 | L1 | **[C-5]** 환불 기준 금액 (finalPrice) |
| 6 | `CouponService.issue` + `IssueCommand`/`CouponInfo` (Fake) | CSVC-01~06 | L2 | **[C-6·C-7]** 복수 발급·발급 시점 만료 |
| 7 | `CouponService.myCoupons` + `MyCouponInfo` status 파생 | CSVC-07~11 | L2 | **[C-3]** EXPIRED 파생 노출 |
| 8 | `ReservationService.reserve` 쿠폰 적용 정상 경로 | RSVC2-01·02·08·09 | L2 | **[C-4·C-5]** |
| 9 | `ReservationService.reserve` 쿠폰 예외 경로 | RSVC2-03~07·10 | L2 | **[C-3]** 만료/소유/이미사용 매핑 |
| 10 | `DailyRoomRepository.findForReserve` 비관락 finder + adapter | (infra — CC-01 전제) | L3 | **[Q1/ADR-004 §1]** 락 어노테이션 adapter 배치 |
| 11 | `CouponIssue` `@Version` 영속 매핑 | (infra — CC-05 전제) | L3 | **[Q2/ADR-004 §2]** |
| 12 | `Property.wish_count` 원자적 UPDATE + `wishlist` UNIQUE 마이그레이션 | (infra — CC-07·09 전제) | L3 | **[Q3/ADR-004 §3]** |
| 13 | `ErrorType` 신설 (UNAUTHORIZED + 쿠폰 5종) + advice 매핑 | (support — ADM/RSVC2 전제) | L1 | **[Q6]** OptimisticLock·DataIntegrity advice |
| 14 | `AdminAuthInterceptor` 헤더 스텁 + `AdminCouponV1Controller` CRUD | ADM-01~08 | L4 | **[Q6]** 경로 분리·헤더 컨벤션 |
| 15 | 어드민 발급내역 + 고객 발급/내쿠폰 컨트롤러 | ADM-09~12 | L4 | **[C-3]** |
| 16 | **동시성 하니스** `ConcurrencyTestSupport` + 재고 비관락 동시성 | CC-01~04·11·12 | L3 | **[Q1]** |
| 17 | 쿠폰 낙관락 + 찜 원자증가/유니크 동시성 | CC-05~10·13·14 | L3 | **[Q2·Q3]** |

---

## D. 관찰·면접 포인트 메모

- **한 트랜잭션에 락 도구 셋, 작업 모양으로 분기** — Round 4 의 핵심 서사. 재고(비관락·*일부 거절*)·쿠폰(낙관락·*1명만*)·찜(원자증가·*전원 성공*) 이 같은 `reserve()` `@Transactional` 안에 공존한다 (ADR-004). "왜 여기만 다른가"의 답이 "동시 요청들에게 무엇이 일어나야 하는가"라는 단일 기준으로 일관된다.
- **같은 원자적 UPDATE 가 재고엔 부적합·찜엔 정답** — 재고는 "가격 읽기+검사+차감"의 합성 작업이라 단일 `+1` 로 안 떨어져 비관락(대안 D 기각, ADR-004), 찜은 순수 상대 증가라 원자 UPDATE 가 정답. 같은 도구가 작업 모양에 따라 정반대 판정 — Q3 의 "멋진 대칭".
- **락으로 못 막는 영역을 제약 계층으로** — 같은 유저 더블파이어 찜 중복은 신규 INSERT 라 *삽입 전 잠글 행이 없어* `FOR UPDATE` 로 못 막는다. UNIQUE(user_id, property_id) 만이 무조건적 최종 보루 (Q3 리서치 — Doyensec/thoughtbot). 이미 loginId 유일성(rule 10) 에 쓰던 패턴이라 일관.
- **EXPIRED 를 저장하지 않는 선택** — 만료를 배치로 status 를 바꾸지 않고 `now > expiredAt` 파생 판정 (D-C3). 배치 인프라 없이 정합 보장, 시점 의존을 응답 계층에 가둠 (Clock 주입). 저장 상태(AVAILABLE/USED 2종)와 표시 상태(3종)의 분리.
- **PriceSnapshot 3분해 = 결제 금액 정합성의 감사 추적** — 할인전/할인액/최종을 한 스냅샷에 박아 "어떤 쿠폰이 얼마를 깎아 얼마가 최종인가"를 예약 시점 값으로 보존 (어드민이 나중에 쿠폰을 수정해도 예약은 당시 값 유지 — Q5). `totalPrice` 가 finalPrice 로 의미 명확화.
- **데드락은 "락 획득 순서 고정"으로 푼다** — 다일자 재고의 핵심 위험. 모든 트랜잭션이 `ORDER BY date ASC` 로 작은 날짜부터 잠그면(InnoDB 인덱스 스캔 순서와 일치) 겹치는 기간이 동시에 와도 사이클 불가 (CC-03·11). reserve·cancel 동일 순서.
- **L2 Fake 는 동시성을 못 본다 — L3 실 DB 가 단독 책임** — `@Version` 충돌·비관락 대기·유니크 위반은 Fake 로 재현 불가. L2(RSVC2/CSVC)는 오케스트레이션 정확성, L3(CC)는 동시성 정합으로 책임이 명확히 갈린다. 테스트 등급(rule 17)이 "무엇을 보장하는가"를 가시화.

---

## E. 정책 결정 대기

### E.1 사이클 게이트 (Red 진입 전 확정)

> Q1~Q6 (락 전략·쿠폰 도메인·어드민) 은 [`03-questions.md`](./03-questions.md) 에서 **이미 확정** — 본 카탈로그의 락·Aggregate·어드민 구조는 그 결정을 따른다. 아래는 카탈로그 작성 중 드러난 **세부 잠정 가정**(C-1~C-7) 으로, 해당 Cycle Red 직전 재확인한다.

| ID | 질문 | 잠정 채택 | 막는 사이클 |
|---|---|---|---|
| **C-1** | RATE 할인 끝전 처리 — `floor`(내림) / round / ceil? 음수 발생 불가 전제? | **내림(floor)** — `floor(pre × value / 100)` (CPN-05: 99999×10% == 9999). FIXED 의 min 클램프와 결합 시 결제액 초과 불가 | 1, 8 |
| **C-2** | minOrderAmount 경계 — `pre >= minOrderAmount` 면 충족? `null` 이면 검사 생략? 미달 시 에러 코드? | **`>=` 충족 + null 생략 + COUPON_MIN_ORDER_NOT_MET(→BAD_REQUEST)** (CPN-08·09·10) | 1, 9 |
| **C-3** | EXPIRED 파생 — ① `CouponIssueStatus` 에 EXPIRED 를 enum 으로 두되 저장만 안 하나, 아예 enum 부재인가? ② `markUsed` 가 `expiredAt` 을 인자로 받나 Service 가 Coupon 동반 검증 선행하나? ③ 만료 경계 `now <= expiredAt` 까지 유효? ④ COUPON_EXPIRED 의 status 매핑(BAD_REQUEST vs CONFLICT)? | **① enum 부재(저장 2종) + 응답 계층 파생 ② markUsed(now)+isUsable(now,expiredAt) 분리, Service 가 Coupon 로드 후 동반 검증 ③ `now <= expiredAt` 유효(초과부터 만료) ④ COUPON_EXPIRED→BAD_REQUEST** (D-C3, CIS-04·05·09) — 정본 우선, 세부는 Red 직전 고정 | 2, 3, 7, 9 |
| **C-4** | `PriceSnapshot` 생성 시그니처 — `of(entries, discountAmount)` 팩토리로 3금액 자동 도출? 음수 floor 위치? 빈 entries 회귀? | **`of(entries, discountAmount)` 팩토리 + finalPrice 0 floor + 빈 entries 는 PS-04 회귀 유지** (RSV2-01~06). 기존 `PriceSnapshot(entries)` 1-인자 생성자도 호환 보존(discountAmount=0) | 4, 8 |
| **C-5** | 환불 기준 금액 — 쿠폰 적용 예약 취소 시 환불은 `finalPrice`(실결제액) 기준? `priceBeforeDiscount`(할인 전)? | **`finalPrice`(실결제액=totalPrice) 기준** — 사용자가 실제 낸 금액을 환불 정책에 적용 (RSV2-10). 쿠폰 복원(USED→AVAILABLE) 은 범위 밖(미도입) | 5 |
| **C-6** | 같은 유저·같은 템플릿 복수 발급 허용? (1유저 1쿠폰 제약?) | **복수 발급 허용** (제약 미명세 — 각 CouponIssue 독립, CSVC-03). 1유저 1쿠폰 요구 생기면 발급 시 유니크 추가 | 6 |
| **C-7** | 발급 시점 만료 검사 — 이미 만료된 Coupon 을 발급 거절? 발급은 허용하되 사용 시 거절? | **잠정: 발급은 허용 + 사용 시 거절** (CSVC-04 — 발급/사용 책임 분리. 만료 쿠폰 발급 거절이 UX 상 낫다는 반론 시 Red 직전 전환) | 6 |

### E.2 정본 대비 잠정 재검토 트리거 (Phase 진행 중 누적)

| 트리거 | 재검토 대상 | 출처 |
|---|---|---|
| 어드민이 기발급분에 소급 영향을 주는 게 문제되면 | `CouponIssue` 참조 방식 → **스냅샷 승격** (발급 시점 type/value/minOrderAmount 복제) | Q5 (Phase 1 재검토 메모) |
| 1유저 1쿠폰 정책 요구 발생 | 발급 시 `UNIQUE(coupon_id, user_id)` 추가 (C-6) | C-6 |
| 만료 쿠폰 UX 개선 요구 | 발급 시점 만료 거절로 전환 (C-7) | C-7 |
| 멀티 인스턴스 배포 | 비관락 → 분산락/Redis 재검토 | ADR-004 재검토 트리거 ① |
| PG 결제 도입 | 트랜잭션 분리 + PENDING 도입 (ADR-002 재검토) | Q4 / ADR-004 ③ |

---

## F. 의도적 제외 (Round 4 범위 밖 — 발제 대비 명시)

| 제외 항목 | 사유 | 재개 시점 |
|---|---|---|
| PG 결제 (PENDING 상태·결제 승인 대기) | 쿠폰·재고는 `reserve()` 단일 트랜잭션 동기 처리 — 외부 비동기 공백 없음. PENDING 도입 시 CONFIRMED 전이 트리거 부재로 정합 깨짐 (Q4 / ADR-002). `ReservationStatus.PENDING` 은 enum 자리만 유지 | 결제 라운드 |
| 어드민 Property/RoomType CRUD API | Round 4 어드민 범위는 **쿠폰 CRUD + 발급내역** 으로 한정 (Q6). Property/RoomType 도메인 메서드(PRP-04~07)는 Round 3 에 존재하나 어드민 API 화는 범위 밖 | 후속 (어드민 확장 라운드) |
| LDAP 실연동 | 인증은 `X-Loopers-Ldap` 헤더 **스텁 인터셉터** (값 검사 + 401). 실 LDAP 은 이번 핵심(동시성)에서 벗어나 과투자 (Q6) | 인증 실분리 요구 시 ADR |
| `:apps:stay-admin` 모듈 분리 | stay-api 내 `/api-admin/v1` 경로 분리로 충분 (Q6) — 배포·인증 실분리 요구 시 ADR 트리거 | 배포 분리 요구 시 |
| 쿠폰 복원 (예약 취소 시 USED→AVAILABLE) | 환불은 finalPrice 기준만 (C-5). 쿠폰 재사용 가능화는 정책 미명세 | 정책 요구 시 |
| 좌석 단위 예약 (발제 좌석 예매 예시) | 본 도메인은 객실 단위(`DailyRoom`). 발제의 좌석 예시는 낙관락 설명용 — 쿠폰 단일사용에 같은 철학 적용 (Q2) | (도메인 불일치 — 미도입) |

### 발제 대비 커버리지 (반영 현황)

- **동시성(발제 핵심)**: 재고 비관락(CC-01~04·11·12)·쿠폰 낙관락(CC-05·06·13)·찜 원자증가+유니크(CC-07~10) — "각 도메인 특성에 맞는 전략" 발제 요구를 ADR-004 3구간으로 직접 커버.
- **트랜잭션**: 쿠폰·재고가 같은 `@Transactional` 안에서 정합(CC-06·13), 실패 시 롤백(RSVC2-03~07·CC-04).
- **쿠폰 도메인**: 2 Aggregate + ID 참조(Q5) + 할인 계산(CPN) + 단일사용 낙관락(CIS·CC-05) + 발급/내쿠폰(CSVC).
- **어드민**: 쿠폰 CRUD + 발급내역(ADM-01~09) + 헤더 인증(ADM-02·03).
- **환경 제약**: L3/L4(CC-·ADM-) 는 Docker 의존 — `build -x test` + L1/L2 회귀로 "코드 회귀 아님" 분리 보고 (rule 14 / 17, CC-14·ADM-12).

---

## G. 변경 이력

- 2026-06-17 v1: Round 4 TDD 계획 신규 작성 — 컴포넌트 인벤토리(B.1~B.6) + 검증 카탈로그(CPN 11·CIS 13·RSV2 13·CSVC 11·RSVC2 10·CC 14·ADM 12 = **84건**) + 통합 사이클 17 + 결정 게이트(C-1~C-7, E.2 재검토 트리거). 정본(쿠폰 2 Aggregate·EXPIRED 파생·PriceSnapshot 3분해·구간별 락) 을 `03-questions.md` Q1~Q7 + ADR-004 와 일치하도록 반영. 핵심 = B.5 동시성 L3(실 MySQL).
