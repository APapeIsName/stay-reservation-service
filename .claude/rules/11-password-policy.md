# 비밀번호 정책

## Rule
- **길이**: 8\~16자
- **Charset**: 영문 대소문자 `A-Z a-z`, 숫자 `0-9`, 그리고 다음 특수문자만 — `! @ # $ % ^ & * ( ) - _ = + [ ] { } ; : ' " , . < > / ? \ |` (공백·백틱 제외)
- **조합 강제 없음**: 위 charset 내라면 어떤 조합이든 허용 (4종 모두 포함 강제 X)
- **생년월일 substring 차단**: 비밀번호 평문에 사용자 생년월일의 `YYYYMMDD` 표현이 부분문자열로 포함되면 거부 (e.g., `1995-03-15` → `"19950315"` substring 검사)
- **저장**: BCrypt 단방향 해시. 평문은 도메인 외부로 노출/저장 절대 금지
- **VO 분리**: `RawPassword`(평문 · 길이/charset 검증만) ↔ `Password`(해시값 보유 · `encrypt(raw, birthDate)` / `matches(raw)`)

## Why
- "사용 가능" 의 spec literal 해석 → 조합 강제는 명시되지 않음 (향후 보안 요구 강화 시 추가 여지)
- charset enumerate 로 "특수문자" 모호성 제거 → 정규식·테스트·문서 일치
- YYYYMMDD 1종 검사: 가장 흔한 표현. YYMMDD/MMDD 까지 확장하면 false-positive(우연 일치) 증가
- BCrypt: Spring Security 표준, salt 자동, 비용 인자 조절 가능 (대안: PBKDF2/Argon2 — 별도 요구 시 갱신)
- 2-VO 분리: BCrypt 호출 없는 검증을 빠른 단위 테스트로 분리 + 책임 분리

## How to apply
- 정규식(charset 검사): `^[A-Za-z0-9!@#$%^&*()\-_=+\[\]{};:'",.<>/?\\|]{8,16}$`
- `RawPassword(value)` 생성자에서 길이·charset 검증
- `Password.encrypt(raw: RawPassword, birthDate: BirthDate)`:
  1. `raw.value` 에 `birthDate.toYyyyMMdd()` substring 포함되면 `throw CoreException(BAD_REQUEST, "...")`
  2. BCrypt 해시 → `Password(hashedValue)` 반환
- `Password.matches(raw: RawPassword): Boolean` — BCrypt.matches
- `Password.ofHashed(hashedValue)` — 영속 복원 전용 (검증 우회). 비밀번호 변경 작업(차주) 에서 활용
- **위반 신호**:
  - 평문 저장 / 도메인 객체에서 평문 그대로 노출
  - `@Pattern` 으로 정책 분산
  - SHA-256 등 빠른 해시 사용
  - charset 정의가 코드 여러 곳에 흩어짐

## References
- 결정: `docs/round-1/01-signup-requirements.md` D-3, D-4, D-8 + 응답 Q3
- 카탈로그: `docs/round-1/02-tdd-plan.md` D-A1, B.2 (RPW-01\~08), B.3 (PW-01\~06)
