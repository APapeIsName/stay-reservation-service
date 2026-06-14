# ADR-002 — `POST /reservations` 즉시 CONFIRMED

**Status**: Accepted
**Date**: 2026-05-28
**Round**: Round 2 (설계 단계)

> ⚠️ **재검토 가능성** — 결제 도메인 도입 (Round 3+) 시 PENDING 상태를 활용하는 패턴으로 *확장* 가능. 본 ADR 이 *대체* 되는 게 아니라 *부가 확장* 될 가능성이 높음.

---

## Context

Round 2 의 시나리오 (`docs/curriculum/round-2-scenario.md`) 는 예약 상태 머신을 다음과 같이 명시한다:

```
PENDING → CONFIRMED → CHECKED_IN → CHECKED_OUT / CANCELLED
```

동시에 시나리오는 **결제 도메인이 본 라운드 out-of-scope** 임도 명시한다:

> *"결제는 과정 진행 중, 추가로 개발하게 됩니다!"*

이 두 사실이 충돌한다. PENDING 의 *의미* 가 보통 *"결제 진행 중 / 결제 응답 대기"* 인데, 결제가 없는 본 라운드에서 `POST /reservations` 의 종착 상태가 무엇이어야 하는가?

### 추가 맥락 — 빅테크 패턴

- **Airbnb** — PENDING (호스트 24h 승인 대기) → CONFIRMED. P2P 모델
- **Booking.com** — 즉시 확정 일반적
- **야놀자 / 여기어때** — *"결제 즉시 100% 예약 확정"* (한국 OTA 표준)
- 우리 시나리오에는 호스트 승인 워크플로 없음 → 한국 OTA 패턴이 자연

## Decision

**`POST /api/v1/reservations` 는 검증·일자별 재고 차감·CONFIRMED 까지 단일 `@Transactional` 트랜잭션으로 처리한다. 본 라운드에서 PENDING 상태는 enum 자리만 정의하고 사용하지 않는다.**

구체적으로:
- 예약 상태 enum: `PENDING, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED` (5종 정의)
- 진입 상태 (initial state) = **CONFIRMED**
- PENDING 은 `01-requirements.md § 7` 의 상태 머신에 *"💡 결제 도입 시 활용 자리"* 로 주석 표시
- 시퀀스 다이어그램 (`02-sequence-diagrams.md § 2`) 에서 검증·차감·CONFIRMED 가 한 트랜잭션 박스 (`rect` + 🔒) 안에 표현

## Consequences

### ✅ 긍정적
- **시나리오 부합** — 결제 out-of-scope 인 상황에서 PENDING 으로 두고 mock 으로 confirm 하는 흐름은 *허구의 트랜잭션 분리* 가 됨. 즉시 CONFIRMED 가 *진짜 일관성 보장* 형태
- **한국 OTA 패턴 일치** — 야놀자/여기어때의 *"결제 즉시 100% 예약 확정"* 과 자연 정렬
- **단순성** — `POST /reservations` 한 호출에 검증·차감·확정. 일관성 보장이 *명시적*
- **시퀀스 다이어그램이 명료** — 단일 트랜잭션 박스 안에서 모든 책임이 보임 (`02-sequence-diagrams.md § 2.2`)
- **테스트 단순** — `POST /reservations` E2E 한 호출로 *최종 상태* 검증. 2단계면 *중간 상태* 검증이 더 필요

### ⚠️ 부정적 / 비용
- **결제 도입 시 트랜잭션 재설계 필요** — Round 3+ 결제 트랜잭션 안에 *일자별 재고 차감* 을 다시 끼워 넣어야 함. 또는 PENDING 단계 재도입
- **PENDING 의미가 미정의** — enum 에 자리만 있고 *실제 의미* 가 비어 있음. 신규 진입자가 *"PENDING 은 뭐죠?"* 물을 때 *"결제 도입 시 활용"* 이라는 *간접* 답변만 가능
- **호스트 승인 워크플로 도입 시 거대한 변경** — Airbnb 식 24h 승인 대기 패턴 도입 시 *대체* ADR 필요. 단, 한국 OTA 도메인이라 가능성 낮음

