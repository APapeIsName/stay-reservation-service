# 숙박 예매 서비스 — Claude Code 작업 규칙

> **프로젝트**: loop-pack 부트캠프 패턴 기반 숙박 예매 서비스 (Kotlin + Spring Boot 멀티모듈). 이력서·면접 자산 중심으로 진행.
>
> **진행 방식**: TDD Red → Green → Refactor 사이클. 각 코드 변경마다 사용자 검수 게이트 — "응" / "응 진행해줘" / "다음" 같은 명시 승인 후 Write. 자세한 어휘 사전과 단계는 [`15-process-conventions.md`](./.claude/rules/15-process-conventions.md).
>
> **의사결정 누적**: 모든 트레이드오프·결정은 `docs/round-N/03-questions.md` 에 Q 로 누적 (질의자/제안자 명시).

본 프로젝트의 모든 설계·정책·진행 방침은 아래 rule 파일에 명문화되어 있다.
코드 작업/리뷰 시 규칙과 충돌하는 상황이 발견되면 즉시 보고하고 rule 갱신을 우선한다.

> 작업 흐름은 항상 **사용자 검수 게이트** ([`05-code-review-gate.md`](./.claude/rules/05-code-review-gate.md)) 를 따른다 — 코드 변경 전 반드시 chat 으로 제안, 사용자 OK 후 적용.

## Rules

@.claude/rules/README.md
@.claude/rules/01-package-and-modules.md
@.claude/rules/02-curriculum-docs-frozen.md
@.claude/rules/03-domain-substitution-from-curriculum.md
@.claude/rules/04-interview-oriented-output.md
@.claude/rules/05-code-review-gate.md
@.claude/rules/06-validation-via-domain-vo.md
@.claude/rules/07-domain-jpa-integration.md
@.claude/rules/08-static-factory-and-clock-injection.md
@.claude/rules/09-api-response-and-exception-mapping.md
@.claude/rules/10-uniqueness-app-and-db.md
@.claude/rules/11-password-policy.md
@.claude/rules/12-user-field-policy.md
@.claude/rules/13-signup-response-no-masking.md
@.claude/rules/14-test-strategy-tdd.md
@.claude/rules/15-process-conventions.md
@.claude/rules/16-user-convention-priority.md
@.claude/rules/17-test-categorization.md

## Round 작업 문서

- `docs/curriculum/round-N.md` — 발제 원문 (동결, 수정 금지)
- `docs/round-N/01-signup-requirements.md` 등 — 라운드별 작업 문서
- `docs/round-N/03-questions.md` — Q&A 누적 (트레이드오프·결정·면접 답변 템플릿)
- 현재: Round 1 (회원가입) 완료, PR #1 검토 대기

## 메모리

세션 컨텍스트 회복용 짧은 hook 은 `~/.claude/projects/.../memory/MEMORY.md` 에 있다.
