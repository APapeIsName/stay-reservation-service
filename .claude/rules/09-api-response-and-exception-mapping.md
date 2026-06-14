# API 응답 Envelope + 예외 매핑

## Rule
- 모든 API 응답은 보존된 `ApiResponse<T>` envelope 으로 반환한다 — `{ meta: { result, errorCode, message }, data: T }`
- 도메인 예외 `CoreException(ErrorType, ...)` 는 `ApiControllerAdvice` 가 일관되게 HTTP status + envelope 로 매핑한다
- 컨트롤러에서 `ResponseEntity<...>` 를 직접 조립하지 않는다

## Why
- 클라이언트가 응답 구조를 일관 가정 가능 → 오류 처리 분기 단순화
- 도메인 ↔ HTTP 변환을 advice 한 곳에 응집 → 컨트롤러는 비즈니스 흐름만 책임
- 보존된 골격을 최대 활용 (재발명 회피)

## How to apply
- 성공 응답: `return ApiResponse.success(dto)` 또는 `ApiResponse.success()`
- 실패는 throw — `throw CoreException(ErrorType.BAD_REQUEST, "...")` / `ErrorType.CONFLICT` / `ErrorType.NOT_FOUND` 등
- 새 에러 코드는 `ErrorType` enum 에 추가 (status, code, message)
- Controller 메서드 반환 타입: `ApiResponse<XxxDto.Response>` 권장
- DB 제약 위반 (`DataIntegrityViolationException`) 도 advice 에서 의미 있는 에러 코드로 매핑 (예: unique → `CONFLICT`) — 필요 시 핸들러 추가
- **위반 신호**:
  - 컨트롤러에서 `ResponseEntity.status(...).body(...)` 직접 조립
  - try-catch 산재
  - 임의 응답 포맷
  - `RuntimeException` 등 비도메인 예외 직접 throw

## References
- 보존 골격: `apps/stay-api/src/main/kotlin/com/stay/interfaces/api/{ApiResponse,ApiControllerAdvice}.kt`, `support/error/{ErrorType,CoreException}.kt`
- 결정: `docs/round-1/01-signup-requirements.md` R-4
