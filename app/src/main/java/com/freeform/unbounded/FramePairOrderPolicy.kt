package com.freeform.unbounded

/** A later complete pair cannot overtake an earlier registered BLAST callback. */
internal object FramePairOrderPolicy {
    data class Readiness(val stamp: Long, val registered: Boolean, val bufferReady: Boolean, val maskReady: Boolean)

    fun newestCompletePrefix(frames: Iterable<Readiness>, watermark: Long): Long? {
        var ready: Long? = null
        for (frame in frames) {
            if (frame.stamp <= watermark) continue
            if (!frame.registered || !frame.bufferReady) break
            if (frame.maskReady) ready = frame.stamp
        }
        return ready
    }
}
