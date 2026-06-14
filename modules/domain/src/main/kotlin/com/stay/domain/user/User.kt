package com.stay.domain.user

import com.stay.domain.BaseEntity
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

/**
 * 회원 도메인 Aggregate Root.
 *
 * 생성 진입점은 정적 팩토리 `User.signUp(...)`. 각 VO 검증과 `Password.encrypt` 의 교차 검증을
 * 도메인 외부 호출 흐름에 노출하지 않고 본 팩토리 안에 응집한다.
 *
 * JPA 매핑:
 *  - @Entity + BaseEntity (id Long IDENTITY, audit ZonedDateTime auto)
 *  - 각 VO 는 @Embedded 인라인 매핑. @AttributeOverride 로 컬럼명 명시 (모든 VO 가 'value'/'hashedValue' 라 충돌 회피)
 *  - 테이블명 'users' (DB 예약어 'user' 회피)
 *
 * Refs:
 *  - docs/round-1/01-signup-requirements.md §1
 *  - .claude/rules/07-domain-jpa-integration.md, 08-static-factory-and-clock-injection.md, 12-user-field-policy.md
 */
@Entity
@Table(name = "users")
class User private constructor(
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "login_id", nullable = false, unique = true, length = 20))
    val loginId: LoginId,
    @Embedded
    @AttributeOverride(name = "hashedValue", column = Column(name = "password_hash", nullable = false, length = 100))
    val password: Password,
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = 10))
    val name: Name,
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "birth_date", nullable = false))
    val birthDate: BirthDate,
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "email", nullable = false, length = 254))
    val email: Email,
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "phone_number", nullable = false, length = 13))
    val phoneNumber: PhoneNumber,
) : BaseEntity() {

    companion object {
        fun signUp(
            loginId: String,
            rawPassword: String,
            name: String,
            birthDate: String,
            email: String,
            phoneNumber: String,
            today: LocalDate,
        ): User {
            // 각 VO 생성자/Password.encrypt 가 검증 책임. 위반 시 CoreException(BAD_REQUEST) 전파
            val birthDateVo = BirthDate(birthDate, today)
            val rawPasswordVo = RawPassword(rawPassword)
            val passwordVo = Password.encrypt(rawPasswordVo, birthDateVo)
            return User(
                loginId = LoginId(loginId),
                password = passwordVo,
                name = Name(name),
                birthDate = birthDateVo,
                email = Email(email),
                phoneNumber = PhoneNumber(phoneNumber),
            )
        }
    }
}
