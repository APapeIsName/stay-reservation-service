# ADR-004 — 예약 트랜잭션 내 구간별 차등 동시성 락 전략

**Status**: Accepted
**Date**: 2026-06-16
**Round**: Round 4 (트랜잭션·동시성)

> ⚠️ **재검토 트리거** — ① 멀티 인스턴스 배포로 자원이 단일 DB 행을 넘어서면(분산락/Redis 검토) ② 재고 경합이 극단적으로 높아 비관락 대기가 병목이 되면(원자적 UPDATE + 도메인 순수성 포기 재논의) ③ 결제(PG) 등 외부 비동기가 예약 흐름에 들어오면(트랜잭션 분리 + PENDING 도입, ADR-002 재검토) → 각각 새 ADR.

---

## Context

Round 3 완료 시점에 예약·찜 흐름에는 **락이 0건**(`@Version` 전무)이라, 동시성 하에서 정합성이 깨지는 세 지점이 있다:

| 구간 | 위치 | 문제 |
|---|---|---|
| **다일자 재고** | `ReservationService.reserve()` — `validateAvailability`(검증) ↔ `consumeAll`→`DailyRoom.consumeOne()`(차감) | TOCTOU 갭. 잔여 1실에 동시 진입 시 둘 다 `canConsume()` 통과 후 둘 다 `reservedRooms += 1` → **오버부킹** |
| **쿠폰 단일사용** | (신규) `CouponIssue.status: AVAILABLE → USED` | 같은 발급분을 두 예약이 동시 사용 → **이중 사용** |
| **찜 수** | `WishlistService` — `Property.wishCount += 1` + `add()` exists-검사 | 여러 유저 동시 찜 → 카운터 lost update / 같은 유저 더블파이어 → 중복 row + 카운트 부풀림 |

"하나의 락 전략으로 통일할지, 구간마다 다르게 갈지"가 결정 필요. 발제는 "각 도메인의 특성에 맞는 전략을 선택"하라고 명시한다(`docs/curriculum/round-4.md`).

핵심 통찰 — **"동시 요청들에게 무엇이 일어나야 하는가"** 가 구간마다 다르다:
- 재고: **일부는 반드시 거절**되어야 함(방이 한정) → 한도(invariant) 보호
- 쿠폰: **정확히 1명만 성공**해야 함(단일사용)
- 찜: **전원 성공**해야 함(한도 없음 — 거절할 명분 자체가 없음)

## Decision

**구간별로 차등 전략을 적용한다** — 단일 전략 통일이 아니라, 각 구간의 작업 모양과 경합 특성에 맞춘다.

### 1. 다일자 재고 = 비관락 (`PESSIMISTIC_WRITE`)
- 예약 시 재고 행을 `SELECT ... FOR UPDATE` 로 **읽는 순간 잠금** → TOCTOU 갭이 락 보유 구간으로 흡수됨.
- **예약 전용 잠그는 finder 를 분리**: domain port 는 의도(`findForReserve`)만, infrastructure adapter 가 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 부여 (DIP — port 는 락을 모름, rule 19). 검색·상세 읽기 경로는 락 없이 유지.
- **데드락 회피 = 날짜 ASC 정렬 락 획득** (`ORDER BY date ASC`, reserve·cancel 동일). 겹치는 기간이 동시에 와도 모두 작은 날짜부터 잠그니 사이클 불가(InnoDB 인덱스 스캔 순서와 일치).
- 경합 시 **즉시 409(fail-fast)**, 재시도 아님.

### 2. 쿠폰 단일사용 = 낙관락 (`@Version`)
- `CouponIssue` 에 `@Version` → 동일 발급분 동시 사용 시 한 명만 커밋, 진 쪽은 `OptimisticLockException` → 409.
- `CouponIssue.markUsed()` 가드를 도메인에 보존. 경합 희박(쿠폰=1유저 소유)이라 낙관락의 평상시 무비용이 발휘됨.

