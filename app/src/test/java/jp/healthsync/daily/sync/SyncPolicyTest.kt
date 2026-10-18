package jp.healthsync.daily.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun success_doesNotRetry() {
        assertEquals(WorkDecision.SUCCESS, decideSync(SendKind.OK, runAttemptCount = 0))
    }

    @Test
    fun configAndClientErrors_failImmediately() {
        assertEquals(WorkDecision.FAILURE, decideSync(SendKind.CONFIG, 0))
        assertEquals(WorkDecision.FAILURE, decideSync(httpKind(401), 0))
        assertEquals(WorkDecision.FAILURE, decideSync(httpKind(404), 3))
    }

    @Test
    fun transientFailures_retryUntilTheLastAttempt() {
        assertEquals(WorkDecision.RETRY, decideSync(SendKind.NETWORK, 0))
        assertEquals(WorkDecision.RETRY, decideSync(httpKind(503), 3))
        assertEquals(WorkDecision.RETRY, decideSync(httpKind(429), 3))
        assertEquals(WorkDecision.FAILURE, decideSync(httpKind(500), 4))
        assertEquals(WorkDecision.FAILURE, decideSync(SendKind.NETWORK, 4))
    }
}