### 🔄 후속 영향
- **취소 흐름 단순화** — PENDING 취소 케이스 분기 불필요. CANCELLED 진입은 CONFIRMED/CHECKED_IN 에서만 (상태 머신 가드 단순)
- **AC 시나리오 보장** — `01-requirements.md § 4.3` 의 Gherkin 시나리오 (재고 부족 → 전체 rollback) 가 단일 트랜잭션 전제하에 자연 성립
- **빅테크 패턴 학습 자산** — Airbnb 식 PENDING 24h vs 한국 OTA 식 즉시 확정 의 *왜* 가 명확히 정리됨. 면접에서 그대로 답변 가능

## Alternatives Considered

### 대안 A — PENDING 생성 → (mock confirm) → CONFIRMED 2단계
- 모양: `POST /reservations` 는 PENDING + 일자별 재고 *임시 점유*. 별도 `confirm` 호출 (이번 라운드 mock) 이 CONFIRMED 전이
- 장점: 결제 도입 시 *자연 연결* (confirm 트리거가 PG 콜백)
- 단점:
  - 본 라운드에 mock confirm 이 *허구의 흐름* 으로 추가됨
  - 시나리오 머신과 정확 일치하지만, *실제 의미가 비어 있는* 상태 머신이라 학습 가치 ↓
  - *임시 점유 → 만료* 같은 부가 정책 결정 필요 (시간 제한 등)
- 거절 이유: 본 라운드 단순성 > 미래 자연 연결. 후속 라운드 PENDING 재도입은 *부가 확장* 가능

### 대안 B — 혼합 (이번 라운드 즉시 CONFIRMED, 결제 도입 시 PENDING 추가)
- 모양: 본 ADR 채택안과 동일하나 enum 에 PENDING 자리 *없이*. 결제 도입 시 enum 추가
- 장점: 본 라운드 단순성 + 미래 변경 비용 분산
- 단점:
  - enum 변경이 *brittle change* — 기존 코드의 모든 when 분기 영향
  - PENDING 자리만 남겨두는 게 *미래 의도 표시* 로 가치 ↑
- 거절 이유: enum 에 자리만 두는 비용 0 + 후속 진입자에게 *"여기에 PENDING 이 올 것"* 명시 가치 ↑

### 대안 C — 호스트 승인 24h PENDING 도입 (Airbnb 식)
- 모양: PENDING (24h 호스트 승인 대기) → CONFIRMED 또는 EXPIRED/DECLINED
- 장점: P2P 도메인 (Airbnb 식) 에 자연
- 단점:
  - 우리 시나리오에 호스트 승인 흐름 없음
  - 한국 OTA 도메인 (야놀자/여기어때) 패턴이 아님
- 거절 이유: 시나리오 모순

## Related

- 결정 누적: [`docs/round-2/03-questions.md` Q1](../round-2/03-questions.md)
- 도메인 학습: [`docs/round-2/01-domain-study.md` § 2.6 Reservation](../round-2/01-domain-study.md)
- 요구사항 명세: [`docs/design/01-requirements.md` § 6 Q1](../design/01-requirements.md) + § 7 상태 머신
- 시퀀스 다이어그램: [`docs/design/02-sequence-diagrams.md § 2 예약 생성`](../design/02-sequence-diagrams.md) + § 4 상태 다이어그램
- 빅테크 비교: [`docs/round-2/01-domain-study.md § 4`](../round-2/01-domain-study.md) — Airbnb/Booking/야놀자/여기어때

---

## 면접 답변 템플릿

> "결제가 본 라운드 out-of-scope 라, PENDING 으로 두고 mock confirm 하는 흐름은 *허구의 트랜잭션 분리* 가 된다고 판단해 **즉시 CONFIRMED** 로 갔습니다.
>
> 야놀자·여기어때 같은 한국 OTA 가 *결제 즉시 확정* 패턴이라 시나리오와도 일관됩니다. Airbnb 처럼 호스트 승인이 있으면 PENDING 24h 가 자연이지만, 우리 도메인엔 그 워크플로가 없습니다.
>
> 결제 도입 시점에 PENDING 단계 도입은 *부가 확장* 으로 가능 — 본 ADR 이 *대체* 되는 게 아니라 *확장* 됩니다. enum 에 PENDING 자리만 남겨둔 것도 그 이유입니다."
