# `.claude/rules/` — 프로젝트 결정 규칙

이 디렉터리는 라운드를 거치며 확정된 **설계·정책·진행 방침**을 개별 파일(rule per file)로 보관합니다. 각 rule 은 프로젝트 루트 `CLAUDE.md` 에서 `@-import` 되어 매 세션 자동 로드됩니다.

## 파일 형식

```markdown
# <Rule 제목>

## Rule
<한 줄 규칙 진술>

## Why
<채택 이유·트레이드오프>

## How to apply
<구현/검토 시 따르는 절차>

### 체크리스트 (✅ 위주의 긍정 검증 장치)
- ✅ ...
- ✅ ...

## References
<출처 문서/결정 ID>
```

## 인덱스

| # | 파일 | 한 줄 |
|---|---|---|
| 01 | `01-package-and-modules.md` | 패키지·모듈 명명 (`com.stay`, `stay-*`) |
| 02 | `02-curriculum-docs-frozen.md` | `docs/curriculum/*.md` 동결 |
| 03 | `03-domain-substitution-from-curriculum.md` | 발제 e-commerce → 숙박 치환 |
| 04 | `04-interview-oriented-output.md` | 의도·대안·트레이드오프 명시 |
| 05 | `05-code-review-gate.md` | 코드 변경 시 사용자 승인 필수 |
| 06 | `06-validation-via-domain-vo.md` | 검증을 도메인 VO 로 일원화 |
| 07 | `07-domain-jpa-integration.md` | Aggregate 에 `@Entity` 통합 |
| 08 | `08-static-factory-and-clock-injection.md` | 정적 팩토리 + 시간 의존성 외부 주입 |
| 09 | `09-api-response-and-exception-mapping.md` | `ApiResponse` envelope + advice 매핑 |
| 10 | `10-uniqueness-app-and-db.md` | 유일성: 어플 + DB 둘 다 |
| 11 | `11-password-policy.md` | 비밀번호 정책 (charset · BCrypt · YYYYMMDD) |
| 12 | `12-user-field-policy.md` | 회원 필드 정규식 모음 |
| 13 | `13-signup-response-no-masking.md` | 가입 응답 마스킹 미적용 |
| 14 | `14-test-strategy-tdd.md` | 테스트 전략 + TDD Red→Green→Refactor + 환경 이슈 분류 |
| 15 | `15-process-conventions.md` | 진행 컨벤션 (승인 신호·사이클·questions.md·deferred) |
| 16 | `16-user-convention-priority.md` | 사용자 컨벤션 우선 (일괄 리네이밍 절차) |
| 17 | `17-test-categorization.md` | 테스트 4단계 분류 (L1\~L4 + `@Tag`) |
| 18 | `18-domain-modeling.md` | Entity/VO/Domain Service 분류 + 규칙 캡슐화 + Aggregate 경계 |
| 19 | `19-layered-architecture-dip.md` | 4 레이어 책임 + DIP (port=domain, adapter=infra) |
| 20 | `20-package-and-dto-strategy.md` | `com.stay.<layer>.<domain>` 패키지 + DTO 3계층 분리 |

## 갱신 규칙
- 새 결정 → 해당 파일 갱신 또는 신규 파일 + `CLAUDE.md` `@-import` 1줄 추가
- 규칙 충돌 발견 시 즉시 보고 후 갱신 우선
- 메모리(`~/.claude/.../memory/MEMORY.md`) 는 세션 컨텍스트 회복용 짧은 hook; rules 는 프로젝트 영구 규칙으로 분담
