package com.stay.application.user

/**
 * 회원가입 입력 DTO.
 * Controller 의 Request DTO 를 Service 진입점 용으로 변환한 형태.
 *
 * 도메인 VO 가 아닌 raw String 으로 보관 — 검증은 Service 가 호출하는 User.signUp 의
 * VO 생성자에서 수행 (rule 06 — 검증을 도메인 VO 로 일원화).
 */
data class SignupCommand(
    val loginId: String,
    val rawPassword: String,
    val name: String,
    val birthDate: String,
    val email: String,
    val phoneNumber: String,
)
