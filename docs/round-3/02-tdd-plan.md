# Round 3 — 도메인 모델·아키텍처 TDD 작업 계획 (v1)

> **목적**
> (A) 무엇을 만들지 = 구현 대상 컴포넌트 인벤토리
> (B) 무엇을 검증할지 = 컴포넌트별 "입력 → 예상 답변" 테스트 케이스 카탈로그 (총 183건)
> (C) 어떤 순서로 = 의존 방향 기준 통합 사이클 순서 (28 사이클)
>
> **선행 문서:** [`docs/design/03-class-diagram.md`](../design/03-class-diagram.md) (LLD — 시그니처의 단일 근거), [`docs/design/02-sequence-diagrams.md`](../design/02-sequence-diagrams.md), [`docs/curriculum/round-3-quest.md`](../curriculum/round-3-quest.md) (발제 체크리스트), ADR-001/002/003
> **결정 누적:** [`03-questions.md`](./03-questions.md)
>
> **산출 경위 (2026-06-10):** 멀티에이전트 워크플로 — 도메인별 드래프터 5 + Domain Service 필요성 분석 1 (병렬) → 초안별 적대적 검증자 5 (LLD·발제 대비 보정) → 전역 비평자 1 (체크리스트 커버리지·ID 충돌·사이클 순서). 총 12 에이전트. Red 단계가 "명세 기반으로 어떤 걸 검증할 것인가" 라는 정의(rule 14, Q1)에 따라, 카탈로그 자체를 명세 검증 산출물로 취급한다.

---

## A. 구현 대상 — 컴포넌트 인벤토리 (요약)

상세 인벤토리는 Part B 의 각 절 머리에 있다. 모든 도메인 코드는 `modules/domain` (rule 01), Application 은 `apps/stay-api` (rule 19/20).

| # | 컴포넌트 | 핵심 클래스 | 카탈로그 |
|---|---|---|---|
| B.1 | Property Aggregate | `Property`(AR), `RoomType`(내부 Entity), `Address`·`CancellationPolicy`·`RefundRule`·`BedConfiguration`·`BedEntry`(VO) + enum 6종 | 50건 (PRP·RT·ADDR·CP·BED) |
| B.2 | DailyRoom Aggregate | `DailyRoom`(AR), `DailyRoomId`(복합 PK VO) | 26건 (DR·DRID) |
| B.3 | Reservation Aggregate | `Reservation`(AR, private ctor + `confirm` 팩토리), `DateRange`·`GuestInfo`·`PriceSnapshot`·`DailyPriceEntry`·`CancellationPolicySnapshot`·`CancellationResult`(VO) | 41건 (RSV·RNG·GI·PS·CPS) |
| B.4 | Wishlist | `Wishlist`(조인 엔티티), `WishlistId`(복합 PK VO) | 4건 (WL) |
| B.5 | Application Layer | `PropertyService`·`WishlistService`·`ReservationService` + Command/Info DTO (rule 20), Fake Repository 기반 L2 | 49건 (PSVC·WSVC·RSVC) |
| B.6 | StayAvailabilityService (Domain Service) | `StayAvailabilityService` — 기간 완전성·전 일자 가용성·견적(`quote`)·`consumeAll`/`releaseAll` (Q3 확정) | 13건 (SAS) |

### 의존 흐름 (예약 1건 — Q3 확정: `StayAvailabilityService` 확장 도입 반영)

```
ReservationService.reserve(ReserveCommand)
  → DateRange(checkIn, checkOut) / GuestInfo(...) VO 화        // 검증 발동 (rule 06)
  → PropertyRepository.findById → property.findRoomType(roomTypeId)
  → DailyRoomRepository.findByRoomTypeAndDateBetween(...)
  → stayAvailabilityService.consumeAll(period, dailyRooms)      // 기간 완전성·가용성 선검증 후 all-or-nothing 차감 (Domain Service)
  → val priceSnapshot = stayAvailabilityService.quote(period, dailyRooms)
  → Reservation.confirm(userId, property, roomType, period, guestInfo, priceSnapshot, now)
  → ReservationRepository.save(reservation)
  ← ReservationInfo.from(reservation)
```

### 설계 결정 메모

- **D-B1 Domain Service 후보 분석**: 6개 후보 중 `StayAvailabilityService` (기간 완전성 검증 + 전 일자 가용성 검사 + 요금 합산) 1개만 도입 적격 — 3관문 (단일 Aggregate 불가 / Repository 무의존 / 무상태) 통과. 나머지 5개 (예약 오케스트레이션·검색 정렬·찜 동기화·환불 계산·range 펼치기) 는 기각 사유 명시. Round 1 D-A3 (도메인 서비스 미도입) 의 논리와 모순 없음 — "Repository 없는 다중 Aggregate 협력 규칙" 이 Round 3 에 처음 등장해 도입 조건이 비로소 충족. **→ 확정 (2026-06-10, Q3): 확장 도입** — B.6 카탈로그 SAS-01~13 증보 완료, `confirm` 시그니처는 `PriceSnapshot` 수취로 변경.
- **D-B2 LLD 내부 불일치 발견**: §4.2 다이어그램의 `ReservationFactory.confirm` 은 ID+객체 중복 수취, §4.4 코드는 객체만 수취. 본 카탈로그는 §4.4 기준. Q-B 확정 후 `03-class-diagram.md` 정정 예정 (견적 VO 채택 시 양쪽 모두 변경).
- **D-B3 CP-/CPS- 의미론 단일 소유**: 환불 경계 의미론 (경계일 포함·달력일 기준·끝전·매칭 부재) 은 `CP-06~12·15·16` 이 단일 소유자. `CPS-` 는 "원본과 동일 결과" 변환 정합 + Snapshot 고유 케이스로 축소 적용. 두 specGap 묶음은 **Q-A 단일 결정표**로 동시 확정 (이중 추적 시 같은 경계가 다른 답으로 고정될 위험 — 전역 비평).
- **D-B4 Round 3 범위 = L1/L2**: 영속 (JPA 매핑·L3)·E2E (L4)·동시 예약 race (rule 10 ② DB 안전망 검증) 는 의도적 제외 — Part F.
- **D-B5 prefix 정리**: DateRange 는 `DRG-` 대신 **`RNG-`** 사용 (DailyRoom 계열 `DR-`·`DRID-` 와의 시각 혼동 방지 — 전역 비평 권고). 카탈로그 ID 검색은 항상 하이픈 포함 (`RSV-` vs `RSVC-`).

---

## B. 검증 카탈로그 — 컴포넌트별 "입력 → 예상 답변"

형식은 Round 1 과 동일: **(ID · Given · When · Then)** + `@Tag` (rule 17). ID 는 테스트 `@DisplayName` 접두로 사용 (rule 14). Then 안의 "가정 (specGap ...)" 표기는 Part E 의 잠정 결정 항목 — 해당 Cycle Red 진입 전 확정한다.

