# 사용자 컨벤션 우선 (User Convention Priority)

## Rule
부트캠프 골격·일반 통념·이전 결정보다 **사용자가 정의한 컨벤션이 우선**한다. 새 컨벤션 발견 시 영향 범위 보고 → 승인 → 일괄 리네이밍/갱신.

## Why
- 이 프로젝트는 사용자의 학습·면접 자산. 사용자 의도가 최상위 입력
- 부트캠프 답습이 자동으로 옳지 않음 — `apps/` 안의 도메인, `Facade` 명명 등이 사용자 컨벤션과 충돌한 사례
- 일관성은 *일괄 처리* 로 유지된다 — 부분 적용은 더 큰 혼란 야기

## How to apply

### 1. 새 컨벤션 발견 신호

다음 패턴 중 하나가 등장하면 컨벤션 정의로 인식한다:

| 신호 | 예시 |
|---|---|
| 정의형 | "{용어} 는 ... 다", "{용어1} = ... / {용어2} = ..." |
| 선호 변경형 | "{기존 이름} 별로 — {새 이름} 가 더 좋다" |
| 비판적 질의형 | "{기존 위치} 에 {대상} 이 있어?", "왜 거기다 썼어?" |
| 모호함 지적형 | "{어휘} 가 뭐야?", "{표현} 이상하네" |

### 2. 일괄 리네이밍·갱신 절차

```
사용자 컨벤션 인식
  ↓
영향 범위 보고 (파일 N개, sed 패턴, 추정 시간) → 사용자 승인
  ↓
파일 이동 (mv / git mv)
  ↓
sed 일괄 치환 (코드 + docs + rules. questions.md / curriculum 의 역사 보존 필요분 제외)
  ↓
빌드·테스트 회귀 검증 (ktlintCheck + build -x test + 단위테스트)
  ↓
questions.md 신규 Q (컨벤션 정의 + 후보 비교 + 결정 + 면접 답변)
  ↓
관련 docs / rules 갱신 (인덱스, 본문 참조 등)
  ↓
PR / 커밋 메시지에 변경 사유 명시
```

### 3. 일괄 리네이밍 종료 체크리스트

- ✅ *영향 범위* 를 사용자에게 사전 보고했다 (파일 N개, sed 패턴, 추정 시간)
- ✅ 사용자 명시 승인 후 진행했다
- ✅ 컴파일 + ktlint + 단위테스트 회귀 0 을 확인했다
- ✅ 변경에 영향받는 docs 와 rules 가 일관 갱신됐다
- ✅ `questions.md` 에 *컨벤션 정의 + 후보 비교 + 결정 + 면접 답변* 이 기록됐다
- ✅ 보존 가치 있는 *역사적 맥락* (이전 결정의 사유) 은 이전 Q 에 원형 보존됐다
- ✅ PR / 커밋 메시지에 변경 사유가 명시됐다

### 4. 적용 사례 (참고)

| 사례 | 사용자 발화 | 변경 | 기록 |
|---|---|---|---|
| 도메인 위치 | "api 안에 vo 가 있어?" | `apps/stay-api/domain` → `modules/domain` 신규, 17 파일 이동, ErrorType HttpStatus 의존 제거 | `questions.md` Q7 |
| Facade vs Service | "Facade 별로다. Service 가 더 맞다" | `UserFacade` → `UserService`, 3 파일 rename + 모든 docs·rules 갱신 | `questions.md` Q10 |

### 5. 변경 직후 정합성 체크리스트

- ✅ `grep -rnI "{이전 이름}" .` 가 의도된 *역사 보존* 위치 외 0 건이다
- ✅ 빌드 산출물 제외하고 잔존 검색이 깨끗하다
- ✅ rule README.md 의 인덱스가 신규 rule 을 포함한다 (rule 추가 시)
- ✅ CLAUDE.md 의 @-imports 가 신규 rule 을 포함한다 (rule 추가 시)

## References
- 운영 사례: `docs/round-1/03-questions.md` Q7, Q10
- 절차 의존: [`05-code-review-gate.md`](./05-code-review-gate.md), [`15-process-conventions.md`](./15-process-conventions.md)
