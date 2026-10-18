package jp.healthsync.daily.sync

enum class WorkDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

/**
 * 2xx is done. 408, 429, and 5xx are transient, as are network failures.
 * Other 4xx and missing configuration are not retried.
 * [runAttemptCount] is WorkManager's 0-based counter for the current run.
 */
fun decideSync(result: SendKind, runAttemptCount: Int, maxAttempts: Int = 5): WorkDecision {
    val transient = when (result) {
        SendKind.OK -> return WorkDecision.SUCCESS
        SendKind.CONFIG -> false
        SendKind.NETWORK -> true
        SendKind.HTTP_TRANSIENT -> true
        SendKind.HTTP_FINAL -> false
    }
    if (!transient) return WorkDecision.FAILURE
    return if (runAttemptCount < maxAttempts - 1) WorkDecision.RETRY else WorkDecision.FAILURE
}

enum class SendKind {
    OK,
    CONFIG,
    NETWORK,
    HTTP_TRANSIENT,
    HTTP_FINAL,
}

fun httpKind(status: Int): SendKind {
    return if (status == 408 || status == 429 || status in 500..599) {
        SendKind.HTTP_TRANSIENT
    } else {
        SendKind.HTTP_FINAL
    }
}
