plugins {
    id("org.jetbrains.kotlin.plugin.jpa")
}

dependencies {
    // BaseEntity 사용 + Cycle 8 User aggregate @Entity 대비
    implementation(project(":modules:jpa"))

    // BCrypt only (Password VO) — Spring Security 전체 아닌 crypto 모듈만
    implementation("org.springframework.security:spring-security-crypto")

    // querydsl annotation processor (Cycle 8 User aggregate Q-class 대비)
    kapt("com.querydsl:querydsl-apt::jakarta")
}
