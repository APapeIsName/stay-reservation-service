# Round 4 — Q&A 누적

> 형식: [`15-process-conventions.md`](../../.claude/rules/15-process-conventions.md) §4 — 질의자/제안자 명시, 면접 답변 템플릿 포함, 역사 보존.
>
> Round 4 주제: **DB 트랜잭션 · 동시성 제어(락) · 쿠폰 도메인**. 핵심 결정은 "예약 트랜잭션 안의 세 동시성 구간(재고·쿠폰·찜)에 각기 다른 전략을 쓴다"로 수렴 (Q1~Q3).

---

## Q1. (2026-06-16) 다일자 재고 차감 동시성 — 비관락 채택

**질의자**: 사용자 (구간별 락 전략 정의) · **제안자**: Claude (후보 분석) · **결정**: 사용자

### 맥락
`ReservationService.reserve()` 는 `validateAvailability`(검증, 읽기)와 `consumeAll`→`DailyRoom.consumeOne()`(차감, 쓰기) 사이에 **TOCTOU 갭**이 있다. 잔여 1실에 두 트랜잭션이 동시 진입하면 둘 다 `canConsume()` 통과 후 둘 다 `reservedRooms += 1` → **오버부킹(lost update)**. 현재 락 0건(`@Version` 전무).

### 답 또는 결정
**비관락(`@Lock(PESSIMISTIC_WRITE)` = `SELECT ... FOR UPDATE`) 채택** + 다음 부속 결정:
- **예약 전용 잠그는 finder 를 분리** — port 에는 의도(`findForReserve`)만, adapter 에 기법(`@Lock`)을. 검색·상세 읽기 경로(`findByRoomTypeAndDateBetween`)는 락 없음 유지 (DIP, rule 19).
- **데드락 회피: 날짜 ASC 정렬 락 획득** — `ORDER BY date ASC`. reserve/cancel 동일 순서. 겹치는 기간이 와도 모두 작은 날짜부터 잠그니 사이클 불가.
- **경합 시 즉시 409 (fail-fast)** — 재시도 아님. 재고가 차면 재시도해도 어차피 없으니 도메인 의미에 부합.

### 후보 비교 (재고는 "거절이 필요한" 구간)
| 후보 | 판정 | 이유 |
|---|---|---|
| 비관락 | ✅ 채택 | 다일자 묶음 차감을 한 번 잠그고 통째로. "방 없으면 즉시 거절"에 부합 |
| 낙관락 `@Version` | ❌ | 고경합 시 재시도 폭풍. 다일자 부분충돌 재시도 복잡 |
| 원자적 조건부 UPDATE | ❌ | reserve 는 "가격 읽기+검사+차감" **합성 작업** — `+1` 단일 변이로 표현 불가, 가격 읽기가 분리됨(MySQL UPDATE 는 RETURNING 없음) |
| SERIALIZABLE / 분산락(Redis) / Named Lock / synchronized / 큐 / 파생행 | ❌ | 과함 / 멀티인스턴스·다음 라운드 / 단일 JVM 한정 / 설계 변경 — 단일 MySQL+단일 앱 범위 밖 |

### 면접 답변 템플릿
> "예약의 검증과 재고 차감 사이 TOCTOU 갭에서 더블부킹이 납니다. 다일자 묶음 차감은 비관락으로 '필요한 날짜를 전부 잠그고 통째로 처리'가 가장 명료해서 `SELECT ... FOR UPDATE` 를 택했습니다. 단 다일자라 데드락이 핵심인데, 모든 트랜잭션이 날짜 오름차순으로만 락을 획득하게 고정하면(인덱스 스캔 순서와 일치) 겹치는 기간이 동시에 와도 사이클이 안 생깁니다. 읽기 경로까지 락이 번지지 않게 예약 전용 잠그는 finder 를 분리했고, 락 어노테이션은 adapter 에만 둬 도메인 port 는 락을 모르게 했습니다. 원자적 UPDATE 도 검토했지만 예약은 가격까지 같이 읽는 합성 작업이라 단일 `+1` 로 안 떨어져 제외했습니다."

### 출처
- 코드: `apps/stay-api/.../application/reservation/ReservationService.kt:55`, `modules/domain/.../dailyroom/DailyRoom.kt:69`, `StayAvailabilityService.kt`
- 반영 예정: ADR-004, rule 신설 후보

---

## Q2. (2026-06-16) 쿠폰 단일사용 동시성 — 낙관락 채택

**질의자**: 사용자 · **제안자**: Claude · **결정**: 사용자

