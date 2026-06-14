# 회원 필드 정책

## Rule

| 필드 | 정규식 / 규칙 | VO |
|---|---|---|
| `loginId` | `^[A-Za-z0-9]{4,20}$`, **유일성 강제** | `LoginId` |
| `name` | `^[가-힣]{1,10}$` (한글 1\~10자) | `Name` |
| `birthDate` | ISO `yyyy-MM-dd`, 미래 불가, **만 14세 이상** (오늘 외부 주입) | `BirthDate` |
| `email` | RFC 5322 호환 (`@Email` 수준 정규식) | `Email` |
| `phoneNumber` | `^010-\d{4}-\d{4}$` | `PhoneNumber` |
| `password` | [비밀번호 정책](./11-password-policy.md) 참조 | `RawPassword` / `Password` |

## Why
- `loginId` 영문+숫자: spec 명시. 길이 4\~20: 일반적 default
- `name` 한글만: 국내 서비스 일반. 외국인/로마자 수용은 추후 요구사항으로
- `birthDate` 만 14세: 정통망법 개인정보 동의 가능 연령
- `phoneNumber` 010 패턴: 예약 SMS 발송 대상 — 형식 엄수
- 모든 필드 [검증을 도메인 VO 로 일원화](./06-validation-via-domain-vo.md) 에 따라 VO 생성자에서 검증

## How to apply
- VO 생성자에 정규식 매칭 / 길이 검사 / 도메인 로직 (만 14세 등) → 위반 시 `throw CoreException(ErrorType.BAD_REQUEST, "<필드명> 형식이 올바르지 않습니다.")`
- DB 컬럼 길이: `loginId` 20, `name` 10, `email` 254, `phoneNumber` 13 등 정규식 상한 + 약간의 안전 마진
- 이메일·휴대폰 **유일성은 현재 미요구** (loginId 만 유일)
- 새 필드 추가 시 본 표 갱신
- **위반 신호**:
  - 컨트롤러/서비스에서 if-throw 산재 검증
  - 정규식 하드코딩이 여러 곳에 분산
  - 정책 변경 시 여러 곳을 동시 수정해야 함

## References
- 결정: `docs/round-1/01-signup-requirements.md` §2, §3
- 응답: Q1\~Q4 (2026-05-20)