### 3. 찜 = 원자적 증가 + DB 유니크 제약
- 카운터: `UPDATE property SET wish_count = wish_count + 1 WHERE id = ?` (취소 `- 1 WHERE wish_count > 0`). 상대 증가라 lost update 원천 불가, 전원 성공, 읽기 핫 행을 락으로 묶지 않음.
- 중복(같은 유저): `UNIQUE(user_id, property_id)` + 앱 선검사 2층(rule 10). 찜 해제는 hard delete 이므로 단순 복합 유니크로 충분. **신규 INSERT 중복은 삽입 전 잠글 행이 없어 행 락으로 못 막음 → 제약 계층에서 방어**.

세 전략은 같은 예약 `@Transactional` 안에서 공존한다(행·기법 상이로 무충돌).

## Consequences

### ✅ 긍정적
- 각 구간이 "동시 요청에 일어나야 할 일"에 정확히 부합 — 재고는 거절, 쿠폰은 1명, 찜은 전원.
- 비관락이 다일자 묶음 차감의 자연스러운 단위(한 번 잠그고 통째로) — 기존 `consumeOne()`/`validateAvailability` 구조를 거의 그대로 유지.
- 낙관락은 쿠폰 저경합 구간에서 평상시 락 비용 0.
- 원자적 증가는 찜 핫 카운터에서 재시도·거절 없이 정확.
- 도메인 규칙(`consumeOne`, `markUsed`)이 객체에 보존됨(찜 카운터만 예외적으로 SQL 상대증가 — 한도 invariant 가 없어 손실 적음).
- 면접 서사 강력: "한 트랜잭션에 락 도구 넷, 작업 모양 기준으로 분기".

### ⚠️ 부정적 / 비용
- **혼합 전략의 인지 비용** — 구간마다 다른 메커니즘이라 신규 개발자가 "왜 여기만 다른가"를 이해해야 함(본 ADR·questions.md 가 그 근거).
- **비관락 대기** — 재고 경합 시 트랜잭션 직렬화로 처리량 저하. 락 보유 구간을 짧게 유지해야(외부 호출 금지).
- **낙관락 충돌 예외 처리** — `OptimisticLockException` → 409 매핑 필요(advice).
- **찜 원자적 증가의 도메인 순수성 희생** — `incrementWish()` 가 SQL 상대증가로 이동(anemic). 단 한도 invariant 가 없어 비용 최소.

### 🔄 후속 영향
- `DailyRoomJpaRepository` 에 잠그는 finder 추가(`findForReserve`), reserve·cancel 의 재고 조회를 ASC 정렬로 고정.
- `CouponIssue`·`Property`(찜) 영속 모델에 `@Version`/유니크 인덱스 반영(ERD `04-erd.md` 확장).
- `ErrorType` 에 쿠폰 에러 코드 + `OptimisticLockException`·`DataIntegrityViolationException` advice 매핑(rule 09).
- 동시성 L3 테스트(실 MySQL + `ExecutorService`/`CountDownLatch`)로 각 구간 검증.

## Alternatives Considered

### 대안 B — 전부 비관락 (단일 전략 통일)
- 장점: 멘탈 모델 1개, 단순.
- 단점: 쿠폰·찜에 불필요한 락 비용. 찜 핫 카운터는 Property 읽기 경로와 락 경합. "전원 성공해야 하는" 찜에 거절-대기는 부적합.
- 거절: 구간 특성 무시 → 정합성은 맞아도 처리량·UX 손해.

### 대안 C — 전부 낙관락 (`@Version` 통일)
- 장점: 평상시 락 없음, 처리량↑.
- 단점: **재고 고경합 시 재시도 폭풍**(10명/5방 → 1명 성공·9명 버전충돌). 다일자 부분충돌 재시도 복잡. 찜은 거절 명분이 없는데 거절.
- 거절: 재고처럼 거절이 필요한 구간에 부적합.

