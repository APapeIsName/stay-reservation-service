# 패키지·모듈 명명

## Rule
- 패키지 루트: `com.stay.*`
- 앱 모듈: `stay-api`, `stay-batch`, `stay-streamer` (`apps/` 하위)
- 재사용 모듈:
  - `modules/jpa`, `modules/redis`, `modules/kafka` — 도메인 무관 인프라 config
  - `modules/domain` — 도메인 코드(VO, Aggregate, Repository 인터페이스, 도메인 예외) 보관. Round 1 후반 분리
- 지원 모듈: `supports/jackson`, `supports/logging`, `supports/monitoring`
- 식별자 `loopers`, `loop-pack`, `commerce-`, `루프팩`, `루퍼스` 흔적 0 유지

### 도메인 코드 위치 원칙
- 도메인 (VO·Aggregate·Repository 인터페이스·도메인 예외) → **`modules/domain`** 에 배치. apps/ 에 두지 않음
- `CoreException`, `ErrorType` 도 도메인이 throw 하므로 modules/domain 에 위치 (apps 가 의존성으로 접근)
- 도메인 모듈은 spring-web 같은 web 레이어 의존을 피한다 (예: ErrorType 은 `HttpStatus` 대신 `Int statusCode` 보관)

## Why
부트캠프 원본(`com.loopers`, `commerce-*`) 정체성을 완전히 제거하고 숙박 도메인(`com.stay`) 으로 재출범한 프로젝트. 잔재가 묻으면 도메인 일관성·면접 설명력 모두 훼손.

## How to apply
- 신규 클래스는 `com.stay.<layer>.<feature>` 패키지에 둔다 (layer: `domain`, `application`, `infrastructure`, `interfaces.api.v1`)
- 빌드 산출물 제외하고 `grep -rniI -E "loopers|loop-pack|commerce-|루프팩|루퍼스" . --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle --exclude-dir=.idea` 가 항상 0 건이어야 함
- 원본 부트캠프 이력은 로컬 태그 `archive/loopers-original` (미푸시) 에만 보존
- 새 앱 모듈 추가 시 `stay-<feature>` 컨벤션 유지

## References
- 작업 완료: 2026-05-18 orphan 루트 커밋 `222d8aa`
- 메모리: `project-goal-stay-reservation`