### B.1 Property Aggregate — `Property`(AR) · `RoomType`(내부 Entity) · 소속 VO

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (VO) | `Address` | 숙소 주소 표현 (detailAddress, zipCode) — 생성자 검증 + 값 동등성 (rule 06) | Unit (L1, @Tag unit) |
| Domain (VO) | `BedEntry` | 침대 1종 구성 (BedType type, Int count) — count 하한 검증 | Unit (L1, @Tag unit) |
| Domain (VO) | `BedConfiguration` | RoomType 의 침대 구성 (List<BedEntry> entries) 합성 | Unit (L1, @Tag unit) |
| Domain (VO) | `RefundRule` | 환불 규칙 1건 (daysBeforeCheckIn, refundRate) — 값 범위 검증 | Unit (L1, @Tag unit) |
| Domain (VO) | `CancellationPolicy` | 현재 취소 정책. rules 기반 refundAmount(totalPrice, cancelledAt, checkIn) 계산 + snapshot() 으로 CancellationPolicySnapshot 변환 (LLD §2.2) | Unit (L1, @Tag unit) |
| Domain (Entity, AR 아님) | `RoomType` | Property 내부 Entity. 기준/최대 인원 보유, canAccommodate(guestCount) 수용 판단 + updateInfo(cmd). 외부 직접 수정 불가 — Property 경유만 (LLD §2.3) | Unit (L1, @Tag unit) |
| Domain (Aggregate Root) | `Property` | 숙소 AR. 위치(city·address)·편의시설·찜 수 보유 (발제 체크리스트), roomTypes composition 관리 (addRoomType 의 이름 중복 금지 불변식 / updateRoomType / removeRoomType / findRoomType), updateInfo, wishCount 카운터 증감 (incrementWish/decrementWish) | Unit (L1, @Tag unit) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| ADDR-01 | detailAddress="테헤란로 152 강남파이낸스센터", zipCode="06236" | Address(detailAddress, zipCode) | 정상 생성, .detailAddress == "테헤란로 152 강남파이낸스센터", .zipCode == "06236" | unit |
| ADDR-02 | 동일 값 ("테헤란로 152", "06236") 으로 생성한 두 인스턴스 | addr1 == addr2 | true — VO 값 동등성 (data class equals, rule 06 의 VO 일원화 전제) | unit |
| ADDR-03 | detailAddress="" (빈 문자열), zipCode="06236" | Address(detailAddress, zipCode) | CoreException(BAD_REQUEST) — 공백 상세주소 불허 가정 (specGap G-9) | unit |
| ADDR-04 | detailAddress="테헤란로 152", zipCode="0623" (4자리) | Address(detailAddress, zipCode) | CoreException(BAD_REQUEST) — 우편번호 5자리 숫자 가정 (specGap G-9) | unit |
| ADDR-05 | detailAddress="테헤란로 152", zipCode="0623A" (비숫자 포함) | Address(detailAddress, zipCode) | CoreException(BAD_REQUEST) | unit |
| BED-01 | type=BedType.DOUBLE, count=1 (하한 경계) | BedEntry(type, count) | 정상 생성, .type == DOUBLE, .count == 1 | unit |
| BED-02 | type=BedType.SINGLE, count=0 | BedEntry(type, count) | CoreException(BAD_REQUEST) — count >= 1 가정 (specGap G-9) | unit |
| BED-03 | type=BedType.KING, count=-1 | BedEntry(type, count) | CoreException(BAD_REQUEST) | unit |
| BED-04 | entries=[BedEntry(DOUBLE,1), BedEntry(SINGLE,2)] | BedConfiguration(entries) | 정상 생성, .entries.size == 2, 순서·값 보존 | unit |
| BED-05 | entries=emptyList() | BedConfiguration(entries) | CoreException(BAD_REQUEST) — 침대 0개 구성 불허 가정 (specGap G-9) | unit |
| BED-06 | 동일 entries=[BedEntry(DOUBLE,1)] 로 생성한 두 BedConfiguration | config1 == config2 | true — VO 값 동등성 | unit |
| CP-01 | daysBeforeCheckIn=7, refundRate=100 (상한 경계) | RefundRule(7, 100) | 정상 생성, .daysBeforeCheckIn == 7, .refundRate == 100 | unit |
| CP-02 | daysBeforeCheckIn=0, refundRate=0 (둘 다 하한 경계) | RefundRule(0, 0) | 정상 생성 — 당일 0% 규칙 표현 가능 | unit |
| CP-03 | daysBeforeCheckIn=-1, refundRate=50 | RefundRule(-1, 50) | CoreException(BAD_REQUEST) — daysBeforeCheckIn >= 0 가정 (specGap G-4) | unit |
| CP-04 | daysBeforeCheckIn=7, refundRate=101 | RefundRule(7, 101) | CoreException(BAD_REQUEST) — refundRate <= 100 가정 (specGap G-4) | unit |
| CP-05 | daysBeforeCheckIn=7, refundRate=-1 | RefundRule(7, -1) | CoreException(BAD_REQUEST) — refundRate >= 0 가정 (specGap G-4) | unit |
| CP-06 | policy=rules[(7일전,100%),(3일전,50%),(0일전,0%)], totalPrice=300000, checkIn=2026-07-10, cancelledAt=2026-06-30T10:00 (10일 전) | policy.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 300000 (100% 환불) | unit |
| CP-07 | 동일 policy, cancelledAt=2026-07-03T10:00 (정확히 7일 전 — 경계일) | policy.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 300000 — 경계일 당일은 높은 환불율 포함 가정 (specGap G-1) | unit |
| CP-08 | 동일 policy, cancelledAt=2026-07-04T10:00 (6일 전 — 7일 미만 첫날) | policy.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 150000 (50% 구간 진입) | unit |
| CP-09 | 동일 policy, cancelledAt=2026-07-07T23:59 (정확히 3일 전 — 경계일 밤) | policy.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 150000 — 달력일 차이 기준 가정, 시각 무관 (specGap G-1) | unit |
| CP-10 | 동일 policy, cancelledAt=2026-07-09T10:00 (1일 전) | policy.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 0 (0% 구간) | unit |
| CP-11 | 동일 policy, cancelledAt=2026-07-10T08:00 (체크인 당일 아침) | policy.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 0 — daysBeforeCheckIn=0 규칙 적용 가정 (specGap G-2) | unit |
| CP-12 | policy=rules[(3일전,50%)], totalPrice=99999 (홀수 금액), cancelledAt=2026-07-05T10:00, checkIn=2026-07-10 (5일 전) | policy.refundAmount(99999, cancelledAt, checkIn) | 반환값 == 49999 — 끝전 내림 가정 (specGap G-3) | unit |
| CP-13 | policy=rules[(7,100),(3,50),(0,0)] 3건 | policy.snapshot() | CancellationPolicySnapshot 타입 반환, .rules 가 원본과 값 동등 (3건, 각 daysBeforeCheckIn·refundRate 일치) | unit |
| CP-14 | CP-13 의 snapshot, totalPrice=300000, cancelledAt=2026-07-04T10:00 (6일 전), checkIn=2026-07-10 | snapshot.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 150000 — 원본 policy.refundAmount 와 동일 결과 (변환 후 계산 일관성, LLD §4.3 시점 일관성 전제) | unit |
| CP-15 | policy=rules[(7일전,100%),(3일전,50%)] — 0일 규칙 없음, totalPrice=300000, checkIn=2026-07-10, cancelledAt=2026-07-09T10:00 (1일 전 — 매칭 규칙 없음) | policy.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 0 — 매칭되는 규칙이 없으면 환불 0원 가정 (specGap G-2) | unit |
| CP-16 | policy=rules 비정렬 입력 [(0일전,0%),(7일전,100%),(3일전,50%)], totalPrice=300000, checkIn=2026-07-10, cancelledAt=2026-06-30T10:00 (10일 전) | policy.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 300000 — 리스트 입력 순서와 무관하게 daysBeforeCheckIn 기준으로 매칭 (specGap G-4: 생성 시 정렬 강제 여부) | unit |
| RT-01 | RoomType(standardGuestCount=2, maxGuestCount=4), guestCount=4 (정확히 max) | roomType.canAccommodate(4) | true — max 와 동수 허용 (발제 체크리스트: 초과 시에만 실패) | unit |
| RT-02 | 동일 RoomType, guestCount=5 (max+1) | roomType.canAccommodate(5) | false | unit |
| RT-03 | 동일 RoomType, guestCount=1 (standardGuestCount=2 미만) | roomType.canAccommodate(1) | true — 기준 인원 미만도 수용 (기준 인원은 요금 정책용, 수용 한도 아님) | unit |
| RT-04 | 동일 RoomType, guestCount=0 | roomType.canAccommodate(0) | false — 0 인원 비수용 가정 (specGap G-7) | unit |
| RT-05 | 동일 RoomType, guestCount=-1 | roomType.canAccommodate(-1) | false — 음수 인원 비수용 가정 (specGap G-7) | unit |
| RT-06 | RoomType(name="디럭스 더블", maxGuestCount=4) | roomType.updateInfo(cmd(name="디럭스 트윈", maxGuestCount=3)) | .name == "디럭스 트윈", .maxGuestCount == 3, 이후 canAccommodate(4) == false (갱신 반영) | unit |
| RT-07 | standardGuestCount=4 > maxGuestCount=2 인 생성 입력 | RoomType 생성 (Property.addRoomType 경유 포함) | CoreException(BAD_REQUEST) — standard <= max 불변식 가정 (specGap G-10) | unit |
| RT-08 | RoomType(standardGuestCount=2, maxGuestCount=4) 정상 상태 | roomType.updateInfo(cmd(standardGuestCount=5, maxGuestCount=3)) | CoreException(BAD_REQUEST) — 갱신 경로에도 standard <= max 불변식 재검증 가정 (specGap G-10), 기존 값 (2, 4) 유지 | unit |
| RT-09 | 정상 생성 입력 (displayStatus=VISIBLE 포함) | RoomType 생성 | .displayStatus == VISIBLE — 필드 보유 단언 (E3-4 증보: RSVC-07 의 RoomType 단위 HIDDEN 전제와 도메인 카탈로그 정합) | unit |
| PRP-01 | roomTypes 빈 Property, cmd(name="스탠다드 더블", standardGuestCount=2, maxGuestCount=2, bedConfiguration, sizeSqm=23, viewType=CITY) | property.addRoomType(cmd) | RoomType 반환 (.name == "스탠다드 더블"), property 의 roomTypes.size == 1 | unit |
| PRP-02 | name="스탠다드 더블" RoomType 1건 보유 Property, 같은 이름 "스탠다드 더블" cmd | property.addRoomType(cmd) | CoreException(CONFLICT) — 같은 이름 RoomType 중복 금지 불변식 (LLD §2.3), roomTypes.size == 1 유지 (specGap G-5: ErrorType 확정) | unit |
| PRP-03 | "스탠다드 더블" 1건 보유 Property, 다른 이름 "디럭스 트윈" cmd | property.addRoomType(cmd) | 정상 추가, roomTypes.size == 2 | unit |
| PRP-04 | RoomType(id=1, name="스탠다드 더블", maxGuestCount=2) 보유 Property (영속 후 재구성 상태 가정) | property.updateRoomType(1, cmd(name="디럭스 더블", maxGuestCount=3)) | findRoomType(1)!!.name == "디럭스 더블", .maxGuestCount == 3 | unit |
| PRP-05 | RoomType(id=1) 만 보유 Property | property.updateRoomType(99, cmd) | CoreException(NOT_FOUND) — 미존재 id 가정 (specGap G-6) | unit |
| PRP-06 | RoomType(id=1) 1건 보유 Property | property.removeRoomType(1) | roomTypes.size == 0, 이후 findRoomType(1) == null | unit |
| PRP-07 | RoomType(id=1) 만 보유 Property | property.removeRoomType(99) | CoreException(NOT_FOUND) — 미존재 id 가정 (specGap G-6), roomTypes.size == 1 유지 | unit |
| PRP-08 | RoomType(id=1, name="스탠다드 더블") 보유 Property | property.findRoomType(1) | 해당 RoomType 반환, .id == 1, .name == "스탠다드 더블" | unit |
| PRP-09 | RoomType(id=1) 만 보유 Property | property.findRoomType(99) | null 반환 — throw 아님 (LLD 시그니처 RoomType? — nullable) | unit |
| PRP-10 | Property(name="스테이 호텔 강남", checkInTime=15:00, roomTypes 1건, wishCount=5) | property.updateInfo(cmd(name="스테이 호텔 역삼", checkInTime=16:00)) | .name == "스테이 호텔 역삼", .checkInTime == 16:00, roomTypes.size == 1 그대로, wishCount == 5 그대로 (무관 필드 불변) | unit |
| PRP-11 | wishCount=5 인 Property | property.incrementWish() | wishCount == 6 | unit |
| PRP-12 | wishCount=5 인 Property | property.decrementWish() | wishCount == 4 | unit |
| PRP-13 | wishCount=0 인 Property (찜 0 상태) | property.decrementWish() | CoreException(INTERNAL_ERROR) — 0 미만 방지 가정, DailyRoom.releaseOne 의 INTERNAL_ERROR 패턴 답습 (LLD §3.4) (specGap G-8) | unit |
| PRP-14 | 정상 생성 입력 (name="스테이 호텔 강남", city=SEOUL, address=Address("테헤란로 152","06236"), propertyType=HOTEL, amenities={WIFI, PARKING}, checkInTime=15:00, checkOutTime=11:00, cancellationPolicy, displayStatus=VISIBLE) | Property 생성 (정적 팩토리 진입점 — rule 08, 시그니처는 specGap G-11 확정 후) | 정상 생성, .city == SEOUL, .address 값 동등, .amenities == {WIFI, PARKING}, .wishCount == 0 (초기값 가정, specGap G-11) — 발제 체크리스트 "위치·편의시설·찜 수 포함" 커버 | unit |
| PRP-15 | RoomType(id=1, name="스탠다드 더블"), RoomType(id=2, name="디럭스 트윈") 보유 Property | property.updateRoomType(2, cmd(name="스탠다드 더블")) | CoreException(CONFLICT) — 갱신 경로에도 이름 중복 금지 불변식 적용 가정 (specGap G-6), id=2 의 name == "디럭스 트윈" 유지 | unit |
| PRP-16 | RoomType(id=1, name="스탠다드 더블") 보유 Property | property.updateRoomType(1, cmd(name="스탠다드 더블", maxGuestCount=3)) — 이름 유지, 다른 필드만 갱신 | 정상 — 중복 검사는 자기 제외 (Cycle 16 Red 작성 중 발견한 경계 증보: 자기 제외 없으면 이름 유지 갱신이 CONFLICT 로 오작동) | unit |

### B.2 DailyRoom Aggregate — `DailyRoom`(AR) · `DailyRoomId`(복합 PK VO)

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (VO) | `DailyRoomId` | (roomTypeId, date) 복합 자연키 VO — 값 동등성(equals/hashCode)으로 비교 (rule 18). JPA @EmbeddedId vs @IdClass 매핑은 영속 단계 결정 (LLD §3.3) | Unit (L1, @Tag("unit")) |
| Domain (Aggregate) | `DailyRoom` | 일자별 재고·요금 단일 Aggregate Root (ADR-003). consumeOne()/releaseOne() 불변식 — 만실·휴실 시 CoreException(CONFLICT), 음수 복원 시 CoreException(INTERNAL_ERROR). availableRooms() 파생 계산 (컬럼 X), isAvailable()/canConsume() 가용 판정. roomTypeId 는 ID 참조만 (Aggregate boundary) | Unit (L1, @Tag("unit")) — 순수 JVM, BCrypt 등 느린 의존 없음 |
| Domain (Port) | `DailyRoomRepository` | 포트 인터페이스 — findByRoomTypeAndDateBetween / findByRoomTypeIdsAndDateBetween / saveAll / upsertRanges (LLD §6.1). 구현체는 apps/stay-api/infrastructure | 인터페이스 자체 테스트 X — 구현체 Integration 은 Round 3 범위 외 (후속 영속 단계) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| DRID-01 | roomTypeId=1L, date=2026-07-01 | DailyRoomId(1L, 2026-07-01) 생성 | 정상 생성, .roomTypeId == 1L, .date == 2026-07-01 | unit |
| DRID-02 | 동일 값 (1L, 2026-07-01) 으로 생성한 두 인스턴스 | equals / hashCode 비교 | equals == true, hashCode 동일 (VO 값 동등성 — rule 18) | unit |
| DRID-03 | (1L, 2026-07-01) vs (1L, 2026-07-02), 그리고 (1L, 2026-07-01) vs (2L, 2026-07-01) | equals 비교 | 둘 다 equals == false — roomTypeId 또는 date 하나만 달라도 다른 복합키 | unit |
| DR-01 | totalRooms=10, reservedRooms=0, closed=false | consumeOne() | 정상 — reservedRooms == 1, availableRooms() == 9 | unit |
| DR-02 | totalRooms=10, reservedRooms=9 (만실 직전 경계 totalRooms-1), closed=false | consumeOne() | 정상 — reservedRooms == 10 (만실 도달), availableRooms() == 0, isAvailable() == false | unit |
| DR-03 | totalRooms=10, reservedRooms=10 (만실), closed=false | consumeOne() | CoreException(CONFLICT) "재고가 부족합니다." — reservedRooms == 10 유지 (상태 불변). §3.4 기준. 전용 코드 ROOM_UNAVAILABLE 도입 결정 시 갱신 (specGap 8번) | unit |
| DR-04 | totalRooms=10, reservedRooms=0, closed=true (휴실) | consumeOne() | CoreException(CONFLICT) "휴실 상태입니다." — reservedRooms == 0 유지 | unit |
| DR-05 | closed=true 이면서 reservedRooms=10 == totalRooms (휴실 + 만실 동시) | consumeOne() | CoreException(CONFLICT), 메시지 "휴실 상태입니다." — §3.4 가드 순서대로 closed 검사 선행 | unit |
| DR-06 | totalRooms=1, reservedRooms=0 (최소 재고 경계) | consumeOne() 1회 → consumeOne() 2회째 | 1회째 정상 (reservedRooms == 1, 만실 도달), 2회째 CoreException(CONFLICT) | unit |
| DR-07 | totalRooms=10, reservedRooms=3 | releaseOne() | 정상 — reservedRooms == 2, availableRooms() == 8 | unit |
| DR-08 | totalRooms=10, reservedRooms=1 (하한 직전 경계) | releaseOne() | 정상 — reservedRooms == 0 | unit |
| DR-09 | totalRooms=10, reservedRooms=0 | releaseOne() | CoreException(INTERNAL_ERROR) "복원할 재고가 없습니다." — reservedRooms == 0 유지 (음수 방지가 도메인 레벨) | unit |
| DR-10 | closed=true, totalRooms=10, reservedRooms=2 (휴실 전환 후 기존 예약 취소 시나리오) | releaseOne() | 정상 — reservedRooms == 1. §3.4 의 releaseOne 에는 closed 가드 없음 (specGap 4번 확인 대상) | unit |
| DR-11 | totalRooms=10, reservedRooms=3 | availableRooms() | 반환값 == 7 (파생 계산 — 별도 컬럼 X) | unit |
| DR-12 | totalRooms=10, reservedRooms=10 (만실) | availableRooms() | 반환값 == 0 | unit |
| DR-13 | totalRooms=10, reservedRooms=0 | availableRooms() | 반환값 == 10 | unit |
| DR-14 | closed=false, totalRooms=10, reservedRooms=3 | isAvailable() | 반환값 == true | unit |
| DR-15 | closed=false, totalRooms=10, reservedRooms=10 (만실) | isAvailable() | 반환값 == false | unit |
| DR-16 | closed=true, totalRooms=10, reservedRooms=0 (잔여 10실이어도 휴실) | isAvailable() | 반환값 == false (closed 가 잔여 수량보다 우선) | unit |
| DR-17 | closed=false, totalRooms=10, reservedRooms=9 (잔여 1실 경계) | isAvailable() | 반환값 == true | unit |
| DR-18 | closed=false, totalRooms=10, reservedRooms=9 | canConsume() | 반환값 == true — consumeOne 가드 통과 가능. 잠정 의미 !closed && reservedRooms < totalRooms (specGap 2번) | unit |
| DR-19 | closed=false, totalRooms=10, reservedRooms=10 (만실) | canConsume() | 반환값 == false | unit |
| DR-20 | closed=true, totalRooms=10, reservedRooms=0 | canConsume() | 반환값 == false | unit |
| DR-21 | totalRooms=5, reservedRooms=2 | consumeOne() 후 releaseOne() (왕복) | reservedRooms == 2 로 항등 복원, availableRooms() == 3 | unit |
| DR-22 | totalRooms=0, reservedRooms=0, closed=false (재고 0실 — 하한 0 경계. §3.4 생성자는 totalRooms=0 을 거부하지 않음) | consumeOne() | CoreException(CONFLICT) "재고가 부족합니다." — reservedRooms(0) >= totalRooms(0) 가드 적중, reservedRooms == 0 유지. availableRooms() == 0, isAvailable() == false | unit |
| DR-23 | closed=false, totalRooms=10, reservedRooms=10 (만실 — isAvailable() == false 상태) | releaseOne() | 정상 — reservedRooms == 9, availableRooms() == 1, isAvailable() false → true 전이 (예약 취소로 만실이 가용 상태로 복귀) | unit |
| DR-24 | pricePerNight=150000 | DailyRoom 생성 | .pricePerNight == 150000 — 요금 보유 단언 (E3-3 증보: 발제 DailyRoomRate 축 직접 커버) | unit |
| DR-25 | pricePerNight=-1 | DailyRoom 생성 | CoreException(BAD_REQUEST) — 음수 요금 거부 (E3-3 증보) | unit |

