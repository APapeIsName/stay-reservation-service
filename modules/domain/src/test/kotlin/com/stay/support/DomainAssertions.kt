package com.stay.support

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows

/**
 * 도메인 VO 생성 시점의 검증 실패(`BAD_REQUEST`) 를 단언하는 공용 헬퍼.
 *
 * 사용:
 * ```
 * assertBadRequest { LoginId("abc") }
 * ```
 *
 * Refs: .claude/rules/06-validation-via-domain-vo.md
 */
fun assertBadRequest(block: () -> Any?) {
    val thrown = assertThrows<CoreException> { block() }
    assertThat(thrown.errorType).isEqualTo(ErrorType.BAD_REQUEST)
}

/**
 * 도메인 상태 충돌(`CONFLICT`) 을 단언하는 공용 헬퍼.
 * 반환된 예외로 메시지 추가 단언 가능 (가드 순서 구분 등).
 *
 * 사용:
 * ```
 * val thrown = assertConflict { dailyRoom.consumeOne() }
 * assertThat(thrown.customMessage).isEqualTo("재고가 부족합니다.")
 * ```
 */
fun assertConflict(block: () -> Any?): CoreException {
    val thrown = assertThrows<CoreException> { block() }
    assertThat(thrown.errorType).isEqualTo(ErrorType.CONFLICT)
    return thrown
}
