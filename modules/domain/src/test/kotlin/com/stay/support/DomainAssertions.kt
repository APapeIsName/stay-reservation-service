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
