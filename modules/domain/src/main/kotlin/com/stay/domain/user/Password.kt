package com.stay.domain.user

import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import jakarta.persistence.Embeddable
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * 비밀번호 자격 증명 VO (해시값 보유).
 *
 * 생성 진입점:
 *  - `encrypt(raw, birthDate)`: 평문 검증 후 BCrypt 해시로 변환
 *  - `ofHashed(hashedValue)`: 영속 복원 — 검증 우회 (DB 에서 읽어온 해시 그대로)
 *
 * 사용:
 *  - `matches(raw)`: 평문이 이 해시와 일치하는지 BCrypt 매칭
 *  - 같은 raw 라도 매 encrypt 결과 hashedValue 다름 (salt 무작위)
 *
 * 보안 노트:
 *  - `private constructor` — 외부는 정적 팩토리만 사용 (검증 우회 차단)
 *  - `equals`/`hashCode` 는 hashedValue 기반
 *  - `toString` 마스킹 — 해시도 자격 증명이므로 로그 노출 차단
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §2, R-3
 *  - .claude/rules/11-password-policy.md, 08-static-factory-and-clock-injection.md
 */
@Embeddable
class Password private constructor(val hashedValue: String) {

    fun matches(raw: RawPassword): Boolean = ENCODER.matches(raw.value, hashedValue)

    override fun equals(other: Any?): Boolean =
        other is Password && other.hashedValue == hashedValue

    override fun hashCode(): Int = hashedValue.hashCode()

    override fun toString(): String = "Password(hashedValue=****)"

    companion object {
        private val ENCODER: BCryptPasswordEncoder = BCryptPasswordEncoder()

        fun encrypt(raw: RawPassword, birthDate: BirthDate): Password {
            if (raw.value.contains(birthDate.toYyyyMMdd())) {
                throw CoreException(
                    ErrorType.BAD_REQUEST,
                    "비밀번호에 생년월일을 포함할 수 없습니다.",
                )
            }
            return Password(ENCODER.encode(raw.value))
        }

        fun ofHashed(hashedValue: String): Password = Password(hashedValue)
    }
}