### 맥락
같은 발급 쿠폰(`CouponIssue`)을 두 예약이 동시에 사용하면 두 번 쓰일 수 있다. 작업은 한 행의 `status: AVAILABLE → USED` **단일 전이**. 재고와 정반대 성격 — ① 단일 전이(합성 아님) ② **경합 희박**(쿠폰은 한 유저 소유 → 본인 더블파이어 때만).

### 답 또는 결정
**낙관락(`@Version` on `CouponIssue`) 채택.** 같은 발급분 동시 사용 시 한 명만 커밋 성공, 진 쪽은 버전 충돌 → 409. `CouponIssue.markUsed()` 가드를 도메인에 보존. 재고의 비관락과 같은 트랜잭션에서 공존(행/기법 상이).

### 결정 기준 — "동시 요청에게 무엇이 일어나야 하나"
- 쿠폰은 **정확히 1명만 성공**해야 함 → 발제의 낙관락 철학("경쟁자 중 1명만 성공·나머지 실패")과 정확히 일치.
- **경합 희박**이 낙관락의 홈그라운드(평상시 충돌 0). "쿠폰=1유저 원칙"이 곧 구조적 저경합 조건.
- ⚠️ 정합성은 비관락과 **동일**(둘 다 오버유즈 불가). 갈린 건 경합 수준이 만드는 UX. 낙관락의 약점은 "정합성"이 아니라 "고경합 시 가짜 실패·재시도 churn"인데, 쿠폰은 저경합이라 무해.

### 면접 답변 템플릿
> "같은 예약 트랜잭션 안인데 재고는 비관락, 쿠폰은 낙관락을 썼습니다. 쿠폰 단일사용은 '한 명만 성공·나머지 실패'가 정확히 낙관락의 철학이고, 쿠폰은 한 유저 소유라 구조적으로 저경합이라 낙관락의 평상시 무비용이 그대로 발휘됩니다. 재고처럼 경합이 몰리면 낙관락은 가짜 실패가 쏟아져 재시도가 필요하지만(10명/5방이면 1명만 성공·9명 버전충돌), 쿠폰은 그 상황이 아니라 적합합니다. 둘 다 정합성은 동일하게 보장되고, 갈린 건 경합 수준이 만드는 UX였습니다."

### 출처
- 발제: `docs/curriculum/round-4.md` (낙관락 — 좌석 예매 예시)
- 신규 도메인: `CouponIssue` (Q5 참조)

---

## Q3. (2026-06-16) 찜 동시성 — 원자적 증가 + `UNIQUE(user_id, property_id)`

**질의자**: 사용자 (유니크 제약 근거 요구 → 리서치 지시) · **제안자**: Claude · **결정**: 사용자

### 맥락
찜은 두 race 가 있다 — ① **여러 유저가 같은 숙소** → `Property.wishCount += 1` lost update, ② **같은 유저 더블파이어** → `add()` 의 exists-검사 TOCTOU 로 중복 Wishlist + 카운트 부풀림. 찜은 **거절 사유가 없는**(한도 없는) 핫 카운터라 "전원 성공해야" 한다.

### 답 또는 결정
- **카운터(①): 원자적 증가** `UPDATE property SET wish_count = wish_count + 1 WHERE id=?` (취소는 `- 1 WHERE wish_count > 0`). 상대 증가라 **lost update 원천 불가**, 전원 성공(거절·재시도 없음), 읽기 핫 행(Property)을 락으로 묶지 않음.
- **중복(②): `UNIQUE(user_id, property_id)`** + 앱 선검사 2층(rule 10) — 찜 해제는 **hard delete** 이므로 단순 복합 유니크로 충분.

### 유니크 제약 — 리서치 결론(출처 기반)
사용자가 "유니크 제약이 왜 필요한가, 업계·기원·의도"를 요구해 웹 리서치 수행:
- **락으로 못 막는 영역**: 신규 INSERT 중복은 **삽입 전 잠글 행이 없어** `FOR UPDATE` 로 못 막는다. 방어는 "락 계층"이 아니라 "제약 계층". [Doyensec — A Race to the Bottom]
- **기원**: Codd(1970) 관계모델 = 집합(중복 튜플 불가) → 후보키 → UNIQUE. PK = 선택된 후보키 + NOT NULL. UNIQUE 가 PK 보다 먼저(SQL-86) 표준화.
- **업계 관행**: upsert(`ON CONFLICT DO NOTHING`/`ON DUPLICATE KEY`)·insert-and-catch·멱등성 키 — 모두 **유니크 제약을 백본**으로. 앱 선검사는 친절한 에러·가독성용(기능적 보증은 DB 제약). [thoughtbot, Enterprise Craftsmanship]
- ⚠️ "유니크만이 **유일한** 방법"은 과장 — SERIALIZABLE·advisory lock 도 가능하나 모두 "전 트랜잭션 협조" 필요. UNIQUE 는 **무조건적 최종 보루**라 가장 신뢰 가능.
- 좋아요/찜 사례: `(user,item)` 복합 유니크 + 비정규화 카운터 + 원자적 `+1` 이 표준(Instagram '저스틴 비버 문제', Tecoble 실측 약 520배).

