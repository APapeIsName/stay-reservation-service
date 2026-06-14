# 코드 검수 게이트

## Rule
**모든 코드 변경은 사용자의 명시적 승인 후에만 적용한다.** Claude 는 코드를 chat 의 fenced block 으로 **제안**한 뒤, 사용자의 OK 신호를 기다리고, 승인 후에 Write/Edit 를 실행한다. 사이클 단위 묶음 승인 금지.

## Why
TDD/면접 관점 진행에서 코드의 의도·구조를 사용자가 매 단계 검토해야 학습 루프와 포트폴리오 가치가 살아난다. 자동 적용은 사용자의 검토 흐름을 끊는다.

## How to apply

### 게이트 대상
- 소스: `.kt`
- 빌드: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
- 리소스: `application*.yml`, `*.xml`
- 테스트: `*Test.kt`, `*E2ETest.kt`, `*IntegrationTest.kt`

### 절차
1. 코드를 chat 에 fenced block 으로 **제안** (파일 경로 + 전체 내용 또는 명확한 diff)
2. 사용자 명시 OK 대기 (e.g., "ok", "go", "진행", "좋아")
3. 승인 후 Write/Edit 실행
4. 적용 결과를 짧게 보고

### TDD 사이클 적용
- (a) **Red** 테스트 코드 제안 → 승인 → Write
- (b) **Green** 구현 코드 제안 → 승인 → Write
- (c) **Refactor** 변경 제안 → 승인 → Write
- (a)+(b)+(c) 를 한 번에 묶어 받지 말 것

### 예외 (게이트 없음)
- `docs/**` 마크다운
- `.claude/rules/**` 마크다운
- `.claude/projects/.../memory/**`, `MEMORY.md`
- 단, **대량 신규/구조 변경** 은 목록 합의 후 실행

### 위반 신호
- 사용자 승인 없이 소스/빌드/리소스 파일에 Write/Edit 호출
- "한 번에 다 짜놓고 검토받기"

## References
- 사용자 지시: 2026-05-21 ("어떤 코드든 내 검수 무조건 맡고 진행해줘")
- 메모리: `code-review-gate`
