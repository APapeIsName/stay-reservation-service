# 진행 컨벤션 (Process Conventions)

## Rule
모든 사이클·결정·코멘트가 일관된 패턴을 따른다 — 사용자 승인 신호의 어휘, TDD 사이클 단계, `questions.md` 누적 형식, deferred 처리 방식.

## Why
- 동일 어휘·동일 절차 → 새 세션·새 라운드에서도 *자동 정렬*
- 에이전트 자동화의 기반 (입력 신호 ↔ 동작 매핑 명확)
- 사용자가 한 단어로 다음 단계 진입 가능 ("응" / "다음")

## How to apply

### 1. 사용자 승인 신호 어휘

| 사용자 입력 | Claude 동작 |
|---|---|
| `응` / `응 진행해줘` / `진행` / `좋아` / `ok` | 직전 chat 제안의 Write/Edit/Bash 실행 |
| `다음` / `다음으로 넘어가자` | 다음 사이클·단계 진입 (직전 단계 완료 가정) |
| `보여줄래?` / `한번 보자` | 해당 파일/부분 Read 후 표시 (리뷰 모드) |
| `(질문 형식)` | answer chat + 마무리 시 `questions.md` Q 후보 |
| `(컨벤션 정의)` / `(컨벤션 비판)` | 신규 컨벤션 인식 → [`16-user-convention-priority`](./16-user-convention-priority.md) 절차 |

### 2. 코드 변경 진행 체크리스트

모든 Write/Edit 직전 다음을 통과한다:

- ✅ 직전 chat 에 코드를 fenced block 으로 *제안* 했다
- ✅ 사용자의 명시 승인 신호를 받았다 (위 표 매핑 확인)
- ✅ 묶음 진행이 명시되지 않은 한 (예: "한 번에 묶어서") 각 단계별 독립 승인을 받았다
- ✅ 실행 후 결과를 짧게 보고할 준비가 됐다

### 3. TDD 사이클 단계

```
Red 제안 → 승인 → Write 테스트 → 컴파일 실패 확인 (Red 확정)
  ↓
Green 제안 → 승인 → Write 구현 → 테스트 + ktlint 통과 (Green 확정)
  ↓
Refactor 평가 → (있으면 제안 → 승인 → Edit) → 회귀 통과 확인
  ↓
Cycle 완료 → 다음 Cycle 안내
```

각 Cycle 종료 시 체크리스트:
- ✅ Red 테스트가 카탈로그 ID `@DisplayName("XXX-NN: ...")` 형식으로 작성됐다
- ✅ Green 구현은 *해당 테스트 통과에 필요한 최소 범위*
- ✅ Refactor 단계는 회귀 0 확인 후 종료됐다
- ✅ Red·Green·Refactor 각 단계가 독립 승인을 거쳤다

### 4. `questions.md` 항목 형식

```markdown
## Q{N}. ({YYYY-MM-DD}) {제목}

**질의자**: 사용자  또는  **제안자**: Claude

### 맥락
{왜 이 질문이 나왔는가}

### 답 또는 결정
{핵심 답}

### {필요 시: 후보 비교 표 / 트레이드오프 표 / N축 분석}

### 면접 답변 템플릿
> "{한 문단 답변 — 면접에서 인용 가능}"

### 출처
- {결정 근거 / 관련 rule / 관련 코드}
```

`questions.md` 갱신 체크리스트:
- ✅ 질의자/제안자 명시됐다
- ✅ 면접 답변 템플릿이 한 문단 안에서 답변 가능한 형태로 포함됐다
- ✅ 후속 질문 안내 라인 (`## Q{N+1}. (날짜) ...`) 이 갱신됐다
- ✅ 역사 보존 — 이전 Q 는 후속 결정 변경에도 그대로 두고, 새 Q 로 갱신 기록한다 (예: Q8 ↔ Q10)

### 5. Deferred 처리

체크리스트:
- ✅ 즉시 결정 못 하는 항목은 `questions.md` 에 *재검토 트리거* 가 명시된 채 기록됐다
- ✅ 트리거는 *구체적 사건* 으로 표현됐다 (예: "Kotlin best practice 문헌 확인 후", "동일 패턴 3개 이상 발견 시")
- ✅ 트리거가 충족되면 해당 deferred Q 를 재방문한다

## References
- 검수 게이트 출처: [`05-code-review-gate.md`](./05-code-review-gate.md)
- 운영 사례: `docs/round-1/03-questions.md` Q9 (deferred), Q10 (컨벤션 변경)
- 보완 rule: [`16-user-convention-priority.md`](./16-user-convention-priority.md), [`17-test-categorization.md`](./17-test-categorization.md)
