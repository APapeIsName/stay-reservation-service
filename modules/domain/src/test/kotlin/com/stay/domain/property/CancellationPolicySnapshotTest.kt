package com.stay.domain.property

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 카탈로그: docs/round-3/02-tdd-plan.md  B.1 CP-13~14 + B.3 CPS-08
 * D-B3 단일 소유: 경계 의미론 (CP-06~12·15~16) 은 CancellationPolicyTest 가 소유 —
 * 본 클래스는 변환 정합 (원본과 동일 결과) 과 Snapshot 고유 케이스 (음수 일수) 만 검증
 */
@Tag("unit")
class CancellationPolicySnapshotTest {

    private val policy = CancellationPolicy(
        listOf(RefundRule(7, 100), RefundRule(3, 50), RefundRule(0, 0)),
    )

    @DisplayName("CP-13: snapshot() 은 원본과 값 동등한 rules 를 가진 스냅샷을 반환한다")
    @Test
    fun snapshotPreservesRules() {
        val snapshot = policy.snapshot()
        assertThat(snapshot.rules).containsExactly(
            RefundRule(7, 100),
            RefundRule(3, 50),
            RefundRule(0, 0),
        )
    }

    @DisplayName("CP-14: 변환 후 refundAmount 가 원본 policy 와 동일 결과다 (6일 전 — 150000)")
    @Test
    fun snapshotComputesSameRefundAsOriginal() {
        val snapshot = policy.snapshot()
        val cancelledAt = LocalDateTime.parse("2026-07-04T10:00")
        val checkIn = LocalDate.parse("2026-07-10")
        assertThat(snapshot.refundAmount(300000L, cancelledAt, checkIn))
            .isEqualTo(policy.refundAmount(300000L, cancelledAt, checkIn))
            .isEqualTo(150000L)
    }

    @DisplayName("CPS-08: 체크인 이후 취소 (음수 일수) 는 0원 — CHECKED_IN 취소 경로에서 실제 도달")
    @Test
    fun refundsNothing_whenCancelledAfterCheckIn() {
        val snapshot = policy.snapshot()
        val cancelledAt = LocalDateTime.parse("2026-07-02T09:00")
        val checkIn = LocalDate.parse("2026-07-01")
        assertThat(snapshot.refundAmount(300000L, cancelledAt, checkIn)).isEqualTo(0L)
    }
}
