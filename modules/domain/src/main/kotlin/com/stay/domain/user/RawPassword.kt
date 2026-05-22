package com.stay.domain.user

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType

/**
 * 평문 비밀번호 VO.
 *  - 규칙: 8~16자, 영문 대소문자 + 숫자 + 허용 특수문자만 (조합 강제 없음)
 *  - 위반 시: CoreException(BAD_REQUEST)
 *  - 생년월일 substring 검사·해시화는 Password VO (Cycle 7) 의 책임
 *
 * 허용 특수문자 (공백·백틱 제외):
 *   ! @ # $ % ^ & * ( ) - _ = + [ ] { } ; : ' " , . < > / ? \ |
 *
 * 보안 노트:
 *  - `toString()` 오버라이드로 평문 로그 누출 방지 (data class 기본 toString 은 value 노출)
 *  - `equals`/`hashCode` 는 도메인 평등성을 위해 value 기반 유지 (data class 자동 생성)
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §2
 *  - .claude/rules/11-password-policy.md, 06-validation-via-domain-vo.md
 */
data class RawPassword(val value: String) {
    init {
        if (!PATTERN.matches(value)) {
            throw CoreException(
                ErrorType.BAD_REQUEST,
                "비밀번호 형식이 올바르지 않습니다.",
            )
        }
    }

    override fun toString(): String = "RawPassword(value=****)"

    companion object {
        private val PATTERN: Regex = Regex(
            """^[A-Za-z0-9!@#$%^&*()\-_=+\[\]{};:'",.<>/?\\|]{8,16}$""",
        )
    }
}