### 멋진 대칭 (면접 포인트)
같은 **원자적 UPDATE** 가 재고에선 기각(합성작업)·찜에선 정답(순수 상대증가). 같은 도구가 작업 모양에 따라 정반대 판정.

### 면접 답변 템플릿
> "찜은 한도 없는 카운터라 동시 요청이 전원 성공해야 합니다 — 누군가를 실패시킬 명분이 없죠. 그래서 락이나 낙관락이 아니라 `wish_count = wish_count + 1` 원자적 증가를 썼습니다. 상대 증가라 lost update 가 원천적으로 안 나고, 읽기 핫 행인 Property 를 락으로 묶지도 않습니다. 같은 유저 더블파이어 중복은 별개 문제인데, 신규 INSERT 중복은 삽입 전 잠글 행이 없어 행 락으로 못 막습니다 — 유니크 제약(또는 Serializable)만이 막죠. 그래서 `(user_id, property_id)` 복합 유니크를 두고 앱 선검사는 친절한 메시지용으로 병행했습니다. 이미 loginId 유일성에 쓰던 패턴이라 일관됩니다."

### 출처
- 코드: `apps/stay-api/.../application/wishlist/WishlistService.kt`, `modules/domain/.../property/Property.kt:137`
- rule 10(앱 선검사 + DB 유니크), rule 09(예외 매핑)
- 리서치: Doyensec / thoughtbot / Enterprise Craftsmanship / PostgreSQL·MySQL 공식 / Tecoble / Instagram 사례 (워크플로우 `unique-constraint-research`)

---

## Q4. (2026-06-16) 예약 상태 — 즉시 CONFIRMED 유지 (PENDING 미도입)

**제안자**: Claude · **결정**: 사용자

### 맥락
발제 예시 흐름이 `3. 예약 엔티티 생성 및 저장 (PENDING)` 으로 끝나 PENDING 도입 여부가 쟁점. 현재는 ADR-002 로 생성 즉시 CONFIRMED.

### 답 또는 결정
**현행 즉시 CONFIRMED 유지.** Round 4 쿠폰 적용·재고 차감은 전부 `reserve()` 단일 트랜잭션 내 **동기** 처리 — 외부 결제(PG) 호출이 범위 밖("결제 금액 정합성" = 최종 금액 계산 정확성). PENDING 은 "비동기 공백(결제 승인 대기 등)"이 있을 때만 의미 — 지금 도입하면 CONFIRMED 로 올릴 트리거가 없어 영원히 PENDING 에 갇힘. `ReservationStatus.PENDING` 은 enum 정의만 유지(미사용), 결제 라운드에서 깨움.

### 면접 답변 템플릿
> "발제 예시가 PENDING 으로 저장하길래 도입을 검토했지만, PENDING 은 결제 승인 같은 비동기 공백이 있을 때만 의미가 있습니다. 이번 범위는 쿠폰·재고를 한 트랜잭션에서 동기로 끝내므로 PENDING 을 만들면 CONFIRMED 로 전이시킬 트리거가 없어 오히려 정합성이 깨집니다. 그래서 즉시 CONFIRMED 를 유지하고 PENDING 은 결제 라운드로 미뤘습니다."

### 출처
- `docs/adr/ADR-002-immediate-confirmed-reservation.md`, `modules/domain/.../reservation/ReservationStatus.kt`

---

## Q5. (2026-06-16) Coupon 도메인 — 2 Aggregate + `Coupon.calculateDiscount()`

**제안자**: Claude · **결정**: 사용자

### 맥락
신규 Coupon 도메인의 Aggregate 경계와 할인 계산 위치 확정 필요.

### 답 또는 결정
- **2 Aggregate**: `Coupon`(템플릿 — 어드민 관리, type/value/minOrderAmount/expiredAt) + `CouponIssue`(발급분 — 유저 소유, status, 단일사용·낙관락 단위). `CouponIssue` 는 `couponId` **ID 참조**(rule 18 §5).
  - 대조: Property↔RoomType 은 1 Aggregate(중첩, 유한 내부 entity), Coupon↔CouponIssue 는 2 Aggregate(분리, 유저별 무한 증식·독립 락 단위).
