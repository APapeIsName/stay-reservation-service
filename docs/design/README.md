# `docs/design/` — 설계 문서 (단일 폴더, 증강형)

> **빅테크 Design Doc 패턴 정렬** — Google·Stripe·Meta 의 *"한 문서에 HLD/LLD 통합"* + arc42 의 *계층적 분해* + C4 model 의 *zoom level* 접근을 절충.
> HLD/LLD 라벨링은 *지역 의존 어휘이지만 면접 자산으로 유용* 하다는 판단 (`docs/round-2/05-hld-lld-research.md`).

---

## 운영 방침

본 폴더는 **라운드별 분리 없이 단일 폴더에서 점차 증강** 되는 살아있는 설계 문서다.

- 라운드별 별도 디렉터리 ❌
- 새 도메인·새 흐름이 추가되면 기존 5 파일에 *섹션 추가* 또는 *다이어그램 추가*
- 매주차 신규 작성 ❌, 필요한 시점에만 증강
- 변경 시 각 파일의 `## 변경 이력` 섹션에 *날짜 + 변경 사유* 1줄 추가

---

## 파일 구성 — HLD / LLD 라벨링

| 파일 | 내용 | HLD/LLD | C4 zoom | arc42 | 첫 작성 |
|---|---|---|---|---|---|
| **`00-overview.md`** | 시스템 컨텍스트 + 컴포넌트 토폴로지 + NFR + 트레이드오프 종합 + ADR 인덱스 | **HLD 본체** | **L1 + L2** | § 3 + § 5(상위) + § 7 + § 10 | Round 2 (2026-05-29) |
| `01-requirements.md` | 유저 시나리오 + 기능 정의 + 요구사항 명세 + 정책 결정 + AC | HLD 이전 (입력) | — | § 1 + § 10 | Round 2 |
| `02-sequence-diagrams.md` | Runtime View — 시퀀스 + Reservation 상태 머신 (Stripe 패턴) | HLD↔LLD 경계 | L3\~L4 | § 6 Runtime View | Round 2 |
| `03-class-diagram.md` | 도메인 객체 클래스 다이어그램 (책임·의존 방향) | **LLD** | L4 Code | § 5 (하위) | Round 2 |
| `04-erd.md` | 테이블 구조·관계·인덱스·복합키 (영속성 구조) | **LLD** | — | § 5 + § 8 (Persistence) | Round 2 |

> *HLD/LLD 는 인도 SI 산업과 폭포수 SDLC 에서 형식화된 어휘이지만, 면접 어휘로 *간결·명확* 해 본 프로젝트도 *라벨링* 만 도입. 우리는 빅테크식으로 **한 폴더 안에 5 파일** 로 통합 운영하며, Simon Brown 의 C4 model 처럼 *추상화 수준 일관성* 을 더 중요한 규율로 본다.*

---

## 작성·증강 체크리스트

- ✅ 새 도메인 등장 시 적절한 파일에 *추가 섹션* 으로 누적 (덮어쓰기 X)
- ✅ 다이어그램 추가 시 *왜 이 다이어그램이 필요한지* + *읽는 포인트* 함께 명시 (Skill 5️⃣ 6️⃣ + arc42 § 6 구조)
- ✅ 추상화 수준 일관 — 한 다이어그램에 시스템·서비스·클래스 섞이지 말 것 (C4 규율)
- ✅ 결정·트레이드오프
  - *진행 중* 누적 → `docs/round-N/03-questions.md`
  - *영구 기록 가치* (모듈 경계·영속 구조·트랜잭션 모델 등) → `docs/adr/` 로 승격
- ✅ 외부 PR 제출 시 본 폴더의 5 파일이 PR 본문에서 직접 참조 가능

---

## ADR 인덱스

`docs/adr/` 디렉터리 — 결정 시점의 *영구 스냅샷*. 본 디자인 문서가 *현재 상태* 라면 ADR 은 *왜 그렇게 됐는가의 시점 기록*. 보완재.

| ID | 제목 | Status | 라운드 |
|---|---|---|---|
| [ADR-001](../adr/ADR-001-modules-domain-separation.md) | modules/domain 분리 | Accepted | Round 1 |
| [ADR-002](../adr/ADR-002-immediate-confirmed-reservation.md) | POST /reservations 즉시 CONFIRMED | Accepted | Round 2 |
| [ADR-003](../adr/ADR-003-single-daily-room-table.md) | 일자별 재고·요금 한 테이블 `daily_room` | Accepted | Round 2 |

ADR 운영 정책 + 형식 → [`docs/adr/README.md`](../adr/README.md).

---

## 관련 도구·규칙

### 학습 도구
- 분석 Skill: [`.claude/skills/requirements-analysis/SKILL.md`](../../.claude/skills/requirements-analysis/SKILL.md) — Skill 5️⃣ 6️⃣ 7️⃣ 다이어그램 가이드
- 시나리오 원본: `docs/curriculum/round-N-scenario.md` (frozen)
- 진행 흐름: [`rule 15 process-conventions`](../../.claude/rules/15-process-conventions.md)
- 컨벤션 변경 대응: [`rule 16 user-convention-priority`](../../.claude/rules/16-user-convention-priority.md)

### 라운드 진행 메모 (`docs/round-N/`)
- `01-domain-study.md` — 도메인 학습 노트
- `02-bigtech-*-research.md` — 빅테크 관행 리서치 (요구사항·시퀀스·HLD/LLD)
- `03-questions.md` — 진행 중 의사결정 누적

### 빅테크 리서치 결과 (Round 2 적용)
| 리서치 | 적용 결과 |
|---|---|
| 요구사항 정리 관행 | `01-requirements.md` 의 Status·Lifecycle 라벨 + AC (Gherkin) + 학습 메트릭 |
| 시퀀스 다이어그램 관행 | `02-sequence-diagrams.md` 의 "Runtime View" 명명 + Reservation 상태 다이어그램 (Stripe 패턴) + 외부 시스템 컨벤션 |
| HLD/LLD 관행 | `00-overview.md` HLD 본체 신설 + `docs/adr/` ADR 디렉터리 + 본 README 의 HLD/LLD 라벨링 |

---

## 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| (Round 2 첫 작성) | 단일 폴더 증강형 운영 방침 + 4 파일 구성 | Round 2 진입 시 |
| 2026-05-29 | `00-overview.md` 신규 (HLD 본체) 추가 → 4파일 → 5파일 / HLD/LLD/C4/arc42 매핑 표 도입 / ADR 인덱스 섹션 신설 / 빅테크 리서치 결과 요약 표 추가 | 빅테크 HLD/LLD 리서치 결과 적용 (출처: `docs/round-2/05-hld-lld-research.md`) |
