package com.freeform.unbounded

/** Provider 进程可以独立于 SystemUI 重建，成功注册也需要续期。 */
internal object EndpointRegistrationPolicy {
    const val RENEW_INTERVAL_NANOS = 5_000_000_000L

    fun bootstrapDelayMillis(raw: String?): Long = raw?.toLongOrNull()?.coerceIn(0, 60_000) ?: 0L

    fun shouldRegister(registered: Boolean, lastAttemptNanos: Long, nowNanos: Long): Boolean =
        nowNanos - lastAttemptNanos >= if (registered) RENEW_INTERVAL_NANOS else 1_000_000_000L
}
