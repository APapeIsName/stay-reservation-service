# Round 3 — Q&A 누적

> 형식: [`15-process-conventions.md`](../../.claude/rules/15-process-conventions.md) §4 — 질의자/제안자 명시, 면접 답변 템플릿 포함, 역사 보존.

---

## Q1. (2026-06-10) Red·Green·Refactor 각 단계의 목적 재정의

**질의자**: 사용자 (컨벤션 정의)

### 맥락
Round 3 구현 진입 직전, 사용자가 TDD 3단계의 *목적* 을 명시 정의. 기존 rule 14 는 절차(실패 테스트 → 최소 구현 → 정리)만 기술하고 각 단계가 *왜 존재하는가* 가 빠져 있었음.

### 답 또는 결정
- **Red** = 명세 기반으로 "어떤 걸 검증할 것인가" 를 테스트로 고정하는 단계. 테스트가 곧 명세의 실행 가능한 표현
- **Green** = 테스트 작성과 **분리된 에이전트**가 테스트만 보고 통과시키는 단계. 작성자·구현자 분리로 "프로덕션 코드 ↔ 테스트 코드 일치" 가 증명됨
- **Refactor** = Green 의 "성공만을 위한" 코드를 객체지향(하는 것/아는 것, 역할·책임·경계·협력 기반 객체)으로 재구성. **변경 한 번마다 테스트 지속 실행** 이 원칙
- rule 14 의 "TDD 사이클" 절을 위 정의로 개정 (검수 게이트는 그대로 — Green 구현 에이전트의 산출물도 chat 제안 → 승인 → Write)

### 면접 답변 템플릿
> "TDD 의 각 단계에 명확한 목적을 부여해 운영했습니다. Red 는 명세를 실행 가능한 형태로 고정하는 단계로 '무엇을 검증할 것인가'에 집중했고, Green 은 테스트 작성자와 구현자를 분리해 — 별도 에이전트에게 테스트와 명세만 주고 구현시키는 방식으로 — 테스트가 실제로 명세를 대변하는지 증명했습니다. Refactor 는 통과만을 위한 코드를 역할·책임·협력 관점의 객체지향 설계로 재구성하되, 변경 한 번마다 테스트를 돌려 안전망 위에서 진행했습니다."

### 출처
- 사용자 정의: 2026-06-10
- 반영 rule: [`14-test-strategy-tdd.md`](../../.claude/rules/14-test-strategy-tdd.md)
- 관련 rule: [`05-code-review-gate.md`](../../.claude/rules/05-code-review-gate.md), [`15-process-conventions.md`](../../.claude/rules/15-process-conventions.md)

---

## Q2. (2026-06-10) 환불 의미론 결정표 — 5개 하위 결정 동시 확정

**제안자**: Claude (전역 비평이 CP-/CPS- 이중 추적 위험 지적) · **결정**: 사용자