- **할인 계산: `Coupon.calculateDiscount(preDiscountAmount)` 도메인 메서드** — 단일 객체에 담기므로 Domain Service 불필요(rule 19 §4). FIXED: `min(value, pre)`, RATE: `floor(pre × value / 100)`, minOrderAmount 미달 시 예약 실패.
- **CouponIssue 는 참조 방식으로 경량**: `(couponId, userId, status, issuedAt, usedAt, @Version)`. 적용된 할인액은 예약 `PriceSnapshot` 3분해(할인전/할인액/최종)에 기록. (어드민 수정이 기발급분에 소급되는 게 문제되면 스냅샷으로 승격 — Phase 1 재검토)
- 책임 배치: 소유=`CouponIssue.belongsTo`, 단일사용=`CouponIssue.markUsed()`(낙관락), 할인·만료·최소금액=`Coupon`.

### 면접 답변 템플릿
> "쿠폰은 템플릿과 발급분의 생명주기·소유·락 단위가 달라 2개 Aggregate 로 쪼개고 ID 로 참조했습니다. RoomType 은 숙소에 묶인 유한한 내부 entity라 중첩했지만, 발급분은 유저마다 무한 증식하고 독립적으로 잠기는 단위라 분리한 거죠 — '언제 중첩하고 언제 쪼개나'의 대조 사례입니다. 할인 계산은 템플릿 필드만으로 끝나 단일 객체(Coupon)의 메서드로 뒀고(도메인 서비스 불필요), 실제 적용 할인액은 예약 스냅샷에 박아 감사 추적을 남깁니다."

### 출처
- rule 18(도메인 모델링), rule 19(레이어드·DIP), rule 20(DTO 전략)
- 신규: `modules/domain/.../coupon/{Coupon,CouponIssue,CouponRepository,CouponIssueRepository}`

---

## Q6. (2026-06-16) 어드민 API — stay-api 내 경로 분리 + 헤더 스텁 인터셉터

**제안자**: Claude · **결정**: 사용자

### 맥락
어드민 쿠폰 CRUD(`/api-admin/v1`, `ldap_required`) 를 어디에 두고 인증을 어떻게 할지. 현재 `:apps:stay-admin` 없음, `SecurityConfig` 전무, 헤더는 "형식적 통과만".

### 답 또는 결정
- **모듈(e): stay-api 내 `/api-admin/v1` 경로 분리** — 단일 앱 경로 구분 + 인터셉터. 모듈 분리(`:apps:stay-admin`)는 인증·배포 실분리 요구가 생길 때 ADR 트리거.
- **인증(c): 헤더 스텁 인터셉터** — `X-Loopers-Ldap` 값 검사 + 실패 401/403. `ErrorType.UNAUTHORIZED` 신설. 고객 API(`user_required`)는 기존 유저 헤더 컨벤션 유지. LDAP 실연동은 Round 4 학습 목표(동시성)에서 벗어나 과투자 → 스텁.

### 면접 답변 템플릿
> "어드민은 포트폴리오 범위라 단일 앱 안에서 `/api-admin/v1` 경로로 분리하고, 인증은 인터셉터에서 ldap 헤더를 검증해 실패 시 401/403 을 내는 스텁으로 구현했습니다. 실제 LDAP 연동은 이번 주 핵심인 트랜잭션·동시성에서 벗어나 과투자라 판단했고, 모듈을 별도로 떼는 건 배포·인증을 실제로 분리해야 할 때 ADR 로 다루기로 했습니다."

### 출처
- `docs/design/01-requirements.md §3.1`(헤더 컨벤션), `modules/domain/.../support/error/ErrorType.kt`(UNAUTHORIZED 신설 예정)

---

## Q7. (2026-06-14) 발제 원문 로컬 전용 — `docs/curriculum/` gitignore

**질의자**: 사용자 · **결정**: 사용자

### 맥락
부트캠프 발제 원문을 GitHub 에 올리지 않으려는 의도(저작권·공개 범위). round-1/2 는 이미 origin 에 푸시됨.

### 답 또는 결정
- `docs/curriculum/` 를 `.gitignore` 에 등재 → 신규(round-3/4) 발제 자동 제외.
- 기 푸시된 round-1/2 는 `git rm --cached` 로 tip 에서만 제거(로컬 유지). **force-push/히스토리 재작성 안 함**("앞으로만 제외").
- 범위: **발제 원문만** 로컬 전용. 작업 문서(`docs/round-N/`, `docs/design/`, `docs/adr/`)는 계속 git 추적 — 포트폴리오 자산.
- 발제는 로컬에서 chmod 444 동결 유지(rule 02).

### 출처
- 메모리: `curriculum-docs-local-only`, rule 02(발제 동결)

---

> **다음 Q 후보**: 할인 계산 끝전 처리 세부 / CouponIssue 참조 vs 스냅샷 승격 트리거 / 동시성 테스트 하니스 설계 — Phase 1~6 진행 중 발생 시 누적.
