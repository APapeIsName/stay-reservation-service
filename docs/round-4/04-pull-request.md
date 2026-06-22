# [Round 4] 트랜잭션·동시성 락·쿠폰 도메인 — 구간별 차등 락

> base: `main` ← head: `round-4` (컨벤션: round-N → main 누적). Round 4 작업 delta = `b6a3a39..ddc3ce7` (14 커밋).

## 배경

예약 시 일자별 재고 / 쿠폰 / 결제 금액의 정합성을 트랜잭션으로 보장하고, 더블부킹 등 동시성 이슈를 제어해야 함. 정액/정률 쿠폰 도메인을 신설해 예약에 할인을 적용함. 핵심 판단은 **"동시 요청들에게 무엇이 일어나야 하는가"** 에 따라 구간별로 다른 락 전략을 선택하는 것.

## 변경 사항 (What)

**도메인 (`modules/domain`)**
- `coupon/Coupon` — 정액/정률 할인 계산(`calculateDiscount`), 어드민 수정(`update`). 락 없음
- `coupon/CouponIssue` — 단일사용(`markUsed`)·`@Version` 낙관락·만료 파생(`isUsable`)
- `reservation/PriceSnapshot` — 할인 3분해(priceBeforeDiscount/discountAmount/finalPrice, 파생은 `@get:Transient`)
- `reservation/Reservation` — `couponId` 감사 필드 + `confirm` 시그니처 확장
- `dailyroom/DailyRoomRepository.findForReserve` — 예약 전용 잠그는 포트
- `property/PropertyRepository.incrementWishCount/decrementWishCount` — 원자적 카운터 포트
- `support/error/ErrorType.UNAUTHORIZED` 신설

**application (`apps/stay-api`)**
- `coupon/CouponService`(발급·내쿠폰 상태 파생), `coupon/CouponAdminService`(템플릿 CRUD·발급내역)
- `reservation/ReservationService.reserve` — 쿠폰 검증·할인·markUsed 통합(단일 트랜잭션)
- `wishlist/WishlistService` — 원자적 카운터 위임

**infrastructure**
- `dailyroom/DailyRoomJpaRepository.findForReserve` — `@Lock(PESSIMISTIC_WRITE)` + JPQL `ORDER BY date ASC`
- `coupon/*` — Coupon·CouponIssue JPA 어댑터
- `property/PropertyJpaRepository` — `@Modifying` 원자적 `wish_count ± 1`

**interfaces**
- `api/v1/coupon` — `POST /coupons/{id}/issue`, `GET /users/me/coupons`
- 예약 API — `couponId`(NULLABLE) + 응답 금액 3분해
- `api/admin/v1/coupon` — `/api-admin/v1/coupons` CRUD + 발급내역
- `api/admin/LdapAuthInterceptor` — `X-Stay-Ldap` 헤더 검증(401), `/api-admin/**` 등록

**문서**
- `docs/adr/ADR-004-lock-strategy.md`, `docs/round-4/{02-tdd-plan,03-questions}.md`, `docs/design/{03-class-diagram,04-erd}.md` 확장

## 설계 결정 (How)

### 1. 구간별 차등 락 (핵심 — ADR-004)

| 구간 | 동시 요청 결과 | 전략 | 근거 |
|---|---|---|---|
| 재고 | *일부 거절* (방 한정) | 비관락 `FOR UPDATE` + 날짜 ASC | 고경합 · 합성작업(가격+검사+차감) · 데드락 회피 |
| 쿠폰 | *정확히 1명* | 낙관락 `@Version` | 저경합(1유저 소유) · 단일 전이 |
| 찜 | *전원 성공* (한도 없음) | 원자적 `wish_count + 1` | 거절 명분 없음 · 상대 증가로 lost update 불가 |

- 재고: 검색·상세 읽기 경로는 락 없는 `findByRoomTypeAndDateBetween` 유지, 예약/취소만 `findForReserve`. port 는 의도만, 락 어노테이션은 adapter(DIP).
- 데드락 회피: 다일자 락을 항상 날짜 오름차순으로 획득(인덱스 스캔 순서와 일치).
- 찜 중복(같은 유저): 신규 INSERT 중복은 행 락으로 못 막으므로 `wishlist` 복합 PK 유니크가 차단.

### 2. 쿠폰 2 Aggregate

`Coupon`(템플릿·어드민 관리) ↔ `CouponIssue`(발급분·유저 소유·독립 락 단위) 분리 + ID 참조. 생명주기·동시성 단위가 달라 RoomType 중첩과 대조. 할인 계산은 단일 객체에 담겨 `Coupon.calculateDiscount` 도메인 메서드(도메인 서비스 불필요).

### 3. PriceSnapshot 3분해 — 파생 컬럼 회피

`discountAmount` 만 저장, `priceBeforeDiscount`/`finalPrice` 는 `@get:Transient` 파생. ERD 의 중복 컬럼(total_price/final_price) 우려 해소. `totalPrice() = finalPrice` 로 기존 환불 로직 무변경.

### 4. 어드민 인증 — 스텁

`X-Stay-Ldap` 헤더 존재 검증 인터셉터(없으면 401). 실 LDAP 연동은 Round 4 핵심(동시성)에서 벗어나 미도입. 헤더명은 rule 01(loopers 흔적 0, grep 게이트) 준수 위해 `X-Loopers-Ldap` → `X-Stay-Ldap`.

## 테스트

| 등급 | 범위 | 결과 |
|---|---|---|
| L1/L2 도메인 | VO·Aggregate·Domain Service | 229건, 실패 0 |
| L1/L2 apps | Service 오케스트레이션 (Fake) | 86건, 실패 0 |
| L3 동시성·영속 | CC-01(더블부킹)·CC-08(쿠폰)·CC-09(찜) | **Docker 차단** — 컴파일·ktlint 검증, 실행은 CI |
| L4 E2E | 컨트롤러 | **Docker 차단** 동일 |

- TDD Red → 분리 구현 Green → Refactor 사이클(rule 14). 카탈로그 ID 를 `@DisplayName` 에 노출.
- ktlint · rule 01 grep 게이트(loopers 등 0건) 통과.
- L3 는 Testcontainers ↔ Docker Desktop 비호환으로 로컬 실행 차단. 락 구현은 코드·컴파일 검증, 실 동시성 검증은 Docker 환경 정상화 시.

## 미해결 / 후속

- L3/L4(동시성·E2E) 실행 검증 — Docker 환경 정상화 시.
- `OptimisticLockingFailureException` → 409 advice 매핑(현재 도메인 `CONFLICT` 만 매핑).
- 어드민 Property/RoomType CRUD, 결제·예약 PENDING 2단계 — 범위 밖/다음 라운드.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