### 맥락
`CancellationPolicy.refundAmount` 계산 기준이 LLD 에 미명세. 카탈로그 작성 중 같은 질문이 Property 컴포넌트 (specGap G-1~G-4) 와 Reservation 컴포넌트 (#7~9·11) 에서 **따로** 추적되고 있었음 — 한쪽만 확정되면 원본 `CancellationPolicy` 와 `CancellationPolicySnapshot` 이 같은 경계를 다른 답으로 구현할 위험.

### 답 또는 결정
① "N일 전" = **달력일 차이** (취소 시각 무관, `cancelledAt.toLocalDate()` 와 `checkIn` 의 일수 차)
② **경계일 당일은 높은 환불율에 포함** (정확히 7일 전 취소 = 100% 구간)
③ 끝전은 **내림** (99,999원의 50% = 49,999원)
④ **매칭 규칙이 없으면 0원** (체크인 임박·당일 등)
⑤ rules **비정렬 입력 허용** — 매칭 로직이 정렬에 의존하지 않음

경계 의미론의 단일 소유자는 `CP-06~12·15~16`, `CPS-` 는 "원본과 동일 결과" 변환 정합으로 축소 (D-B3).

### 면접 답변 템플릿
> "환불액처럼 돈이 걸린 계산은 '대략 맞는' 구현이 위험해서, 경계 의미론 다섯 가지(일수 계산 기준·경계일 포함·끝전·미매칭·정렬 전제)를 결정표로 만들어 한 번에 확정하고 각각을 테스트로 못박았습니다 — 정확히 7일 전 취소는 100%, 99,999원의 50% 는 49,999원 내림처럼요. 특히 같은 규칙을 현재 정책(CancellationPolicy)과 예약 시점 스냅샷(CancellationPolicySnapshot) 두 타입이 공유하기 때문에, 결정이 한쪽에만 반영되면 같은 경계가 다른 답으로 고정될 수 있어 단일 결정표로 양쪽 테스트 카탈로그를 동시에 묶었습니다."

### 출처
- `docs/round-3/02-tdd-plan.md` E.1 Q-A, D-B3 / 카탈로그 CP-06~16, CPS-01~08

---

## Q3. (2026-06-10) `StayAvailabilityService` — Round 3 유일 Domain Service, 확장 범위로 도입

**제안자**: Claude (DS 분석 + 전역 비평 보완 4건) · **결정**: 사용자

### 맥락
발제 체크리스트에 Domain Service 항목 2건. 후보 6개를 3관문 (단일 Aggregate 불가 / Repository 무의존 / 무상태) 으로 심사 — 예약 오케스트레이션·검색 정렬·찜 동기화·환불 계산·range 펼치기는 전부 기각, "기간 × 다중 DailyRoom 협력" 만 통과.

### 답 또는 결정
**확장 도입**: ① 기간 완전성 검증 (`DateRange.stayDates()` 전 일자의 row 존재·날짜 정합) ② 전 일자 가용성 검사 ③ `quote(...)` 요금 합산 → `PriceSnapshot` ④ `consumeAll`(all-or-nothing)/`releaseAll` 대칭 ⑤ 검색·상세 조회 경로에서도 견적 재사용 (합산 로직 분기 위험 해소). 부수 효과: `Reservation.confirm` 이 `List<DailyRoom>` 대신 `PriceSnapshot` 을 수취 — 타 Aggregate 객체 의존 제거.

### 후보 비교
| 선택지 | 장점 | 단점 |
|---|---|---|
| 미도입 (현행 LLD) | 변경 0 | 가용성·합산 규칙이 3개 경로 (검색·상세·예약) 에 반복 — rule 18 휴리스틱 위반 |
| 최소 도입 (가용성+합산만) | 파급 최소 | 차감/복원 대칭 반복은 잔존 |
| **확장 도입 (채택)** | 규칙 단일 소유 + confirm 설계 냄새 해소 | SAS 카탈로그 증보 + RSV/RSVC Then 재조정 |
| StayPlan 일급 컬렉션 VO | DS 없이 캡슐화 | VO 가 타 Aggregate 객체 보유 — 경계 원칙과 긴장 |

### 면접 답변 템플릿
> "Round 1 에서는 '중복 검사는 Repository 의존이므로 Application 책임'이라는 논리로 Domain Service 를 도입하지 않았습니다 (D-A3). Round 3 에서 처음으로 Repository 없이 여러 Aggregate — 숙박 기간 × N개의 일자별 재고 — 가 협력하는 규칙이 등장했고, 같은 원칙을 적용하니 이번에는 도입 조건이 충족됐습니다. 원칙이 바뀐 게 아니라 조건이 충족된 거죠. 검색·상세·예약 세 경로가 같은 가용성·합산 규칙을 반복하는 것도 승격 근거였고, 부수 효과로 Reservation.confirm 이 타 Aggregate 객체 리스트를 직접 받던 설계 냄새도 견적 VO 수취로 해소했습니다."

### 출처
- DS 분석·전역 비평: `docs/round-3/02-tdd-plan.md` D-B1, B.6 (SAS-01~13), E.1 Q-B
- Round 1 대비: `docs/round-1/02-tdd-plan.md` D-A3 / rule 18 §1·§3

---

## Q4. (2026-06-10) `Reservation.checkIn()`/`checkOut()` 전이 메서드 추가

**제안자**: Claude (적대 검증·전역 비평 — 픽스처 캡슐화 훼손 지적) · **결정**: 사용자

### 맥락
LLD §4.4 에는 `cancel()` 만 있고 CHECKED_IN/CHECKED_OUT 진입 수단이 없음. 해당 상태가 필요한 테스트 11건의 픽스처가 `var status` 직접 대입으로 우회해야 하는 상황 — 캡슐화 훼손이 테스트 설계에서 먼저 드러난 사례.

### 답 또는 결정
이번 라운드에 추가. 가드: `checkIn()` 은 CONFIRMED 에서만, `checkOut()` 은 CHECKED_IN 에서만 — 위반 시 `CoreException(CONFLICT)`. 카탈로그 RSV-16~19 증보. LLD `03-class-diagram.md` 는 Q3 의 confirm 시그니처 변경과 묶어 정정.

### 면접 답변 템플릿
> "상태 머신을 enum 으로만 두면 전이 규칙이 코드 밖에 있게 됩니다. 테스트 카탈로그를 설계하다가 CHECKED_IN 상태 픽스처를 만들 방법이 var 직접 대입밖에 없다는 걸 발견했는데, 테스트가 캡슐화를 깨야 한다는 것 자체가 도메인에 전이 메서드가 빠졌다는 설계 신호라고 판단해 checkIn()/checkOut() 을 가드와 함께 추가했습니다. 결과적으로 상태 다이어그램의 모든 전이가 컴파일 가능한 코드로 표현됩니다."

### 출처
- `docs/round-3/02-tdd-plan.md` E.1 Q-C, 카탈로그 RSV-16~19 / LLD §4.2 상태 다이어그램

---

## Q5. (2026-06-10) 찜 멱등 처리 + wishCount 카운터 보호

**제안자**: Claude (전역 비평 — 정책 3중 분산 지적) · **결정**: 사용자

### 맥락
중복 등록/미존재 취소 정책이 Property (G-8 decrementWish), Wishlist (중복/미존재), Application (S-3) 세 곳에서 따로 추적됨 — 실제로는 하나의 정책.

### 답 또는 결정
**멱등 무시**: 이미 찜한 숙소 재등록 / 찜 안 한 숙소 취소 = no-op (성공 응답, 상태 변화 없음). `WishlistService` 가 `existsByUserIdAndPropertyId` 선검사로 `incrementWish`/`decrementWish` 호출을 가드 → 카운터 이중 증감 원천 차단. 도메인 `decrementWish` 의 0 미만 throw (`INTERNAL_ERROR`) 는 최후 안전망으로 유지 (`DailyRoom.releaseOne` 패턴 답습).

### 면접 답변 템플릿
> "찜 등록은 더블클릭·네트워크 재시도가 흔한 연산이라 멱등으로 설계했습니다. 같은 요청이 두 번 와도 결과가 같아야 클라이언트 재시도가 안전하니까요. 카운터 정합은 두 겹으로 보호했습니다 — 서비스가 존재 여부 선검사로 증감 호출 자체를 가드하고, 도메인의 decrementWish 는 0 미만이 되면 INTERNAL_ERROR 를 던지는 최후 안전망으로 남겼습니다. 안전망이 발동된다면 그건 버그 신호이지 정상 흐름이 아니라는 의미를 에러 타입(500)으로 구분한 겁니다."

### 출처
- `docs/round-3/02-tdd-plan.md` E.1 Q-D / 카탈로그 PRP-13, WSVC-02·05 / 시퀀스 §5 멱등 메모

---

## Q6. (2026-06-11) [Deferred] `StayAvailabilityService.quote` 의 자체 검증 범위

**제안자**: Claude

### 맥락
Cycle 19 Green 의 `quote` 는 기간 완전성을 자체 검증하지 않음 — 누락 일자 입력 시 `NoSuchElementException` (CoreException 아님). 현재는 `validateAvailability`/`consumeAll` 선행이 사용 계약. 그런데 상세 조회 (PSVC) 의 만실 RoomType 노출 정책 (S-4: 목록 제외 vs "예약 불가 플래그" 포함 노출) 에 따라 quote 가 **만실 객실의 견적도 산출해야 할 수 있어** (가격 표시용), 검증 범위가 달라짐 — 완전성 검증만 넣을지(만실 허용), 현행 유지할지.

### 답 또는 결정
**Deferred → 해소 (2026-06-11, Cycle 23)**: S-4 잠정 확정 (가용 불가 RoomType 은 목록 제외 — 만실 견적 표시 불필요) 에 따라 **(b) 현행 유지** 채택. 대신 조회 필터용으로 `isAvailable(period, dailyRooms): Boolean` 비예외 쌍둥이를 신설 (SAS-14~16) — 예외-제어흐름 회피. quote 는 항상 가용 검증 통과 후 호출되는 계약 유지.

### 출처
- 코드: `modules/domain/.../dailyroom/StayAvailabilityService.kt` quote
- 관련: 02-tdd-plan E.2 (S-4), rule 15 §5 (deferred 트리거)

---

## Q7. (2026-06-11) Cycle 22~28 묶음 진행 승인

**질의자**: 사용자

### 맥락
Cycle 21 (도메인 레이어 완성) 직후 사용자 지시: "앞으로는 계속 알아서 진행해줘. 끝나고 내가 최종 검증해볼게" — rule 15 §2 의 "묶음 진행 명시" 조건 충족.

### 답 또는 결정
Application 구간 (Cycle 22~28) 은 단계별 승인 없이 묶음 진행. 단 사이클 규율 (Red 확인 → 분리된 Green 에이전트 → 테스트+ktlint+회귀) 은 그대로 유지하고, 완료 후 최종 검증용 종합 리포트 제출. E.2/E.3 잠정 가정은 카탈로그 가정값으로 적용하고 리포트에 명시.

### 출처
- rule 15 §2 (묶음 진행 예외), rule 05 (게이트)

---

## Q8. (2026-06-11) 후속 라운드 범위 확정 + JPA 매핑 설계 결정

**질의자**: 사용자 (범위) · **제안자**: Claude (설계)

### 맥락
Round 3 구현 완료 후 사용자 지시: "미룬 작업 전부 진행, 동시 예약 race 는 제외". 범위 = ① JPA 영속 + Repository 구현체 (L3) ② API 계층 (L4) ③ @Tag retrofit + LLD 정정. Q7 과 동일하게 묶음 진행.

### 답 또는 결정 (영속 설계)
- **E4 (ERD 확정) 그대로**: VO 컬렉션은 `@ElementCollection + @CollectionTable` 별도 테이블 (refund_rule, bed_entry, property_amenity, reservation_price_entry, reservation_refund_rule). 복합키는 `@EmbeddedId` (DailyRoomId·WishlistId — `Serializable` 부여)
- **R3 Aggregate 는 BaseEntity 를 상속하지 않음** — BaseEntity 의 `@Id @GeneratedValue val id` 는 ① 재구성 생성자 (id 포함) 가 단위 테스트 픽스처의 핵심인 R3 패턴과 충돌 ② 복합 자연키 테이블 (daily_room·wishlist) 과 양립 불가. 대신 **자체 `@Id` 선언** + audit 전용 `@MappedSuperclass AuditedEntity` 신설 (modules/jpa — BaseEntity 는 보존 골격이라 무수정)
- Reservation 은 AuditedEntity 도 미상속 — created_at 이 도메인 의미 시각 (now 주입, ERD 에 updated_at 없음)
- 도메인 ↔ JPA 통합은 rule 07 그대로 (@Entity 직접 부여, 어노테이션만 추가 — 행위 무변경, L1/L2 회귀가 증명)
- 복합키 엔티티는 `@EmbeddedId` 보유 + 평탄 프로퍼티는 파생 getter 로 노출 (도메인 코드·테스트 시그니처 불변)

### 면접 답변 템플릿
> "도메인과 JPA 엔티티를 통합하면서 보존 골격의 BaseEntity 를 그대로 쓸지 검토했는데, 두 가지가 걸렸습니다. 복합 자연키 테이블은 surrogate id 전제와 양립하지 않고, 단위 테스트가 의존하는 재구성 생성자의 id 파라미터와도 충돌했습니다. 그래서 audit 관심사만 분리한 MappedSuperclass 를 신설하고 식별자는 각 Aggregate 가 자기 키 전략에 맞게 선언했습니다 — 상속은 공통 관심사가 진짜 공통일 때만 쓴다는 원칙의 사례라고 생각합니다."

### 출처
- ERD: `docs/design/04-erd.md` §5 E4 (2026-05-30 확정), §2.7·2.11 복합 PK
- rule 07 (Domain↔JPA 통합), Q7 (묶음 진행)

---

## Q9. (날짜) — (다음 질문)