### B.3 Reservation Aggregate — `Reservation`(AR) · 스냅샷 VO 묶음

> **Q3·Q4 확정 반영 (2026-06-10)**: ① `confirm` 은 `List<DailyRoom>` 대신 **`PriceSnapshot` 수취** (SAS `quote` 가 생성) — RSV-01~04 의 Given 에서 dailyRooms 픽스처는 PriceSnapshot 픽스처로 대체 적용, 인벤토리의 시그니처 기술도 동일 대체. ② `checkIn()`/`checkOut()` 전이 메서드 추가 — RSV-08~10·12~13 의 "var status 직접 대입" 픽스처는 전이 메서드 호출로 대체. 전이 가드 자체는 RSV-16~19.

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (VO) | `DateRange` | checkIn < checkOut 검증 (같은 날짜·역전 → BAD_REQUEST 가정, specGap #1 확인). 파생 계산 nights() / stayDates() — 체크아웃 날짜 미포함. 무의존 VO 로 컴포넌트 내 모든 항목의 기반 | Unit (L1) |
| Domain (VO) | `GuestInfo` | 투숙자 정보 합성 — Round 1 의 Name·PhoneNumber VO 재사용 + guestCount 보관. guestCount 하한 검증 (최소 1 가정 — specGap #4 확인). 이름·전화 형식 위반은 각 VO 단위 테스트(NAM/PH)에서 이미 망라되므로 재검증 안 함 | Unit (L1) |
| Domain (VO) | `DailyPriceEntry` | (date, pricePerNight) 1박 요금 값 보유. 검증 로직 없음 — DailyRoom 에서 복사되는 스냅샷 단위 | Unit (PS 사이클에서 함께 — 값 보유 확인 1건) |
| Domain (VO) | `PriceSnapshot` | DailyPriceEntry 목록 보유 + totalPrice() 합산. Reservation.confirm 에서 dailyRooms 로부터 생성되어 totalPrice 필드의 단일 출처가 됨 | Unit (L1) |
| Domain (VO) | `CancellationPolicySnapshot` | 예약 시점 환불 규칙 보존 (RefundRule 목록 — Property Aggregate 의 VO 재사용). refundAmount(totalPrice, cancelledAt, checkIn) 환불액 계산. 원본 CancellationPolicy 와 별도 타입 (LLD §4.3 해석 — 라이프사이클 분리) | Unit (L1) |
| Domain (VO) | `CancellationResult` | cancel 결과 (refundAmount, cancelledAt) 값 보유 data class (LLD §7.2). 자체 로직 없음 | (값 보유 — RSV cancel 테스트에서 간접 검증, 별도 테스트 클래스 X) |
| Domain (enum) | `ReservationStatus` | 상태 머신 5종 — PENDING / CONFIRMED / CHECKED_IN / CHECKED_OUT / CANCELLED. PENDING 은 enum 자리만 (Q1 / ADR-002) — 본 라운드 어떤 도메인 경로에서도 생성되지 않음 (미생성 검증은 RSV-01·06 이 담당) | Unit (가드 테스트 1건 — RSV-15) |
| Domain (Aggregate) | `Reservation` | private constructor + companion `confirm(userId, property, roomType, period, guestInfo, dailyRooms, now)` (LLD §4.4 기준 — §4.2 다이어그램과 불일치는 specGap #12) — 생성 = 즉시 CONFIRMED 강제, 스냅샷 4종 (PriceSnapshot·CancellationPolicySnapshot·propertyNameSnapshot·roomTypeNameSnapshot) 채움, canAccommodate 위반 시 BAD_REQUEST. `cancel(now)` 상태 머신 가드 (CONFIRMED·CHECKED_IN 만 허용) + CancellationResult 반환. `refundAmount(now)` 스냅샷 위임. `isCancellable()` / `belongsTo(userId)` 의도 명시 메서드. 시간은 모두 now 외부 주입 (rule 08) | Unit (L1 — Property/RoomType/DailyRoom 실객체 픽스처, mock·느린 라이브러리 없음) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| RNG-01 | checkIn=2026-07-01, checkOut=2026-07-03 (2박) | DateRange(checkIn, checkOut) | 정상 생성, .checkIn == 2026-07-01, .checkOut == 2026-07-03, nights() == 2 | unit |
| RNG-02 | checkIn=2026-07-01, checkOut=2026-07-02 (1박 경계 하한) | DateRange(checkIn, checkOut) | 정상 생성, nights() == 1 | unit |
| RNG-03 | checkIn=2026-07-01, checkOut=2026-07-03 | stayDates() | 반환값 == [2026-07-01, 2026-07-02] — 체크아웃 날짜 미포함, size == nights() | unit |
| RNG-04 | checkIn=2026-07-30, checkOut=2026-08-02 (월 경계 걸침, 3박) | stayDates() / nights() | stayDates() == [2026-07-30, 2026-07-31, 2026-08-01], nights() == 3 | unit |
| RNG-05 | checkIn=2026-07-01, checkOut=2026-07-01 (같은 날짜, 0박) | DateRange(checkIn, checkOut) | CoreException(BAD_REQUEST) — 0박 불가 가정 (specGap #1 확정 후 고정) | unit |
| RNG-06 | checkIn=2026-07-03, checkOut=2026-07-01 (역전) | DateRange(checkIn, checkOut) | CoreException(BAD_REQUEST) | unit |
| GI-01 | guestName=Name("공명선"), guestPhone=PhoneNumber("010-1234-5678"), guestCount=2 | GuestInfo(guestName, guestPhone, guestCount) | 정상 생성, .guestCount == 2, .guestName.value == "공명선" | unit |
| GI-02 | 정상 Name·PhoneNumber, guestCount=1 (하한 경계 가정) | GuestInfo(...) | 정상 생성, .guestCount == 1 | unit |
| GI-03 | 정상 Name·PhoneNumber, guestCount=0 | GuestInfo(...) | CoreException(BAD_REQUEST) — 최소 1명 가정 (specGap #4 확정 후 고정) | unit |
| GI-04 | 정상 Name·PhoneNumber, guestCount=-1 (음수) | GuestInfo(...) | CoreException(BAD_REQUEST) | unit |
| PS-01 | date=2026-07-01, pricePerNight=100000 | DailyPriceEntry(date, pricePerNight) | 정상 생성, .date == 2026-07-01, .pricePerNight == 100000 (값 보유 확인) | unit |
| PS-02 | entries=[DailyPriceEntry(2026-07-01, 100000), DailyPriceEntry(2026-07-02, 200000)] | PriceSnapshot(entries).totalPrice() | 반환값 == 300000 | unit |
| PS-03 | entries=[DailyPriceEntry(2026-07-01, 150000)] (단건) | PriceSnapshot(entries).totalPrice() | 반환값 == 150000 | unit |
| PS-04 | entries=emptyList() | PriceSnapshot(entries) | CoreException(BAD_REQUEST) — 빈 스냅샷 무의미 가정 (specGap #5 확정 후 고정) | unit |
| CPS-01 | rules=[(7일 전, 100%), (3일 전, 50%), (0일 전, 0%)], totalPrice=300000, checkIn=2026-07-10, cancelledAt=2026-06-30T10:00 (10일 전) | snapshot.refundAmount(300000, cancelledAt, checkIn) | 반환값 == 300000 (100% 환불) | unit |
| CPS-02 | 동일 rules, cancelledAt=2026-07-03T10:00 (정확히 7일 전, 경계 상한) | refundAmount(300000, cancelledAt, 2026-07-10) | 반환값 == 300000 — daysBeforeCheckIn 이상 남으면 해당 rule 적용 가정 (specGap #7 확정 후 고정) | unit |
| CPS-03 | 동일 rules, cancelledAt=2026-07-05T10:00 (5일 전) | refundAmount(300000, cancelledAt, 2026-07-10) | 반환값 == 150000 (50% 구간) | unit |
| CPS-04 | 동일 rules, cancelledAt=2026-07-07T10:00 (정확히 3일 전, 구간 경계) | refundAmount(300000, cancelledAt, 2026-07-10) | 반환값 == 150000 | unit |
| CPS-05 | 동일 rules, cancelledAt=2026-07-09T10:00 (1일 전) | refundAmount(300000, cancelledAt, 2026-07-10) | 반환값 == 0 (0% 구간) | unit |
| CPS-06 | 동일 rules, cancelledAt=2026-07-10T10:00 (체크인 당일) | refundAmount(300000, cancelledAt, 2026-07-10) | 반환값 == 0 가정 (specGap #8 — 체크인 당일 환불율 확정 후 고정) | unit |
| CPS-07 | rules=[(7일 전, 100%), (0일 전, 50%)], totalPrice=99999 (홀수), cancelledAt=2026-07-09T10:00 (1일 전), checkIn=2026-07-10 | refundAmount(99999, cancelledAt, checkIn) | 반환값 == 49999 — 50% 적용 후 끝수 절사 가정 (specGap #9 확정 후 고정) | unit |
| CPS-08 | rules=[(7일 전, 100%), (3일 전, 50%), (0일 전, 0%)], totalPrice=300000, checkIn=2026-07-01, cancelledAt=2026-07-02T09:00 (체크인 다음날 — 음수 일수) | refundAmount(300000, cancelledAt, checkIn) | 반환값 == 0 가정 (specGap #8 — 음수 일수 처리 확정 후 고정. CHECKED_IN 취소 경로 RSV-08 에서 실제 도달하므로 단위 검증 필수) | unit |
| RSV-01 | userId=1, Property(id=10, name="스테이 제주", 정책 rules=[(7,100),(3,50),(0,0)]), RoomType(id=20, name="디럭스 더블", standard=2, max=3), period=2026-07-01~07-03, guestInfo(guestCount=2), dailyRooms=[(07-01, 100000), (07-02, 200000)], now=2026-06-15T10:00 | Reservation.confirm(userId, property, roomType, period, guestInfo, dailyRooms, now) | 정상 생성 — status == CONFIRMED (PENDING 경유 없음), id == 0L, createdAt == now, confirmedAt == now (== 2026-06-15T10:00), cancelledAt == null, userId == 1, propertyId == 10, roomTypeId == 20, period·guestInfo 가 입력값 그대로 보존 (발제: 예약은 객실 타입 + 체크인/체크아웃 + 인원수를 명시) | unit |
| RSV-02 | RSV-01 과 동일 입력 | Reservation.confirm(...) | 스냅샷 4종 채워짐 — propertyNameSnapshot == "스테이 제주", roomTypeNameSnapshot == "디럭스 더블", priceSnapshot.entries == [(2026-07-01, 100000), (2026-07-02, 200000)], cancellationPolicySnapshot.rules == [(7,100),(3,50),(0,0)] (값 동등) | unit |
| RSV-03 | RSV-01 과 동일 입력 | Reservation.confirm(...) | totalPrice == 300000 — priceSnapshot.totalPrice() 와 일치 (단일 출처) | unit |
| RSV-04 | RoomType maxGuestCount=3, guestInfo.guestCount=3 (최대 인원 경계 상한) | Reservation.confirm(...) | 정상 생성 (canAccommodate(3) == true 경계 허용) | unit |
| RSV-05 | RoomType maxGuestCount=3, guestInfo.guestCount=4 (최대 인원 +1) | Reservation.confirm(...) | CoreException(BAD_REQUEST) — "최대 인원을 초과했습니다." (LLD §4.4 메시지) | unit |
| RSV-06 | CONFIRMED 예약 (totalPrice=300000, checkIn=2026-07-01, snapshot rules=[(7,100),(3,50),(0,0)]), now=2026-06-25T10:00 (체크인 6일 전) | reservation.cancel(now) | status == CANCELLED, cancelledAt == 2026-06-25T10:00, 반환 CancellationResult.refundAmount == 150000 (50% 구간), CancellationResult.cancelledAt == now | unit |
| RSV-07 | 동일 CONFIRMED 예약, now=2026-06-20T10:00 (체크인 11일 전) | reservation.cancel(now) | CancellationResult.refundAmount == 300000 (100% 구간) | unit |
| RSV-08 | RSV-06 픽스처 (totalPrice=300000, checkIn=2026-07-01, rules=[(7,100),(3,50),(0,0)]) 의 status 를 CHECKED_IN 으로 둔 예약 (var status 직접 대입 — 전이 메서드 부재, specGap #10) | reservation.cancel(now=2026-07-02T09:00) — 체크인 다음날 | 정상 — status == CANCELLED, cancelledAt == now, CancellationResult 반환 (CHECKED_IN 취소 허용, LLD §4.4). CancellationResult.refundAmount 는 specGap #8 확정 후 고정 — CPS-08 가정 따르면 0 | unit |
| RSV-09 | status 를 CHECKED_OUT 으로 둔 예약 | reservation.cancel(now) | CoreException(CONFLICT) — "취소할 수 없는 상태입니다: CHECKED_OUT" | unit |
| RSV-10 | 이미 cancel(now1) 호출되어 CANCELLED 인 예약 | reservation.cancel(now2) 재호출 (이중 취소) | CoreException(CONFLICT). status == CANCELLED 유지, cancelledAt == now1 (변동 없음 — 가드가 변이보다 먼저) | unit |
| RSV-11 | CONFIRMED 예약 (totalPrice=300000, checkIn=2026-07-01, rules=[(7,100),(3,50),(0,0)]), now=2026-06-25T10:00 (6일 전) | reservation.refundAmount(now) — 취소 없이 단독 호출 | 반환값 == 150000 (cancellationPolicySnapshot.refundAmount 위임 결과와 일치), status == CONFIRMED 유지 (부수효과 없음 — 조회 전용) | unit |
| RSV-12 | status == CONFIRMED 예약 / status == CHECKED_IN 예약 | reservation.isCancellable() | 둘 다 반환값 == true | unit |
| RSV-13 | status == CHECKED_OUT 예약 / status == CANCELLED 예약 | reservation.isCancellable() | 둘 다 반환값 == false | unit |
| RSV-14 | userId=1 로 confirm 한 예약 | reservation.belongsTo(1L) / reservation.belongsTo(2L) | belongsTo(1L) == true, belongsTo(2L) == false | unit |
| RSV-15 | ReservationStatus enum | ReservationStatus.values() | 5종 (PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED) 정의됨 — 상태 머신 enum 자리 가드 (Q1 / ADR-002). PENDING 미생성·미전이의 행위 검증은 본 테스트가 아니라 RSV-01 (confirm 즉시 CONFIRMED)·RSV-06 (cancel 즉시 CANCELLED) 이 담당 | unit |
| RSV-16 | CONFIRMED 예약 | reservation.checkIn() | status == CHECKED_IN (Q4 확정 — 전이 가드) | unit |
| RSV-17 | CANCELLED 예약 | reservation.checkIn() | CoreException(CONFLICT) — CONFIRMED 에서만 체크인 가능. status == CANCELLED 유지 | unit |
| RSV-18 | CHECKED_IN 예약 | reservation.checkOut() | status == CHECKED_OUT | unit |
| RSV-19 | CONFIRMED 예약 (체크인 전이 없이) | reservation.checkOut() | CoreException(CONFLICT) — CHECKED_IN 에서만 체크아웃 가능. status == CONFIRMED 유지 | unit |

### B.4 Wishlist — 조인 엔티티 · `WishlistId`(복합 PK VO)

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (VO) | `WishlistId` | (userId, propertyId) 복합 PK VO. 값 동등성(equals/hashCode)으로 식별 — JPA @EmbeddedId 전제 | Unit (L1) |
| Domain (Join Entity) | `Wishlist` | 유저-숙소 찜 관계의 사실(fact) 보관 — userId, propertyId, createdAt. 도메인 메서드 없음 (LLD §5.3). createdAt 은 외부 주입 잠정 전제 (rule 08 — LocalDateTime.now() 직접 호출 금지. 단 BaseEntity audit 대안 존재 — specGap 'createdAt 출처' 참조) | Unit (L1) — 생성·필드 보유·WishlistId 합성만 |
| Domain (Port) | `WishlistRepository` | 포트 인터페이스 — existsByUserIdAndPropertyId / save / deleteByUserIdAndPropertyId / findByUserId(page) (LLD §6.1) | 인터페이스 자체 테스트 X. 구현체(Integration)는 영속 단계(후속) 범위 |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| WL-01 | userId = 1L, propertyId = 10L | WishlistId(1L, 10L) | 정상 생성, .userId == 1L, .propertyId == 10L | unit |
| WL-02 | 동일 값 두 인스턴스 — WishlistId(1L, 10L) 와 WishlistId(1L, 10L) | equals / hashCode 비교 | equals == true, hashCode 동일 (VO 값 동등성 — 복합 PK 식별의 전제) | unit |
| WL-03 | WishlistId(1L, 10L) vs WishlistId(1L, 11L), 그리고 WishlistId(1L, 10L) vs WishlistId(2L, 10L) | equals 비교 | 둘 다 false — propertyId 또는 userId 한 요소만 달라도 불일치 (hashCode 불일치는 단언하지 않음 — 해시 충돌 허용) | unit |
| WL-04 | userId = 1L, propertyId = 10L, createdAt = LocalDateTime.of(2026, 6, 10, 12, 0) — now 외부 주입은 rule 08 에 따른 잠정 전제 (LocalDateTime.now() 직접 호출 금지. BaseEntity audit 채택 시 본 단언 제거 — specGap 'createdAt 출처' 참조) | Wishlist 생성 (LLD §5 명세 필드 그대로) | 정상 생성, .userId == 1L, .propertyId == 10L (LLD §5 다이어그램의 직접 필드 표기 기준 잠정 단언 — @EmbeddedId 단일 보유 채택 시 위임 프로퍼티 경유로 형태 변경, specGap '필드 구조 정합' 참조), .createdAt == 2026-06-10T12:00, id 는 WishlistId(1L, 10L) 와 값 동등 (Wishlist *-- WishlistId 합성) | unit |

### B.5 Application Layer — `PropertyService` · `WishlistService` · `ReservationService` (Fake Repository, L2)

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Application | `PropertyService` | getPropertyDetail(propertyId, period, guests) — PropertyRepository.findById + DailyRoomRepository.findByRoomTypeIdsAndDateBetween 조합, RoomType 별 canAccommodate 위임·일자별 가격 합산·가용성 집계 + wishCount 노출 / search(criteria: SearchCriteria) — 도시→인원→일자별 가용성 3단 필터 + 합산 최저가 + 정렬 4종 + 페이지 (시퀀스 §1). 규칙은 도메인 위임, 집계·정렬만 보유 (rule 19 §3). 클래스 분리(PropertySearchService) 여부는 specGap S-11 | Unit L2 (Fake Repository 주입, slow-unit) |
| Application | `WishlistService` | add(userId, propertyId) / remove(userId, propertyId) — Property 존재 확인 (add·remove 대칭) → WishlistRepository.save/deleteByUserIdAndPropertyId → Property.incrementWish()/decrementWish() 위임 (단일 트랜잭션, LLD §5.3). 중복 등록·취소는 멱등 처리 (시퀀스 §5 메모 — specGap S-3 확정 대상) | Unit L2 (Fake Repository 주입, slow-unit) |
| Application | `ReservationService` | reserve(userId, command) — Property/RoomType 로드·검증 → 일자별 DailyRoom 조회(checkIn~checkOut-1)·선검증 → 전부 consumeOne() 위임 → Reservation.confirm(...) 정적 팩토리 → 저장 (시퀀스 §2, 단일 트랜잭션) / cancel(userId, reservationId) — belongsTo 권한·상태 가드 → 일자별 releaseOne() → reservation.cancel(now) 위임. Clock 주입 (rule 08, specGap S-10 의 잠정 결론) | Unit L2 (Fake Repository + Clock.fixed 주입, slow-unit) |
| Application (DTO) | `ReserveCommand` | 예약 유스케이스 입력 (propertyId, roomTypeId, checkIn, checkOut, guestCount, guestName, guestPhone — String/원시 타입, 웹 무지). rule 19 §3 예시 명명 | 값 보유 — 직접 테스트 X (RSVC 경유) |
| Application (DTO) | `ReservationInfo` | 예약 결과 출력 — ReservationInfo.from(reservation) 정적 팩토리로 평탄화 (reservationId, status, totalPrice 등). 도메인 객체 노출 차단 (rule 20) | RSVC-01 매핑 검증 경유 (slow-unit) |
| Application (DTO) | `CancellationInfo` | 취소 결과 출력 — refundAmount, cancelledAt, status (시퀀스 §3 반환 계약) | RSVC-14 경유 간접 검증 (slow-unit) |
| Application (DTO) | `PropertyDetailInfo` | 상세 조합 결과 — 숙소 정보 + wishCount + RoomType 별 (합산가·가용성). 도메인 → Info 평탄화 (rule 20) | PSVC-01~05 경유 간접 검증 (slow-unit) |
| Application (DTO) | `PropertyPage` | 검색 결과 페이지 — 항목(propertyId, name, minTotalPrice, wishCount 등) + pagination (시퀀스 §1 반환 명명) | PSVC-08~18·21 경유 간접 검증 (slow-unit) |
| Domain (VO — 기존, LLD §7.2) | `SearchCriteria` | 검색 입력 VO (city, checkIn, checkOut, guests, sort, page, size) — PropertyService.search 의 입력. 도메인 소유 (LLD 명세) | 값 보유 — PSVC 경유 |
| Test Fixture | `FakePropertyRepository` | PropertyRepository port 의 인메모리 구현 (MutableMap 기반) — findById/findByCity/save 등. Spring 없는 L2 의 기반 (발제 체크리스트 'Fake/Stub 단위 테스트') | 자체 테스트 X — Service 테스트의 픽스처 |
| Test Fixture | `FakeDailyRoomRepository` | DailyRoomRepository port 인메모리 구현 — findByRoomTypeAndDateBetween / findByRoomTypeIdsAndDateBetween / saveAll | 자체 테스트 X — Service 테스트의 픽스처 |
| Test Fixture | `FakeReservationRepository` | ReservationRepository port 인메모리 구현 — findById/save (저장 건수·저장 객체 검증 창구) | 자체 테스트 X — Service 테스트의 픽스처 |
| Test Fixture | `FakeWishlistRepository` | WishlistRepository port 인메모리 구현 — existsByUserIdAndPropertyId/save/deleteByUserIdAndPropertyId | 자체 테스트 X — Service 테스트의 픽스처 |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| PSVC-01 | Fake 에 Property P1("스테이 제주", JEJU, wishCount=7, VISIBLE) + RoomType R1("디럭스 더블", 최대 2인)·R2("패밀리 스위트", 최대 4인); DailyRoom R1: (6/20, total 5, reserved 0, 100000)·(6/21, 120000), R2: (6/20, 200000)·(6/21, 220000) | getPropertyDetail(P1, 2026-06-20~06-22, guests=2) | PropertyDetailInfo 반환 — name == "스테이 제주", wishCount == 7, roomTypes 2건, R1 합산가 == 220000, R2 합산가 == 420000 | slow-unit |
| PSVC-02 | PSVC-01 픽스처 (R1 최대 2인, R2 최대 4인) | getPropertyDetail(P1, 6/20~6/22, guests=3) | R1 은 canAccommodate(3)==false 위임으로 가용 불가 처리 (잠정: 목록 제외 — specGap S-4), R2 만 1건 노출 | slow-unit |
| PSVC-03 | R1 의 6/21 DailyRoom row 없음 (일자 누락), R2 는 2일치 완비 | getPropertyDetail(P1, 6/20~6/22, guests=2) | R1 가용 불가 처리 (잠정: 제외 — specGap S-4), R2 합산가 == 420000 정상 노출 | slow-unit |
| PSVC-04 | R1 의 6/20 reservedRooms == totalRooms == 5 (매진) | getPropertyDetail(P1, 6/20~6/22, guests=2) | R1 가용 불가 처리 (isAvailable()==false 위임, 잠정: 제외 — specGap S-4), R2 정상 노출 | slow-unit |
| PSVC-05 | R1 의 6/20 closed == true (휴실) | getPropertyDetail(P1, 6/20~6/22, guests=2) | R1 가용 불가 처리 (isAvailable() 위임), R2 정상 노출 | slow-unit |
| PSVC-06 | Fake 에 propertyId=999 미존재 | getPropertyDetail(999, 6/20~6/22, guests=2) | CoreException(NOT_FOUND) | slow-unit |
| PSVC-07 | 정상 P1 픽스처, 기간 역전 | getPropertyDetail(P1, checkIn=6/22, checkOut=6/20, guests=2) | CoreException(BAD_REQUEST) — DateRange 생성 시점에 도메인 위임 (rule 06) | slow-unit |
| PSVC-08 | Fake 에 SEOUL 숙소 P2·P3 (각 가용 RoomType 보유), JEJU 숙소 P1 | search(SearchCriteria(SEOUL, 6/20, 6/22, guests=2, PRICE_ASC, page=0, size=10)) | items 2건 (P2, P3) — P1(JEJU) 미포함 (1차 도시 필터) | slow-unit |
| PSVC-09 | P2 의 모든 RoomType maxGuestCount == 2 | search(SEOUL, 6/20~6/22, guests=3, ...) | P2 결과 제외 (2차 인원 필터 — canAccommodate 위임) | slow-unit |
| PSVC-10 | P3 의 유일 RoomType 이 6/21 DailyRoom row 누락 (6/20 만 존재) | search(SEOUL, 6/20~6/22, guests=2, ...) | P3 결과 제외 — nights(2)일치 row 전부 존재 필요 (시퀀스 §1.4 '누락 일자 → 결과 제외') | slow-unit |
| PSVC-11 | P3 의 유일 RoomType 6/20 reservedRooms == totalRooms (재고 0) | search(SEOUL, 6/20~6/22, guests=2, ...) | P3 결과 제외 — 모든 row 의 availableRooms() > 0 필요 | slow-unit |
| PSVC-12 | P1(JEJU, wishCount=7) 의 R1 합산 220000, R2 합산 420000 (양쪽 모두 가용) | search(JEJU, 6/20~6/22, guests=2, ...) | P1 항목의 minTotalPrice == 220000 (가용 RoomType 합산가 중 최소) + 항목에 wishCount == 7 노출 (발제 체크리스트 '찜 수는 검색/상세 조회에서 함께 제공') | slow-unit |
| PSVC-13 | SEOUL 가용 숙소 3건 — minTotalPrice: P2=150000, P3=180000, P4=130000 | search(SEOUL, ..., sort=PRICE_ASC) | items 순서 == [P4(130000), P2(150000), P3(180000)] | slow-unit |
| PSVC-14 | SEOUL 가용 숙소 3건 — wishCount: P2=5, P3=12, P4=0 | search(SEOUL, ..., sort=WISHES_DESC) | items 순서 == [P3(12), P2(5), P4(0)] | slow-unit |
| PSVC-15 | SEOUL 가용 숙소 3건 (specGap S-2 — RECOMMENDED 기준 미정) | search(SEOUL, ..., sort=RECOMMENDED) | 예외 없이 3건 반환 (정렬 순서 단언은 specGap S-2 결정 후 확정) | slow-unit |
| PSVC-16 | SEOUL 가용 숙소 3건 (LLD Property 에 rating 필드 부재 — specGap S-1) | search(SEOUL, ..., sort=RATING_DESC) | 잠정: 예외 없이 3건 반환 (rating 부재 — 기본 정렬 fallback). 정렬 단언 또는 BAD_REQUEST 거절 여부는 specGap S-1 결정 후 확정 | slow-unit |
| PSVC-17 | SEOUL 가용 숙소 3건 | search(SEOUL, ..., page=0, size=2) → page=1 → page=2 순차 호출 | page 0 → items 2건, page 1 → items 1건, page 2 → items 0건 (마지막 페이지 초과 — 예외 아님) | slow-unit |
| PSVC-18 | GANGNEUNG 숙소 0건 | search(GANGNEUNG, 6/20~6/22, guests=2, ...) | 빈 items (예외 아님 — 시퀀스 §1.4 '결과 0건 → items: []') | slow-unit |
| PSVC-19 | checkIn == checkOut == 6/20 | search(SEOUL, 6/20, 6/20, guests=2, ...) | CoreException(BAD_REQUEST) — DateRange 위임 (시퀀스 §1.4 INVALID_DATE_RANGE) | slow-unit |
| PSVC-20 | P9.displayStatus == HIDDEN (Property 단위 숨김), RoomType·DailyRoom 은 완비 | getPropertyDetail(P9, 6/20~6/22, guests=2) | 잠정: CoreException(NOT_FOUND) — 조회 노출 정책은 specGap S-5 결정 후 확정 (예약은 시퀀스 §2 step 2 에 명시, 조회는 미정) | slow-unit |
| PSVC-21 | SEOUL 에 VISIBLE P2 + HIDDEN P9 (둘 다 가용 RoomType·DailyRoom 보유) | search(SEOUL, 6/20~6/22, guests=2, ...) | 잠정: items 에 P2 만 — HIDDEN Property 제외 (specGap S-5 결정 후 확정) | slow-unit |
| WSVC-01 | P1 존재 (wishCount=0), U1 의 찜 없음 | add(U1, P1) | FakeWishlistRepository 에 (U1, P1) 1건 저장, P1.wishCount == 1 (incrementWish 위임) | slow-unit |
| WSVC-02 | (U1, P1) 찜 이미 존재, wishCount=1 | add(U1, P1) 재호출 | 예외 없음 (멱등 — 시퀀스 §5 메모, specGap S-3 확정 대상), 찜 1건·wishCount == 1 유지 (incrementWish 미호출) | slow-unit |
| WSVC-03 | propertyId=999 미존재 | add(U1, 999) | CoreException(NOT_FOUND), Wishlist 저장 0건 | slow-unit |
| WSVC-04 | (U1, P1) 찜 존재, wishCount=1 | remove(U1, P1) | existsByUserIdAndPropertyId(U1, P1) == false, P1.wishCount == 0 (decrementWish 위임) | slow-unit |
| WSVC-05 | U1 의 찜 없음, wishCount=0 | remove(U1, P1) | 예외 없음 (멱등), wishCount == 0 유지 (decrementWish 미호출 — 카운터 음수 방지) | slow-unit |
| WSVC-06 | P1 존재 (wishCount=0) | add(U1, P1) 후 add(U2, P1) | Wishlist 2건, P1.wishCount == 2 (유저별 누적) | slow-unit |
| WSVC-07 | propertyId=999 미존재, U1 의 찜 없음 | remove(U1, 999) | CoreException(NOT_FOUND) — add(WSVC-03) 와 대칭의 Property 존재 선검증, decrementWish·delete 호출 0 | slow-unit |
| RSVC-01 | Clock.fixed(2026-06-10T10:00 KST). P1/R1(최대 2인), DailyRoom 6/20(total 5, reserved 0, 100000)·6/21(120000). cmd = ReserveCommand(P1, R1, 6/20, 6/22, guests=2, "공명선", "010-1234-5678") — guests == maxGuestCount 허용 경계 겸함 | reserve(U1, cmd) | ReservationInfo — status == CONFIRMED, totalPrice == 220000. FakeReservationRepository 1건, 6/20·6/21 reservedRooms 각 == 1 | slow-unit |
| RSVC-02 | RSVC-01 픽스처 (P1 name "스테이 제주", R1 name "디럭스 더블", 취소 정책 보유) | reserve(U1, cmd) 후 저장된 Reservation 확인 | propertyNameSnapshot == "스테이 제주", roomTypeNameSnapshot == "디럭스 더블", priceSnapshot.entries == [(6/20, 100000), (6/21, 120000)], cancellationPolicySnapshot == 예약 시점 정책, createdAt == confirmedAt == 2026-06-10T10:00 (Clock 주입 — LLD §4.4 confirm 이 둘 다 now 로 설정) | slow-unit |
| RSVC-03 | RSVC-01 픽스처, 1박 (경계 하한) | reserve(U1, cmd(checkIn=6/20, checkOut=6/21)) | 6/20 만 reservedRooms == 1, 6/21 은 0, totalPrice == 100000 | slow-unit |
| RSVC-04 | DailyRoom 6/20·6/21·6/22 모두 존재 | reserve(U1, cmd(6/20~6/22)) | 6/22 reservedRooms == 0 — 체크아웃 당일 미차감 (조회 범위 checkIn~checkOut-1, 시퀀스 §2 step 5) | slow-unit |
| RSVC-05 | propertyId=999 미존재 | reserve(U1, cmd(propertyId=999)) | CoreException(NOT_FOUND), Reservation 저장 0건·차감 0 | slow-unit |
| RSVC-06 | P1 존재, roomTypeId=999 — property.findRoomType(999) == null | reserve(U1, cmd(roomTypeId=999)) | CoreException(NOT_FOUND), 저장 0건 | slow-unit |
| RSVC-07 | R1.displayStatus == HIDDEN | reserve(U1, cmd) | CoreException(BAD_REQUEST) — 시퀀스 §2 step 2 검증, 차감·저장 0 | slow-unit |
| RSVC-08 | R1 최대 2인, cmd guests=3 | reserve(U1, cmd) | CoreException(BAD_REQUEST) — canAccommodate 위임. 차감 전 선검증이므로 두 DailyRoom reservedRooms 변동 0, 저장 0건 (Fake 는 rollback 없음 — 선검증 순서가 검증 대상) | slow-unit |
| RSVC-09 | 6/20 reserved 0, 6/21 reservedRooms == totalRooms (재고 부족) | reserve(U1, cmd(6/20~6/22)) | CoreException(CONFLICT) — 시퀀스 §2 step 4 전 일자 선검증. 6/20 도 차감 안 됨 (reservedRooms == 0 유지), 저장 0건 | slow-unit |
| RSVC-10 | 6/21 DailyRoom row 없음 — dailyRooms.size(1) != nights(2) | reserve(U1, cmd(6/20~6/22)) | CoreException(CONFLICT) — 일자 누락 (INVENTORY_NOT_AVAILABLE), 차감·저장 0 | slow-unit |
| RSVC-11 | 6/20 closed == true (휴실) | reserve(U1, cmd(6/20~6/22)) | CoreException(CONFLICT) — canConsume()/isAvailable() 선검증 위임, 6/21 차감 안 됨 | slow-unit |
| RSVC-12 | 6/20·6/21 모두 total 1, reserved 0 (마지막 1실) | reserve(U1, cmd(6/20~6/22, guests=2)) | 정상 CONFIRMED — 두 일자 reservedRooms == 1 == totalRooms (차감 후 잔여 0 허용 경계) | slow-unit |
| RSVC-13 | checkIn == checkOut == 6/20 | reserve(U1, cmd(6/20, 6/20)) | CoreException(BAD_REQUEST) — DateRange 위임 | slow-unit |
| RSVC-14 | Clock.fixed(2026-06-10T10:00 KST). U1 의 CONFIRMED 예약 R-100 (checkIn 6/20 — 10일 전, totalPrice 220000, 스냅샷 정책 [7일 전 100%, 3일 전 50%, 0일 전 0%]) | cancel(U1, R-100) | CancellationInfo — refundAmount == 220000 (100%), status == CANCELLED, cancelledAt == 2026-06-10T10:00. 저장된 Reservation.status == CANCELLED | slow-unit |
| RSVC-15 | RSVC-14 예약, DailyRoom 6/20·6/21 reservedRooms == 1, 6/22 == 0 | cancel(U1, R-100) | 6/20·6/21 reservedRooms 각 == 0 (releaseOne 위임 — consumeOne 대칭), 6/22 복원 호출 없음 (== 0 유지) | slow-unit |
| RSVC-16 | RSVC-14 예약 생성 후, Property 의 현재 cancellationPolicy 를 [전 구간 0%] 로 변경 | cancel(U1, R-100) | refundAmount == 220000 — 현재 정책이 아닌 cancellationPolicySnapshot 사용 (시점 일관성, 시퀀스 §3.3) | slow-unit |
| RSVC-17 | reservationId=999 미존재 | cancel(U1, 999) | CoreException(NOT_FOUND) | slow-unit |
| RSVC-18 | U1 소유의 CONFIRMED 예약 R-100 | cancel(U2, R-100) | CoreException — belongsTo(U2) == false 위임, 403 매핑 (ErrorType FORBIDDEN 신설 전제 — specGap S-8). 재고·상태 변동 없음 | slow-unit |
| RSVC-19 | status == CANCELLED 인 예약 (이미 재고 복원됨, reservedRooms == 0) | cancel(U1, R-100) 재호출 | CoreException(CONFLICT) — 상태 머신 가드 위임. releaseOne 미호출 (이중 복원 방지 — reservedRooms == 0 유지) | slow-unit |
| RSVC-20 | status == CHECKED_OUT 인 예약 | cancel(U1, R-100) | CoreException(CONFLICT) — terminal 상태 (시퀀스 §3.4 ALREADY_COMPLETED), 변동 없음 | slow-unit |
| RSVC-21 | status == CHECKED_IN 인 예약 (스냅샷 정책 [0일 전 0%]), now = 체크인 이후 | cancel(U1, R-100) | 정상 — status == CANCELLED, refundAmount == 0 (정책 위임). 재고 복원 범위 단언은 specGap S-7 결정 후 확정 (전 기간 vs 잔여일) | slow-unit |

### B.6 StayAvailabilityService — Domain Service (Q3 확정으로 증보)

#### 인벤토리

| 계층 | 클래스 | 책임 | 테스트 범위 |
|---|---|---|---|
| Domain (Service) | `StayAvailabilityService` | 무상태 Domain Service (`modules/domain`). ① 기간 완전성 검증 — `period.stayDates()` 전 일자의 DailyRoom row 존재·날짜 정합·중복 금지 ② 전 일자 가용성 검사 (휴실·만실 — `DailyRoom` 판정 메서드 위임) ③ `quote(period, dailyRooms): PriceSnapshot` 일자별 요금 합산 ④ `consumeAll` (선검증 후 전 일자 차감 — all-or-nothing) / `releaseAll` (대칭 복원. 복원 범위는 호출자가 List 로 결정 — S-7 결정과 분리) ⑤ 검색·상세 조회 경로에서도 ①~③ 재사용 (합산 로직 단일 소유) | Unit (L1 — 실객체 픽스처, mock 없음) |

#### 카탈로그

| ID | Given | When | Then | @Tag |
|---|---|---|---|---|
| SAS-01 | period=7/1~7/3 (2박), dailyRooms=[7/1 가용, 7/2 가용] | validateAvailability(period, dailyRooms) | 정상 통과 (예외 없음) | unit |
| SAS-02 | period=7/1~7/3 (2박), dailyRooms=[7/1] 1건만 (row 누락) | validateAvailability | CoreException(CONFLICT) — 기간 완전성 위반 (판매 정보 없는 일자 = 가용 불가) | unit |
| SAS-03 | period=7/1~7/3, dailyRooms=[7/1, 7/5] (개수 일치·날짜 불일치) | validateAvailability | CoreException(CONFLICT) — stayDates 와의 날짜 정합 검사 (전역 비평 갭 3 반영) | unit |
| SAS-04 | period=7/1~7/3, dailyRooms=[7/1, 7/1] (중복 날짜) | validateAvailability | CoreException(CONFLICT) | unit |
| SAS-05 | 2박 중 7/2 가 closed=true (휴실) | validateAvailability | CoreException(CONFLICT) — 휴실 일자 포함 기간은 가용 불가 | unit |
| SAS-06 | 2박 중 7/2 가 만실 (reservedRooms == totalRooms) | validateAvailability | CoreException(CONFLICT) — 만실 일자 포함 기간은 가용 불가 | unit |
| SAS-07 | period=7/1~7/2 (1박 경계), dailyRooms=[7/1] | validateAvailability | 정상 통과 — 체크아웃 일자는 검사 대상 아님 (stayDates 정의 정합) | unit |
| SAS-08 | 2박, 요금 7/1=100000 · 7/2=120000 | quote(period, dailyRooms) | PriceSnapshot 반환 — totalPrice() == 220000, entries 2건 (날짜·요금 보존) | unit |
| SAS-09 | dailyRooms 를 [7/2, 7/1] 역순 입력 | quote | entries 가 stayDates 순 (7/1, 7/2) 으로 정렬되어 생성 — 입력 순서 무관 | unit |
| SAS-10 | 2박 모두 가용 (각 reservedRooms=0) | consumeAll(period, dailyRooms) | 전 일자 reservedRooms == 1 (각 DailyRoom.consumeOne 위임) | unit |
| SAS-11 | 2박 중 7/2 만실 | consumeAll | CoreException(CONFLICT) + **7/1 도 차감되지 않음** (reservedRooms == 0 유지) — 선검증 후 차감 (all-or-nothing) | unit |
| SAS-12 | consumeAll 완료 상태 (각 reservedRooms=1) | releaseAll(dailyRooms) | 전 일자 reservedRooms == 0 — consumeAll 과 대칭 복원 (releaseOne 위임) | unit |
| SAS-13 | 가용 dailyRooms (각 reservedRooms=0) | validateAvailability + quote 연속 호출 | 입력 List·각 DailyRoom 상태 비변경 (reservedRooms == 0 유지) — 무상태·조회 전용 단언 (발제 "도메인 서비스는 상태 없이" 직접 커버) | unit |
| SAS-14 | 전 일자 row 존재 + 가용 | isAvailable(period, dailyRooms) | true — validateAvailability 의 비예외 쌍둥이 (검색·상세의 가용 필터용, S-4 잠정 채택으로 Cycle 23 증보) | unit |
| SAS-15 | 일자 row 누락 | isAvailable | false — throw 아님 (예외-제어흐름 회피) | unit |
| SAS-16 | 만실 또는 휴실 일자 포함 | isAvailable | false (DailyRoom.isAvailable 위임) | unit |

---

## C. 통합 사이클 순서 (전역 비평 확정)

의존 방향 순: 의존 없는 VO → 의존 있는 VO → Aggregate → Application. 각 사이클은 rule 14/15 의 Red→Green→Refactor + 단계별 독립 승인. **[게이트]** 표시는 해당 사이클 Red 진입 전 확정해야 하는 결정 (Part E).

> **게이트 확정 (2026-06-10)**: Q-A~Q-D 4건 모두 권고안으로 확정 — [`03-questions.md`](./03-questions.md) Q2~Q5. 아래 [Q-*] 표기는 "어떤 결정이 이 사이클을 지배하는가" 의 역추적용으로 유지.

| Cycle | 대상 | 카탈로그 | 게이트 |
|---|---|---|---|
| 1 | `Address` VO | ADDR-01~05 | — |
| 2 | `BedEntry` → `BedConfiguration` VO | BED-01~06 | — |
| 3 | `RefundRule` VO 생성·범위 검증 | CP-01~05 | — |
| 4 | `DateRange` VO | RNG-01~06 | — |
| 5 | `DailyRoomId` VO | DRID-01~03 | — |
| 6 | `WishlistId` VO | WL-01~03 | — |
| 7 | `GuestInfo` VO (R1 `Name`·`PhoneNumber` 합성) | GI-01~04 | 잠정 S-9 (GuestName 규칙) |
| 8 | `DailyPriceEntry` + `PriceSnapshot` VO | PS-01~04 | — |
| 9 | `CancellationPolicy.refundAmount` | CP-06~12·15~16 | **[Q-A]** 환불 의미론 결정표 |
| 10 | `CancellationPolicySnapshot` (CP/CPS 통합 사이클) | CP-13~14 + CPS-01~08 | **[Q-A]** |
| 11 | `RoomType` Entity | RT-01~08 | 잠정 (displayStatus 보유 — E.3) |
| 12 | `DailyRoom` 판정·파생 메서드 | DR-11~20 | — |
| 13 | `DailyRoom.consumeOne` | DR-01~06·22 | — |
| 14 | `DailyRoom.releaseOne` + 왕복·전이 | DR-07~10·21·23 | — |
| 15 | `Property` 생성 + `addRoomType`/`findRoomType` | PRP-14·01~03·08~09 | — |
| 16 | `Property.updateRoomType`/`removeRoomType` | PRP-04~07·15 | — |
| 17 | `Property.updateInfo` + wishCount 증감 | PRP-10~13 | **[Q-D]** 찜 정책 |
| 18 | `Wishlist` 조인 엔티티 | WL-04 | **[Q-D]** |
| 19 | `StayAvailabilityService` (Domain Service) | SAS-01~13 | **[Q-B 확정]** Reservation 선행 |
| 20 | `Reservation.confirm` + 스냅샷·인원 경계 | RSV-01~05 | **[Q-B·Q-C 확정]** confirm 은 PriceSnapshot 수취 |
| 21 | `Reservation.cancel`·`checkIn()`/`checkOut()`·`refundAmount`/`isCancellable`/`belongsTo` | RSV-06~19 | **[Q-C 확정]** 전이 가드 포함 |
| 22 | `WishlistService` (Fake Repository 패턴 확립) | WSVC-01~07 | **[Q-D]** |
| 23 | `PropertyService.getPropertyDetail` | PSVC-01~07·20 | 잠정 S-4·S-5 |
| 24 | `PropertyService.search` 필터·합산·HIDDEN 제외 | PSVC-08~12·21 | 잠정 S-5 |
| 25 | `PropertyService.search` 정렬·페이지네이션 | PSVC-13~19 | 잠정 S-1·S-2 |
| 26 | `ReservationService.reserve` 정상 경로 | RSVC-01~04·12 | **[Q-B]** |
| 27 | `ReservationService.reserve` 예외 경로 | RSVC-05~11·13 | **[Q-B]** |
| 28 | `ReservationService.cancel` | RSVC-14~21 | 잠정 S-7·S-8 |

---

## D. 관찰·면접 포인트 메모

- **카탈로그 작성이 명세 검증이었다** — 테스트 케이스를 설계하는 과정에서 LLD 의 명세 공백 40여 건 (환불 경계 의미론, 상태 전이 메서드 부재, 시그니처 불일치 등) 이 **구현 전에** 드러났다. "Red 가 '어떤 걸 검증할 것인가' 단계" (Q1) 라는 정의의 실증 사례.
- **Domain Service 도입 서사** — Round 1 D-A3 에서 "중복 검사는 Repository 의존이므로 Application 책임" 논리로 미도입 → Round 3 에서 "Repository 없는 다중 Aggregate 협력" (기간 × N개 DailyRoom) 이 처음 등장해 같은 원칙으로 도입. 원칙이 일관되니 결정이 뒤집힌 게 아니라 조건이 충족된 것.
- **돈 계산은 가정을 테스트로 못박는다** — 환불액 경계일 포함·달력일 기준·끝전 내림 (CP-07·09·12) 은 Round 1 의 "BCrypt salt 무작위성 테스트" 와 같은 패턴: 정책을 테스트가 고정.
- **같은 의미론을 두 타입이 공유할 때의 함정** — `CancellationPolicy` ↔ `CancellationPolicySnapshot` 이 동일 환불 규칙을 갖는데 specGap 이 양쪽에서 따로 추적되면 같은 경계가 다른 답으로 고정될 수 있다 → 단일 결정표 (Q-A) 로 통합.
- **Fake Repository 로 Spring 없는 L2 오케스트레이션 테스트** — 발제 체크리스트 "Fake/Stub 등을 사용해 단위 테스트 가능" 의 직접 구현. port 가 도메인에 있어 (DIP) Fake 가 가능하다는 점이 아키텍처 가치의 체감 증거.
- **멀티에이전트 검증 파이프라인** — 초안 (드래프터) → 적대적 검증 (보정 31건) → 전역 비평 (이음새 갭 12건) 의 3단 구조. 단일 작성자가 놓치는 컴포넌트 간 불일치 (RoomType.displayStatus 부재, 합산 로직 분기 위험) 를 전역 비평이 잡았다.

---

## E. 정책 결정 대기

### E.1 사용자 결정 4건 (사이클 게이트)

| ID | 질문 | 권고 | 막는 사이클 |
|---|---|---|---|
| **Q-A** | 환불 의미론 단일 결정표 — ① "N일 전" = 달력일 차이 (시각 무관)? ② 경계일 당일은 높은 환불율에 포함? ③ 끝전은 내림? ④ 매칭 규칙 없으면 0원? ⑤ rules 비정렬 입력 허용 (매칭이 정렬 무관)? | 카탈로그 가정 5건 그대로 채택 | 9, 10, 21, 28 |
| **Q-B** | `StayAvailabilityService` 도입 + 범위 — 미도입 / 최소 (가용성+합산) / 확장 (+ consumeAll·releaseAll, 조회 경로 재사용, `confirm` 시그니처를 견적 VO 수취로 변경) | 확장 도입 (전역 비평: 검색·상세가 같은 규칙의 3번째 반복 — 합산 분기 위험 해소) | 19, 20, 26, 27 |
| **Q-C** | `Reservation.checkIn()`/`checkOut()` 전이 메서드를 이번에 추가? (미추가 시 테스트 픽스처가 `var status` 직접 대입 — 캡슐화 훼손) | 추가 (상태 머신 가드 완결 + 픽스처 캡슐화) | 20, 21, 28 |
| **Q-D** | 찜 중복 등록/미존재 취소 — 멱등 무시 vs CONFLICT/NOT_FOUND 예외. wishCount 이중 증감 방지와 연동 | 멱등 무시 (시퀀스 §5 메모 정합) + `decrementWish` 0 미만 throw 는 안전망 유지 | 17, 18, 22 |

**→ 확정 (2026-06-10)**: 4건 모두 권고안 채택. 결정 기록·후보 비교·면접 답변 템플릿은 [`03-questions.md`](./03-questions.md) **Q2 (환불 의미론) / Q3 (SAS 확장 도입) / Q4 (전이 메서드) / Q5 (찜 멱등)**.

### E.2 컴포넌트별 잠정 가정 (카탈로그 가정값으로 진행 — 해당 Cycle Red 직전 재확인)

#### B.1 Property Aggregate

- G-1. RefundRule 적용 경계 — 취소 시각이 정확히 N일 전(경계일)이면 더 높은 환불율 규칙에 포함되는가? 일수 계산은 cancelledAt.toLocalDate() 와 checkIn 의 달력일 차이인가, 숙소 checkInTime 을 포함한 시간 단위 차이인가? (CP-07/CP-09 는 '경계일 포함 + 달력일 차이' 가정)
- G-2. 체크인 당일·경과 후 취소 및 매칭 규칙 없음 — rules 에 daysBeforeCheckIn=0 규칙이 없는 정책에서 당일·임박 취소 시 환불액은? 매칭되는 규칙이 하나도 없으면 0원 반환인가? 체크인 일시가 이미 지난 시점의 취소는 0원 반환인가 CoreException(CONFLICT) 인가? (CP-11 은 0일 규칙 존재 가정, CP-15 는 매칭 규칙 없음 → 0원 반환 가정)
- G-3. 환불액 끝전 처리 — totalPrice × refundRate / 100 이 나누어떨어지지 않을 때 내림/반올림/올림 중 무엇인가? (CP-12 는 내림 가정: 99999 의 50% == 49999)
- G-4. RefundRule 값 범위·정책 구성 — refundRate 0~100, daysBeforeCheckIn >= 0 강제 여부? CancellationPolicy 의 rules 빈 리스트 허용 여부와 동일 daysBeforeCheckIn 중복 규칙의 처리(거부 vs 첫 규칙 우선)는? rules 비정렬 입력 시 생성 시점에 정렬을 강제하는가, 매칭 시 daysBeforeCheckIn 기준으로 동작 보장하는가? (CP-03~05 는 범위 강제, CP-16 은 순서 무관 동작 가정)
- G-5. RoomType 이름 중복 판단 기준 — 정확 일치 비교인가, trim·대소문자 무시 비교인가? 중복 시 ErrorType 은 CONFLICT 가 맞는가 BAD_REQUEST 인가? (PRP-02 는 정확 일치 + CONFLICT 가정, rule 10 의 유일성 CONFLICT 패턴 차용)
- G-6. updateRoomType/removeRoomType 의 미존재 id 와 갱신 경로 불변식 — CoreException(NOT_FOUND) 를 도메인에서 throw 하는가, findRoomType 처럼 null/무시 후 Service 책임으로 두는가? updateRoomType 으로 기존 다른 RoomType 과 같은 이름으로 변경할 때도 중복 금지 불변식이 적용되는가 (LLD 는 addRoomType 만 언급)? (PRP-05/07 은 NOT_FOUND 가정, PRP-15 는 갱신 경로에도 불변식 적용 + CONFLICT 가정)
- G-7. canAccommodate 의 0·음수 인원 — false 반환인가, CoreException(BAD_REQUEST) throw 인가, 아니면 GuestInfo VO(guestCount >= 1 검증) 가 상류에서 차단하므로 도달 불가로 간주하는가? (RT-04/05 는 false 가정)
- G-8. decrementWish 의 0 미만 방지 — wishCount=0 에서 호출 시 CoreException(INTERNAL_ERROR) throw (DailyRoom.releaseOne 패턴 답습) 인가, 0 으로 클램프(조용히 무시) 인가? 위시 등록/취소 멱등성 정책과 연동 필요 (PRP-13 은 throw 가정)
- G-9. Address·Bed 계열 검증 규칙 미명세 — detailAddress 공백 금지·길이 상한? zipCode 형식(5자리 숫자)? BedEntry.count 하한(>= 1)? BedConfiguration 빈 리스트 허용 여부와 동일 BedType 중복 entry(예: SINGLE entry 2건 vs count 합산) 처리? (ADDR-03~05, BED-02/03/05 는 가정 기반)
- G-10. RoomType 생성·갱신 불변식과 id 부여 — standardGuestCount <= maxGuestCount 강제 여부 (RT-07 은 생성 시 BAD_REQUEST 가정)? updateInfo 갱신 경로에도 동일 불변식이 재검증되는가 (RT-08 은 적용 + 실패 시 기존 값 유지 가정)? 영속 전 addRoomType 직후 RoomType 의 id(0L) 상태에서 findRoomType(id) 식별은 어떻게 하는가 — 단위 테스트는 id 가 부여된 재구성 Property 를 전제해도 되는가?
- G-11. Property 생성 검증 미명세 — 생성 진입점은 정적 팩토리(rule 08)로 노출하는가, 시그니처(cmd)는? name 공백·길이 제한? wishCount 초기값 0 강제? displayStatus 기본값(VISIBLE)? representativeImageUrl 형식 검증 여부? amenities 빈 Set 허용 여부? (PRP-14 는 wishCount == 0 초기값 가정)

#### B.2 DailyRoom Aggregate

- DailyRoom 생성 시점 불변식 미명세 — 생성자에서 totalRooms < 0, reservedRooms < 0, reservedRooms > totalRooms, pricePerNight < 0 입력을 거부해야 하는가? rule 06 (검증 VO 일원화) 을 AR 생성자에도 적용할지 결정 필요 (현재 §3.4 생성자는 검증 없음). 특히 reservedRooms > totalRooms 상태가 허용되면 availableRooms() 가 음수를 반환할 수 있어 파생 계산의 전제(0 이상)가 깨진다
- canConsume() 본문 미명세 — §3.2 다이어그램에는 있으나 §3.4 코드 스니펫에는 없음. 의미가 isAvailable() (!closed && availableRooms() > 0) 과 동일해 보이는데, consumeOne 사전 조회용 별도 메서드로 유지할 것인가 isAvailable 로 통합할 것인가? (카탈로그 DR-18~20 은 잠정적으로 !closed && reservedRooms < totalRooms 의미로 작성)
- 과거 일자 차감 차단 여부 — date 가 오늘 이전인 DailyRoom 에 consumeOne() 호출 시 도메인에서 거부해야 하는가? 거부한다면 rule 08 에 따라 consumeOne(today: LocalDate) 처럼 시간 외부 주입 시그니처가 필요 (현재 §3.4 는 무인자 — 시간 검증 없음 가정)
- 휴실(closed=true) 상태에서 releaseOne 허용 여부 — §3.4 의 releaseOne 은 closed 가드가 없어 복원이 허용됨. 휴실 전환 후 기존 예약 취소 시 재고 복원 시나리오를 위한 의도된 정책인지 확인 필요 (DR-10 은 허용으로 가정)
- closed 필드 가변성 — §3.4 는 val closed (불변). 어드민이 휴실 전환/해제할 때 close()/open() 도메인 메서드를 추가할 것인가, 아니면 upsertRanges 로 row 를 재작성하는 방식인가?
- DailyRoomId 보유 방식 — §3.4 생성자는 roomTypeId/date 를 평탄(flat) 필드로 보유하고, §3.2 는 DailyRoom *-- DailyRoomId composition 으로 표기. 도메인 클래스가 DailyRoomId VO 를 직접 필드로 보유할 것인가, JPA 매핑 시점(@EmbeddedId/@IdClass)에만 도입할 것인가? (도메인 클래스 모양에 영향)
- DailyRoomId 자체 검증 — roomTypeId <= 0 같은 비정상 값을 VO 생성자에서 거부할 것인가? LLD 에 검증 명세 없음 (rule 06 적용 범위 결정 필요)
- 에러 코드 표기 불일치 — LLD §3.3 해석, docs/design/01-requirements.md, 02-sequence-diagrams.md 는 재고 부족 시 CoreException(409, ROOM_UNAVAILABLE) 전용 코드를 명시하는데, §3.4 코드 스니펫은 ErrorType.CONFLICT + 메시지 "재고가 부족합니다." 사용. ErrorType 에 ROOM_UNAVAILABLE 을 신설할 것인가, 범용 CONFLICT 로 통일할 것인가? 결정 필요 (카탈로그 DR-03/04/05/06/22 는 §3.4 기준 CONFLICT 로 작성 — 전용 코드 채택 시 일괄 갱신)

#### B.3 Reservation Aggregate

- DateRange 0박 — checkIn == checkOut 을 BAD_REQUEST 로 거부하는 게 맞는가? (당일 대실 개념 도입 계획 없음 확인 필요 — RNG-05 가정)
- DateRange 최대 연박 상한 — 연박 수 제한 (예: 30박) 을 둘 것인가? 둔다면 상한값과 위반 시 에러는? (LLD 에 명세 없음)
- DateRange 과거 날짜 — 과거 checkIn 거부 검증을 어디에 둘 것인가? DateRange 에 두면 rule 08 에 따라 today 외부 주입 시그니처 (DateRange(checkIn, checkOut, today)) 가 필요한데 현재 LLD §4.2 시그니처에는 today 가 없음 — Application Service 책임으로 미루는가?
- GuestInfo.guestCount 최소값 — 최소 1명 가정 (0·음수 BAD_REQUEST) 이 맞는가? RoomType.standardGuestCount 와의 관계 (기준 인원 미만 허용?) 는 검증 대상 아닌가? (GI-02~04 가정)
- PriceSnapshot 빈 entries — emptyList 를 생성 시점 BAD_REQUEST 로 거부하는가, totalPrice() == 0 으로 허용하는가? (PS-04 가정. confirm 경로에서는 dailyRooms 가 항상 1건 이상이므로 도달 불가일 수도 있음)
- confirm 의 dailyRooms 정합성 — dailyRooms 가 period.stayDates() 와 1:1 대응 (개수·날짜 일치) 하는지 confirm 안에서 검증하는가, 조회 계층 (ReservationService) 책임으로 두는가? LLD §4.4 confirm 에는 해당 검증이 없음 — 누락 시 totalPrice 과소 계산 위험
- RefundRule 적용 경계 의미론 — "N일 전" 계산 기준은? cancelledAt(LocalDateTime) 과 checkIn(LocalDate) 의 차이를 날짜 단위 절사로 보는가, 24시간 단위로 보는가? daysBeforeCheckIn 경계값은 "이상" 포함인가? (CPS-02·04 는 이상 포함 + 날짜 단위 가정)
- 체크인 당일·이후 취소 환불율 — rules 에 0일 규칙이 없을 때 기본값은 0% 환불인가? CHECKED_IN 상태 취소 (RSV-08) 처럼 cancelledAt 이 checkIn 이후인 경우 음수 일수는 어떻게 처리하는가? (CPS-06·08, RSV-08 은 0원 가정)
- 환불액 끝수 처리 — refundRate 적용 시 소수 발생하면 절사인가 반올림인가? (CPS-07 은 절사 가정. 결제 도입 전 확정 필요)
- CHECKED_IN/CHECKED_OUT 전이 메서드 부재 — LLD §4.4 에 checkIn()/checkOut() 메서드가 없어 테스트 픽스처는 var status 직접 대입으로 준비해야 함 (RSV-08~10·12~13). 상태 머신 가드 일관성을 위해 전이 메서드를 추가할 것인가, 후속 라운드로 미룰 것인가?
- CancellationPolicySnapshot 빈 rules — rules emptyList 허용 여부와 그때의 refundAmount 기본값은? 검증을 snapshot 생성 시점에 두는가, 원본 CancellationPolicy (Property 컴포넌트) 에 두는가?
- LLD 내부 시그니처 불일치 — §4.2 ReservationFactory 다이어그램은 confirm(userId, propertyId, roomTypeId, period, guestInfo, dailyRooms, property, now), §4.4 코드 블록은 confirm(userId, property, roomType, period, guestInfo, dailyRooms, now). 본 카탈로그는 §4.4 기준 채택 — §4.2 다이어그램 정합 갱신 필요

#### B.4 Wishlist

- Wishlist 생성 진입점 — LLD §5 에 정적 팩토리 미명세. rule 08 에 따라 Wishlist.of(userId, propertyId, now) 같은 정적 팩토리를 추가할 것인가, 도메인 메서드 없는 단순 fact 이므로 주 생성자 직접 노출을 허용할 것인가?
- createdAt 의 출처 — LLD 내부 비일관: §3.4 DailyRoom 은 다이어그램에 createdAt/updatedAt 을 표기하면서도 생성자 시그니처에서 제외 (BaseEntity audit 자동 기록 시사 — rule 07), 반면 §4.4 Reservation 은 now 파라미터로 createdAt 을 직접 보유 (rule 08). Wishlist §5 는 createdAt 필드만 표기하고 주입 방식 미명세 — BaseEntity audit 채택 시 WL-04 의 createdAt 단언은 제거(영속 계층 검증으로 이동), 도메인 필드 채택 시 rule 08 에 따라 외부 주입 필수. 본 카탈로그는 후자를 잠정 전제
- WishlistId 의 ID 값 검증 — LLD 에 검증 규칙 없음. userId/propertyId 가 0 또는 음수일 때 CoreException(BAD_REQUEST) 로 거부할 것인가, 참조 무결성(실재 User/Property 존재) 검증은 application/DB 책임이므로 도메인은 무검증 통과시킬 것인가? (rule 06 의 'VO 생성자 검증 일원화' 적용 범위 결정 필요)
- Wishlist 필드 구조 정합 — LLD §5 다이어그램이 Wishlist 에 +userId/+propertyId 직접 필드와 WishlistId 복합 PK 합성(*--)을 동시에 표기. 구현 시 @EmbeddedId WishlistId 단일 보유 + 위임 프로퍼티 노출인가, 필드 이중 보유인가? (04-erd.md 와 교차 확인 필요 — WL-04 의 단언 형태에 영향)
- 중복 찜 등록 정책 — 이미 찜한 (userId, propertyId) 에 재등록 요청 시 CoreException(CONFLICT) 인가, 멱등 무시(no-op)인가? rule 10 (어플 선검사 + DB unique 이중 보장) 을 Wishlist 에도 적용하는가? — application 컴포넌트 결정이나 도메인 예외 타입·DB 제약 설계에 선행 영향
- 미존재 찜 취소 정책 — 찜하지 않은 (userId, propertyId) 에 취소 요청 시 CoreException(NOT_FOUND) 인가, 멱등 무시인가? wishCount decrement 와 묶이면 멱등 처리 시 카운터 이중 감소 방지 로직이 필요 — application 컴포넌트로 전달할 결정 사항

#### B.5 Application Layer

- S-1: RATING_DESC 정렬 — LLD Property 클래스에 rating 필드가 없다. Round 3 에 평점 필드를 추가하는가, 아니면 RATING_DESC 요청은 미구현으로 거절(BAD_REQUEST)하거나 지원 목록에서 제외하는가?
- S-2: RECOMMENDED 정렬 기준 — 추천 점수 정의가 LLD·시퀀스 어디에도 없다. 잠정 기본 정렬(예: 등록순 id asc)로 두는가, wishCount·가격 가중 조합으로 정의하는가?
- S-3: 찜 중복 등록/취소 처리 — 시퀀스 §5 는 '자리만' 상태이고 멱등(변화 없음) 메모만 있다. 멱등으로 확정하는가, CoreException(CONFLICT) 응답인가? (WSVC-02·05 의 Then 이 이 결정에 의존)
- S-4: 상세 조회에서 인원 초과·매진·일자 누락 RoomType 처리 — 검색은 시퀀스 §1.4 에 '결과 제외' 가 명시됐지만 상세 조회는 미정. 목록에서 제외하는가, '예약 불가' 플래그로 포함 노출하는가? (PSVC-02~05 의 Then 이 의존)
- S-5: DisplayStatus.HIDDEN 인 Property/RoomType 의 검색·상세 노출 정책 — 검색 제외 + 상세 NOT_FOUND 로 처리하는가? (예약은 시퀀스 §2 에 BAD_REQUEST 명시, 조회는 미정 — PSVC-20·21 의 Then 이 의존)
- S-6: 과거 날짜 검색·예약 차단 — checkIn < today 를 거부해야 하는가? LLD 의 DateRange(checkIn, checkOut) 시그니처에 today 주입이 없다. rule 08 정합을 위해 DateRange 검증에 today 를 주입(DateRange(checkIn, checkOut, today))으로 확장하는가?
- S-7: CHECKED_IN 상태 취소의 재고 복원 범위 — 상태 다이어그램(§4.2)은 '잔여일 복원', 취소 시퀀스(§3.2 step 4)는 전 기간(checkIn~checkOut-1) 조회로 상호 불일치. 어느 쪽인가? (RSVC-21 의 복원 단언이 의존)
- S-8: 타 유저 예약 취소의 403 매핑 — 현재 ErrorType 은 INTERNAL_ERROR(500)/BAD_REQUEST(400)/NOT_FOUND(404)/CONFLICT(409) 4종뿐. FORBIDDEN(403, ACCESS_DENIED) 을 신설하는가, 리소스 존재 은폐를 위해 NOT_FOUND(404) 로 응답하는가? (RSVC-18 의 Then 이 의존)
- S-9: GuestInfo.guestName 의 Name VO 재사용 — LLD 의 GuestInfo 는 회원 Name VO(한글 1~10자)를 그대로 쓴다. 예약자 이름에도 동일 규칙(영문 게스트명 거부)을 적용하는가, 별도 GuestName 규칙을 두는가?
- S-10: cancel 의 now 전달 방식 — 시퀀스 §3 은 Controller 가 now 를 인자로 전달하지만, Round 1 패턴(rule 08)은 Service 가 Clock 을 주입받아 내부에서 생성한다. ReservationService 가 Clock 을 보유하고 cancel(userId, reservationId) 시그니처로 통일하는가?
- S-11: 검색 서비스 클래스 명명 불일치 — 시퀀스 §1 은 PropertySearchController/PropertySearchService 로 별도 클래스를 명명하지만, 본 카탈로그·발제 예시(PropertyFacade.getPropertyDetail → rule 16 으로 Service 명명)는 PropertyService 단일 클래스에 getPropertyDetail + search 를 통합한다. 검색을 별도 Service 로 분리하는가, PropertyService 로 통합하고 시퀀스 문서를 증강 시 정정하는가? (reserve vs createReservation 명명은 rule 19 §3 예시가 reserve 로 확정이라 gap 아님)
- S-12: 조회 전용 포트의 유스케이스 부재 — LLD §6 에 WishlistRepository.findByUserId(userId, page), ReservationRepository.findByUserIdAndPeriod/findAll(page) 포트가 정의돼 있으나, 이를 사용하는 Application 유스케이스(내 찜 목록 조회, 내 예약 목록 조회)가 발제 체크리스트·시퀀스 어디에도 없다. Round 3 구현 범위에 포함하는가, 포트만 선언하고 후속 라운드로 미루는가? (포함 시 PSVC/WSVC/RSVC 카탈로그 증보 필요)

### E.3 전역 잠정 결정 (전역 비평 발굴 — 컴포넌트 간 이음새)

| # | 항목 | 잠정 채택 | 근거/영향 |
|---|---|---|---|
| E3-1 | `createdAt` 출처 — BaseEntity audit (rule 07) vs now 주입 (rule 08) | 도메인 의미 있는 시각 (`confirmedAt`·`cancelledAt`·찜 `createdAt`) 은 now 주입, 순수 audit 은 BaseEntity | DailyRoom·Reservation·Wishlist 3곳 공통 — 라운드 시작 시 일괄 |
| E3-2 | 과거 날짜 차단 소재지 (RNG 과거 checkIn / DR 과거 일자 차감 / S-6) | Application Service 책임 (`LocalDate.now(clock)` 주입 후 비교). `DateRange` 시그니처는 LLD 유지 | 3중 분산 질문의 단일화 — 시그니처 재작업 0 |
| E3-3 | `DailyRoom.pricePerNight` 도메인 검증 — 현재 카탈로그 0건 | DR 카탈로그에 2건 증보 (요금 보유 단언 + 생성자 음수 거부) — Cycle 12 Red 때 포함 | 발제 "DailyRoomRate 축" 직접 커버 |
| E3-4 | `RoomType.displayStatus` 보유 여부 — RSVC-07 이 전제하나 RT-/PRP- 에 단언 없음 | LLD §2.2 대로 보유. RT 생성 케이스에 필드 단언 1건 증보 — Cycle 11 Red 때 포함 | 도메인↔Application 카탈로그 정합 |
| E3-5 | 검색/상세 합산가 ↔ 예약 `PriceSnapshot.totalPrice` 동일 규칙 보장 | Q-B 확장 채택 시 SAS 견적 산출을 조회 경로에서도 재사용 (구조적 해소) | 합산 로직 2중 구현·분기 위험 |
| E3-6 | S-8: 타 유저 예약 취소 응답 — `FORBIDDEN(403)` 신설 vs `NOT_FOUND(404)` 은폐 | `ErrorType.FORBIDDEN(403)` 신설 (rule 09 — 새 에러 코드는 ErrorType 에 추가) | Cycle 28 (RSVC-18) |
| E3-7 | S-11: 검색 Service 분리 여부 — 시퀀스 §1 은 `PropertySearchService` 별도 명명 | `PropertyService` 단일 통합 (발제 예시 + rule 16 Service 명명 정합). 시퀀스 문서는 증강 시 정정 | Cycle 23~25 |
| E3-8 | S-12: 내 찜/예약 목록 조회 유스케이스 — LLD 포트만 존재 | Round 3 미포함 (후속 라운드). 포트 선언만 유지 | 범위 확정 |

---

## F. 의도적 제외 (Round 3 범위 밖 — 발제 대비 명시)

| 제외 항목 | 사유 | 재개 시점 |
|---|---|---|
| 동시 예약 race (rule 10 ② DB 안전망 — 락/원자적 UPDATE) | L1/L2 단위 범위 밖. "재고 차감 음수 방지" 는 도메인 레벨 (DR-03~05) 로 커버, 동시성 측면은 별도 | 후속 라운드 (L3 + Testcontainers) |
| JPA 매핑·Repository 구현체 L3 통합테스트 | Docker 환경 제약 ([[testcontainers-docker-desktop-incompat]]) + 단위 중심 범위 | 후속 (환경 정상화 시) |
| E2E (L4) — Controller·HTTP 계층 | Round 3 발제가 도메인 모델·단위 테스트 중심 | 후속 라운드 |
| `RATING_DESC` 정렬 실구현 | `Property` 에 rating 필드 부재 (S-1) — 지원 목록에서 제외, 요청 시 BAD_REQUEST | 평점 도메인 도입 시 |
| 어드민 기능 (RoomType CRUD API·재고 upsertRanges) | 도메인 메서드 (PRP-04~07, `upsertRanges` 포트) 까지만. Application/API 는 후속 | 후속 라운드 |

### 전역 비평 요지 (반영 현황)

- ID 직접 충돌 0건. CP-/CPS- 실질 중복 → **D-B3 단일 소유 원칙**으로 반영. DRG→RNG 리네이밍 → **D-B5** 반영
- SAS 카탈로그 부재 → Q-B 채택 즉시 SAS-01~ 증보 (기간 완전성·중간 휴실/만실·1박 경계·합산·all-or-nothing·releaseAll 대칭 + "입력 List 비변경" 무상태 단언)
- `Reservation.confirm` 의 dailyRooms ↔ `stayDates()` 날짜 정합 (개수 같고 날짜 다름·중복·순서 뒤섞임) — Q-B 채택 시 SAS 소재로 케이스 발급
- 발제 체크리스트 커버리지: Domain Service 2항목 (SAS 증보 대기) 과 Repository 구현체 L3 (의도적 제외) 외 전 항목 카탈로그 ID 매핑 완료

---

## G. 변경 이력

- 2026-06-10 v1: 멀티에이전트 워크플로 산출 — 컴포넌트 인벤토리 + 검증 카탈로그 166건 + 통합 사이클 28 + 결정 대기 (Q-A~Q-D, E.2~E.3). 적대 검증 보정 31건·전역 비평 갭 12건 반영.
- 2026-06-10 v1.1: Q-A~Q-D 확정 (`03-questions.md` Q2~Q5) 반영 — B.6 `StayAvailabilityService` 카탈로그 증보 (SAS-01~13), 전이 가드 증보 (RSV-16~19), `confirm` 시그니처 `PriceSnapshot` 수취 변경 메모 (B.3), 의존 흐름 갱신. 총 카탈로그 166 → **183건**.
- 2026-06-11 v1.2: **구현 완료** — Cycle 1~28 전체 (Q7 묶음 진행: Cycle 22~28). 실행 중 증보: RT-09 (E3-4), DR-24~25 (E3-3), PRP-16 (자기 제외 갱신 경계 — Red 작성 중 발견), SAS-14~16 (`isAvailable` 비예외 쌍둥이 — Q6 해소·S-4 잠정). Cycle 24+25 및 26+27 은 동일 메서드 대상이라 통합 실행. `ErrorType.FORBIDDEN(403)` 신설 (E3-6). 도메인 194건 + Application 52건 = **246건 통과** (L1/L2), `clean ktlintCheck build -x test` 성공. L3/L4 는 Part F 의도적 제외 유지.
- 2026-06-11 v1.3: **후속 라운드 완료** (Q8 — 동시 예약 race 만 제외 유지). ① JPA 영속: 도메인 17파일 매핑 (E4 — `@ElementCollection` 별도 테이블 5종, `@EmbeddedId` 복합키 2종, `AuditedEntity` 신설) + 어댑터 8종 — L1/L2 246건 회귀로 행위 무변경 증명 ② L3 통합테스트 12케이스 (INT-PRP/DR/RSV/WL — 환경 차단, 컴파일 검증) ③ API 계층: V1 엔드포인트 6개 (검색·상세·찜 등록/취소·예약·취소, `X-USER-ID` 헤더 잠정) + E2E 7케이스 (E2E- 접두, 환경 차단) ④ @Tag retrofit 14파일 + `-DtestTag` 등급 실행 가동 (rule 17 §3 실현) ⑤ LLD 정정 (D-B2 해소 + §3.5 SAS 등재 + 전이 메서드). Part F 잔여: 동시 예약 race, S-12 (내 찜/예약 목록), 어드민 API.
