package com.freeform.unbounded

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** URI 帧负载读写；沿用桥接层的校验和线程局部缓冲策略。 */
internal object DepthFramePayload {
    private const val TAG = "FreeformUnbounded"
    private val framePayloadScratch = ThreadLocal<ByteArray>()

    fun write(context: Context, uri: android.net.Uri, bitmap: Bitmap) {
        val byteCount = bitmap.byteCount
        var bytes = framePayloadScratch.get()
        if (bytes == null || bytes.size < byteCount) {
            bytes = ByteArray(byteCount)
            framePayloadScratch.set(bytes)
        }
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(bytes, 0, byteCount).order(ByteOrder.nativeOrder()))
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes, 0, byteCount)
        } ?: error("ContentProvider returned null output stream for $uri")
    }

    fun read(
        context: Context,
        uri: android.net.Uri?,
        width: Int,
        height: Int,
        bytes: Int,
        expectedKind: String,
        authority: String,
        maxDimension: Int,
        uriExtra: String,
        sequence: Int,
    ): Bitmap? {
        if (uri == null) return null
        val segment = uri.lastPathSegment
        if (uri.scheme != "content" || uri.authority != authority ||
            uri.pathSegments.size != 1 || segment == null ||
            !segment.matches(Regex("[0-9]+-$expectedKind"))
        ) {
            Log.w(TAG, "Ignoring frame with unexpected URI=$uri")
            return null
        }
        val expectedBytes = width.toLong() * height.toLong() * 4L
        if (width <= 0 || height <= 0 ||
            width > maxDimension || height > maxDimension ||
            bytes <= 0 || expectedBytes > Int.MAX_VALUE ||
            bytes.toLong() != expectedBytes
        ) {
            return null
        }
        val started = System.nanoTime()
        return runCatching {
            val payload = context.contentResolver.openInputStream(uri)?.use { input ->
                val bounded = ByteArray(bytes)
                var offset = 0
                while (offset < bounded.size) {
                    val count = input.read(bounded, offset, bounded.size - offset)
                    require(count >= 0) { "Frame ended at $offset expected=$bytes" }
                    offset += count
                }
                require(input.read() < 0) { "Frame contains bytes beyond expected=$bytes" }
                bounded
            } ?: error("ContentProvider returned null input stream")
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(payload).order(ByteOrder.nativeOrder()))
            bitmap
        }.onFailure { error ->
            Log.w(TAG, "Unable to read URI frame uri=$uri", error)
        }.getOrNull().also {
            if (DepthViewDiagnostics.shouldLog("uri-received:$uriExtra")) Log.i(
                TAG,
                    "URI frame received extra=$uriExtra sequence=$sequence " +
                    "bytes=$bytes size=${width}x${height} decoded=${it != null} " +
                    "decodeMs=${(System.nanoTime() - started) / 1_000_000L}",
            )
        }
    }

}
