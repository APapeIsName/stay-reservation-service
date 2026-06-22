package com.stay.domain.coupon

import com.stay.support.assertBadRequest
import com.stay.support.assertConflict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * 카탈로그: docs/round-4/02-tdd-plan.md  B.2 CouponIssue (CIS-01~13)
 * 규칙 (Q2/Q5/ADR-004, specGap C-3 확정):
 *   - issue(couponId, userId, now): AVAILABLE 발급분 생성 (정적 팩토리, rule 08)
 *   - CouponIssueStatus 저장 값은 AVAILABLE, USED 2종 — EXPIRED 는 저장 안 함(파생, D-C3)
 *   - markUsed(now, expiredAt): 가드 ① USED → CONFLICT ② 만료(now > expiredAt) → BAD_REQUEST ③ 통과 시 USED
 *   - isUsable(now, expiredAt): AVAILABLE && now <= expiredAt
 *   - @Version 은 CouponIssue 에만 — 낙관락 단위 (ADR-004 §2)
 * 비고: markUsed 는 순수 상태 전이 → L1 unit (DailyRoom.consumeOne 동형).
 */
@Tag("unit")
class CouponIssueTest {

    private val issuedAt = LocalDateTime.parse("2026-06-17T10:00:00")
    private val now = LocalDateTime.parse("2026-06-17T10:00:00")
    private val notExpired = LocalDateTime.parse("2026-12-31T23:59:00")
    private val alreadyExpired = LocalDateTime.parse("2026-06-16T23:59:00")

    private fun availableIssue(couponId: Long = 10L, userId: Long = 1L) =
        CouponIssue.issue(couponId = couponId, userId = userId, now = issuedAt)

    private fun usedIssue(couponId: Long = 10L, userId: Long = 1L) =
        availableIssue(couponId, userId).apply { markUsed(now, notExpired) }

    @Nested
    @DisplayName("발급 — issue 팩토리")
    inner class Issue {

        @DisplayName("CIS-01: issue(10, 1, now) → AVAILABLE 발급분 생성")
        @Test
        fun creates() {
            val issue = CouponIssue.issue(couponId = 10L, userId = 1L, now = issuedAt)
            assertThat(issue.couponId).isEqualTo(10L)
            assertThat(issue.userId).isEqualTo(1L)
            assertThat(issue.status).isEqualTo(CouponIssueStatus.AVAILABLE)
            assertThat(issue.issuedAt).isEqualTo(issuedAt)
            assertThat(issue.usedAt).isNull()
        }

        @DisplayName("CIS-12: 발급 직후 version == 0 (낙관락 초기값)")
        @Test
        fun initialVersion() {
            assertThat(availableIssue().version).isEqualTo(0L)
        }
    }

    @Nested
    @DisplayName("소유 — belongsTo")
    inner class BelongsToOwner {

        @DisplayName("CIS-02: 발급분 소유자만 belongsTo true")
        @Test
        fun ownership() {
            val issue = availableIssue(userId = 1L)
            assertThat(issue.belongsTo(1L)).isTrue()
            assertThat(issue.belongsTo(2L)).isFalse()
        }
    }

    @Nested
    @DisplayName("사용 가능 판정 — isUsable (만료 파생)")
    inner class Usable {

        @DisplayName("CIS-03: AVAILABLE + 미만료 → true")
        @Test
        fun availableNotExpired() {
            assertThat(availableIssue().isUsable(now, notExpired)).isTrue()
        }

        @DisplayName("CIS-04: AVAILABLE + 만료 → false (status 는 AVAILABLE 이지만 사용 불가)")
        @Test
        fun availableExpired() {
            assertThat(availableIssue().isUsable(now, alreadyExpired)).isFalse()
        }

        @DisplayName("CIS-05: now == expiredAt (경계) → true (만료는 초과부터)")
        @Test
        fun boundaryInclusive() {
            assertThat(availableIssue().isUsable(notExpired, notExpired)).isTrue()
        }

        @DisplayName("CIS-06: USED + 미만료 → false (이미 사용은 만료와 무관하게 불가)")
        @Test
        fun usedNotUsable() {
            assertThat(usedIssue().isUsable(now, notExpired)).isFalse()
        }
    }

    @Nested
    @DisplayName("사용 처리 — markUsed (가드: 이미사용·만료)")
    inner class Use {

        @DisplayName("CIS-07: AVAILABLE + 미만료 markUsed → USED, usedAt == now")
        @Test
        fun marksUsed() {
            val issue = availableIssue()
            issue.markUsed(now, notExpired)
            assertThat(issue.status).isEqualTo(CouponIssueStatus.USED)
            assertThat(issue.usedAt).isEqualTo(now)
        }

        @DisplayName("CIS-08: 이미 USED 재사용 → CONFLICT, 최초 usedAt 보존")
        @Test
        fun rejectsAlreadyUsed() {
            val issue = availableIssue()
            issue.markUsed(now, notExpired)
            assertConflict { issue.markUsed(now.plusHours(1), notExpired) }
            assertThat(issue.status).isEqualTo(CouponIssueStatus.USED)
            assertThat(issue.usedAt).isEqualTo(now)
        }

        @DisplayName("CIS-09: AVAILABLE + 만료된 쿠폰 markUsed → BAD_REQUEST, AVAILABLE 유지")
        @Test
        fun rejectsExpired() {
            val issue = availableIssue()
            assertBadRequest { issue.markUsed(now, alreadyExpired) }
            assertThat(issue.status).isEqualTo(CouponIssueStatus.AVAILABLE)
        }

        @DisplayName("CIS-10: markUsed 성공 후 status·usedAt 영속 (멱등 아님)")
        @Test
        fun persistsAfterUse() {
            val issue = availableIssue().apply { markUsed(now, notExpired) }
            assertThat(issue.status).isEqualTo(CouponIssueStatus.USED)
            assertThat(issue.usedAt).isEqualTo(now)
        }

        @DisplayName("CIS-13: 소유·상태·만료 3가드 통과 경로 — belongsTo 후 markUsed")
        @Test
        fun fullUsablePath() {
            val issue = availableIssue(userId = 1L)
            assertThat(issue.belongsTo(1L)).isTrue()
            issue.markUsed(now, notExpired)
            assertThat(issue.status).isEqualTo(CouponIssueStatus.USED)
        }
    }

    @Nested
    @DisplayName("상태 enum — CouponIssueStatus")
    inner class StatusEnum {

        @DisplayName("CIS-11: CouponIssueStatus 는 AVAILABLE, USED 2종 (EXPIRED 부재 — 파생)")
        @Test
        fun twoStoredStatuses() {
            assertThat(CouponIssueStatus.values()).containsExactly(
                CouponIssueStatus.AVAILABLE,
                CouponIssueStatus.USED,
            )
        }
    }
}