### 대안 D — 재고에 원자적 조건부 UPDATE
- 모양: `UPDATE daily_room SET reserved_rooms = reserved_rooms + 1 WHERE ... AND reserved_rooms < total_rooms`.
- 장점: 최고 성능, 실무 최다.
- 단점: 예약은 "가격 읽기 + 검사 + 차감"의 **합성 작업** — 단일 `+1` 로 안 떨어지고, 가격을 따로 읽어야(MySQL UPDATE 는 RETURNING 없음) 자연스러운 한 덩어리가 쪼개짐. `DailyRoom.consumeOne()` 도메인 규칙이 SQL 로 빠짐.
- 거절: 합성 작업엔 비관락이 더 정합적. (단 찜은 순수 단일 증가라 같은 도구를 채택 — Q3)

### 대안 E — SERIALIZABLE / 분산락(Redis) / Named Lock / synchronized
- 거절: SERIALIZABLE 은 과함(광범위 락). 분산락은 멀티 인스턴스·다음 라운드 영역(단일 DB 행은 행 락으로 충분). Named Lock 불필요. `synchronized` 는 단일 JVM 한정이라 멀티 파드 무용.

## Related
- 결정 누적: [`docs/round-4/03-questions.md` Q1·Q2·Q3](../round-4/03-questions.md)
- 상태 머신: [ADR-002](./ADR-002-immediate-confirmed-reservation.md) (즉시 CONFIRMED 유지 — Q4, 쿠폰 동기 적용으로 PENDING 불요)
- 영속 구조: [ADR-003](./ADR-003-single-daily-room-table.md) (단일 `daily_room` — 동시성 충돌이 한 행에 모이는 비용이 본 ADR 의 비관락으로 관리됨)
- rule: [10 유일성(앱+DB)](../../.claude/rules/10-uniqueness-app-and-db.md), [18 도메인 모델링](../../.claude/rules/18-domain-modeling.md), [19 레이어드·DIP](../../.claude/rules/19-layered-architecture-dip.md), [09 예외 매핑](../../.claude/rules/09-api-response-and-exception-mapping.md)
- 발제: [`docs/curriculum/round-4.md`](../curriculum/round-4.md) (락 전략 — 좌석 예매 예시)
- 유니크 제약 리서치: `docs/round-4/03-questions.md` Q3 (Doyensec·thoughtbot·PostgreSQL/MySQL 공식 등)

---

## 면접 답변 템플릿

> "한 예약 트랜잭션 안에 동시성 구간이 셋 있는데, '동시 요청들에게 무엇이 일어나야 하는가'를 기준으로 전략을 갈랐습니다.
>
> 재고는 방이 한정돼 *일부는 반드시 거절*돼야 하고 검증·가격읽기·차감이 같은 행들에 묶인 합성 작업이라, 비관락으로 필요한 날짜를 전부 잠그고 통째로 처리했습니다. 다일자라 데드락이 핵심인데 모든 트랜잭션이 날짜 오름차순으로만 잠그게 고정하면 사이클이 안 생깁니다.
>
> 쿠폰 단일사용은 *정확히 1명만 성공*이라 낙관락의 철학과 맞고, 쿠폰은 한 유저 소유라 구조적 저경합이라 평상시 무비용입니다.
>
> 찜은 한도가 없어 *전원 성공*해야 하니 거절·재시도가 안 맞고, `wish_count = wish_count + 1` 원자적 증가로 lost update 를 원천 차단했습니다. 같은 유저 중복은 신규 INSERT라 행 락으로 못 막아(잠글 행이 없음) 유니크 제약으로 막았고요.
>
> 흥미로운 건 같은 원자적 UPDATE 가 재고엔 부적합(합성작업)이고 찜엔 정답(순수 증가)이라는 점이었습니다. 정합성은 모두 동일하게 보장하면서, 갈린 건 작업의 모양과 경합이 만드는 처리량·UX였습니다."
