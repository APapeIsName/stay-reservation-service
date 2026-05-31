# `docs/adr/` — Architecture Decision Records

> **ADR (Architecture Decision Record)** — Michael Nygard (2011) 가 정립한 *결정 시점의 스냅샷* 기록 패턴.
> 본 프로젝트는 *Round 별 의사결정 누적은 `docs/round-N/03-questions.md`* 에 두고, *영구 기록 가치가 있는 결정* 만 본 디렉터리에 ADR 로 승격한다.

---

## 1. ADR 란 무엇인가

> *"Architecture decisions are decisions that have significant impact on the architecture of a system. They are difficult to change because the cost of reversing them is high. They are often made early in the project."* — Michael Nygard

### 핵심 특징

- **시점 스냅샷** — 결정 당시의 *맥락·근거* 를 그 시점 그대로 기록. 후속 변경은 *새 ADR* 로 (기존 ADR 의 status 를 Superseded 로)
- **immutable** — 한번 Accepted 된 ADR 은 *수정하지 않음*. 잘못된 부분은 새 ADR 이 *대체*
- **하나의 결정 = 하나의 ADR** — 여러 결정을 한 파일에 묶지 않음
- **1\~2 페이지** — 짧고 핵심만

---

## 2. ADR 와 `docs/round-N/03-questions.md` 의 관계

| 항목 | `questions.md` | ADR |
|---|---|---|
| 성격 | *진행 중 의사결정 누적* | *영구 기록 (시점 스냅샷)* |
| 시점 | 매 Round 진행 중 | 결정이 *영구적 영향* 임이 명확해진 시점 |
| 수정 | 갱신·재논의 가능 (Q9 deferred 등) | immutable. 변경은 *새 ADR* 로 |
| 청중 | 본 라운드 진행자 | 후임자 + 면접관 + 6개월 뒤 본인 |
| 형식 | 자유 (질의자/제안자 표기 + 트레이드오프 + 면접 답변 템플릿) | Michael Nygard 표준 5 섹션 |

**승격 규칙** — `questions.md` 의 결정 중 다음 조건을 모두 만족하면 ADR 로 승격:

1. *되돌리기 비용이 높은* 아키텍처 영향 (모듈 경계 / 영속 구조 / 트랜잭션 모델 등)
2. *후속 라운드에 영향을 주는* 결정 (단일 라운드 일회성이 아님)
3. *6개월 뒤에도 "왜 그렇게 했는가" 답이 필요한* 결정

→ 일반적인 *코드 스타일 / 명명 / 단순 trade-off* 는 ADR 으로 승격하지 않음.

---

## 3. ADR 형식 (Michael Nygard 표준)

```markdown
# ADR-NNN — <결정의 한 줄 제목>

**Status**: Proposed | Accepted | Deprecated | Superseded by ADR-XXX
**Date**: YYYY-MM-DD
**Round**: Round N

## Context
이 결정이 *왜* 필요했는가. 당시의 상황·제약·딜레마.
"우리는 X 라는 상황에 있었고, Y 라는 결정을 해야 했다."

## Decision
*우리가 무엇을 결정했는가*. 명확한 단언문.
"우리는 X 를 한다."

## Consequences
이 결정의 *결과* — 좋은 것 / 나쁜 것 / 중립.
✅ 긍정적 결과
⚠️ 부정적 결과 / 비용
🔄 후속에 미치는 영향

## Alternatives Considered
*다른 대안* 과 *왜 그것을 안 골랐는가*.
- 대안 A — 이유로 거절
- 대안 B — 이유로 거절

## Related
- 관련 ADR
- 관련 questions.md Q번호
- 관련 rule, 관련 design 파일
```

### Status 의미

- **Proposed** — 작성됨, 아직 합의 안 됨
- **Accepted** — 합의 완료, 시행 중
- **Deprecated** — 더 이상 권장하지 않음 (단, 대체 없음)
- **Superseded by ADR-XXX** — 새 ADR 이 대체. 본 ADR 은 *역사 보존* 목적

---

## 4. ADR 인덱스

| ID | 제목 | Status | 라운드 | 날짜 | 영향 영역 |
|---|---|---|---|---|---|
| [ADR-001](./ADR-001-modules-domain-separation.md) | modules/domain 분리 | Accepted | Round 1 | 2026-05-22 | 모듈 경계 / 아키텍처 |
| [ADR-002](./ADR-002-immediate-confirmed-reservation.md) | POST /reservations 즉시 CONFIRMED | Accepted | Round 2 | 2026-05-28 | 도메인 모델 / 상태 머신 |
| [ADR-003](./ADR-003-single-daily-room-table.md) | 일자별 재고·요금 한 테이블 `daily_room` | Accepted | Round 2 | 2026-05-28 | 영속 구조 / 동시성 영역 |

---

## 5. 새 ADR 작성 절차

1. `docs/round-N/03-questions.md` 의 결정 중 *§ 2 승격 규칙* 3 조건을 모두 만족하는지 확인
2. 다음 번호 (ADR-NNN) 부여
3. `ADR-NNN-kebab-case-제목.md` 작성 — § 3 형식
4. 본 README 의 *§ 4 인덱스 표* 에 row 추가
5. 관련 design 문서·rule·questions.md 에 *역참조 링크* 추가
6. 기존 ADR 을 대체하는 경우 — 기존 ADR 의 Status 를 `Superseded by ADR-NNN` 으로 갱신

---

## 6. 관련 자료

- [Michael Nygard 원문 (2011) — Documenting Architecture Decisions](https://www.cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- [adr.github.io — ADR 공식 사이트](https://adr.github.io/)
- [Joel Parker Henderson — ADR GitHub 컬렉션](https://github.com/joelparkerhenderson/architecture-decision-record)
- [CNCF Korea — ADR 가이드 (한국어)](https://www.cncf.co.kr/blog/adr-guide/)
- 본 프로젝트 빅테크 리서치: `docs/round-2/05-hld-lld-research.md` § 8

---

## 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-05-29 | 첫 작성 + ADR-001/002/003 신설 | 빅테크 HLD/LLD 리서치 결과 — *결정 (ADR) + 트레이드오프 산문* 의 가치 상승 (출처: `docs/round-2/05-hld-lld-research.md`) |
