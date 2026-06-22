package com.stay.application.coupon

import com.stay.application.support.FakeCouponIssueRepository
import com.stay.application.support.FakeCouponRepository
import com.stay.domain.coupon.Coupon
import com.stay.domain.coupon.CouponIssue
import com.stay.domain.coupon.CouponType
import com.stay.support.error.CoreException
import com.stay.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

/**
 * 어드민 쿠폰 CRUD + 발급내역 유스케이스 테스트.
 * 규칙: register=생성+save / update=findById(NOT_FOUND)→coupon.update→save /
 *   delete=deleteById / list=findAll 페이지네이션 / get=findById(NOT_FOUND) /
 *   listIssues=findByCouponId 필터+페이지네이션.
 * Fake: FakeCouponRepository.save 가 순차 id 키 부여 (도메인 id 0L), findAll/deleteById 보조.
 */
@Tag("slow-unit")
class CouponAdminServiceTest {

    companion object {
        private val EXPIRED_AT: LocalDateTime = LocalDateTime.parse("2026-12-31T23:59:00")

        private fun command(
            name: String = "여름 5천원",
            type: CouponType = CouponType.FIXED,
            value: Long = 5000L,
            minOrderAmount: Long? = null,
            expiredAt: LocalDateTime = EXPIRED_AT,
        ) = CouponTemplateCommand(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )

        private fun coupon(
            name: String = "여름 5천원",
            type: CouponType = CouponType.FIXED,
            value: Long = 5000L,
            minOrderAmount: Long? = null,
            expiredAt: LocalDateTime = EXPIRED_AT,
        ) = Coupon(name = name, type = type, value = value, minOrderAmount = minOrderAmount, expiredAt = expiredAt)
    }

    private fun service(
        couponRepository: FakeCouponRepository = FakeCouponRepository(),
        couponIssueRepository: FakeCouponIssueRepository = FakeCouponIssueRepository(),
    ) = CouponAdminService(couponRepository, couponIssueRepository)

    @Nested
    @DisplayName("등록 — register")
    inner class Register {

        @DisplayName("ADM-01: 템플릿 등록 → 저장 + CouponDetailInfo 반환")
        @Test
        fun registers() {
            val couponRepo = FakeCouponRepository()

            val info = service(couponRepo).register(command(name = "여름 5천원", value = 5000L))

            assertThat(info.name).isEqualTo("여름 5천원")
            assertThat(info.type).isEqualTo("FIXED")
            assertThat(info.value).isEqualTo(5000L)
            assertThat(couponRepo.findAll(0, 10)).hasSize(1)
        }
    }

    @Nested
    @DisplayName("목록 — list (페이지네이션)")
    inner class ListTemplates {

        @DisplayName("ADM-02: 5건 size=2 → page0 2 / page1 2 / page2 1 / page3 0")
        @Test
        fun paginates() {
            val couponRepo = FakeCouponRepository()
            val sut = service(couponRepo)
            repeat(5) { sut.register(command(name = "C$it")) }

            assertThat(sut.list(page = 0, size = 2)).hasSize(2)
            assertThat(sut.list(page = 1, size = 2)).hasSize(2)
            assertThat(sut.list(page = 2, size = 2)).hasSize(1)
            assertThat(sut.list(page = 3, size = 2)).isEmpty()
        }
    }

    @Nested
    @DisplayName("단건 조회 — get")
    inner class Get {

        @DisplayName("ADM-03: 미존재 couponId → NOT_FOUND")
        @Test
        fun rejectsUnknown() {
            val thrown = assertThrows<CoreException> {
                service().get(couponId = 999L)
            }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("ADM-04: 존재하는 couponId → CouponDetailInfo 반환")
        @Test
        fun returnsDetail() {
            // 도메인 id 는 Fake 에서 0L 유지(실제 id 부여는 JPA/L3 책임) — 조회 성공·필드 매핑만 검증
            val couponRepo = FakeCouponRepository().apply {
                seed(10L, coupon(name = "겨울 1만원", type = CouponType.RATE, value = 15L))
            }

            val info = service(couponRepo).get(couponId = 10L)

            assertThat(info.name).isEqualTo("겨울 1만원")
            assertThat(info.type).isEqualTo("RATE")
            assertThat(info.value).isEqualTo(15L)
        }
    }

    @Nested
    @DisplayName("수정 — update")
    inner class Update {

        @DisplayName("ADM-05: 존재하는 템플릿 수정 → 필드 갱신 + Info 반영")
        @Test
        fun updatesFields() {
            val couponRepo = FakeCouponRepository().apply { seed(10L, coupon(name = "기존", value = 5000L)) }

            val info = service(couponRepo).update(
                couponId = 10L,
                command = command(name = "변경", type = CouponType.RATE, value = 15L, minOrderAmount = 30000L),
            )

            assertThat(info.name).isEqualTo("변경")
            assertThat(info.type).isEqualTo("RATE")
            assertThat(info.value).isEqualTo(15L)
            assertThat(info.minOrderAmount).isEqualTo(30000L)
            assertThat(couponRepo.findById(10L)!!.name).isEqualTo("변경")
        }

        @DisplayName("ADM-06: 미존재 템플릿 수정 → NOT_FOUND")
        @Test
        fun rejectsUnknown() {
            val thrown = assertThrows<CoreException> {
                service().update(couponId = 999L, command = command())
            }
            assertThat(thrown.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("삭제 — delete")
    inner class Delete {

        @DisplayName("ADM-07: 삭제 → 목록에서 제거")
        @Test
        fun removes() {
            val couponRepo = FakeCouponRepository().apply { seed(10L, coupon()) }
            val sut = service(couponRepo)

            sut.delete(couponId = 10L)

            assertThat(couponRepo.findById(10L)).isNull()
        }
    }

    @Nested
    @DisplayName("발급내역 — listIssues")
    inner class ListIssues {

        @DisplayName("ADM-08: couponId 로 발급분 필터 → 해당 템플릿 발급분만 반환")
        @Test
        fun filtersByCouponId() {
            val issueRepo = FakeCouponIssueRepository().apply {
                save(CouponIssue.issue(couponId = 10L, userId = 1L, now = EXPIRED_AT))
                save(CouponIssue.issue(couponId = 10L, userId = 2L, now = EXPIRED_AT))
                save(CouponIssue.issue(couponId = 20L, userId = 3L, now = EXPIRED_AT))
            }

            val result = service(couponIssueRepository = issueRepo).listIssues(couponId = 10L, page = 0, size = 10)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.couponId }).containsOnly(10L)
            assertThat(result.map { it.userId }).containsExactlyInAnyOrder(1L, 2L)
        }
    }
}
