package com.freeform.unbounded

import android.content.Context
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.SharedMemory
import android.system.OsConstants
import android.os.Bundle
import android.net.Uri
import android.util.Log
import java.util.concurrent.atomic.AtomicReference
import java.nio.ByteBuffer
import android.view.SurfaceControl
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Authenticated one-way Binder transport for compact clock masks. */
internal object ClockMaskTransport {
    fun interface Receiver {
        fun onMask(sequence: Long, width: Int, height: Int, capturedNanos: Long, memory: SharedMemory?,
            sessionEnded: Boolean, transaction: SurfaceControl.Transaction?, bootstrap: Boolean)
    }

    private val senderEndpoint = AtomicReference<IBinder?>(null)
    private val pairedEndpoint = AtomicReference<IBinder?>(null)
    @Volatile private var systemUiEndpoint: Binder? = null
    @Volatile private var registeredReceiver: Receiver? = null
    @Volatile private var systemUiContext: Context? = null
    private val validatedWriterUids = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var nextLookupNanos = 0L
    private val replayLock = Any()
    private val replayStarted = AtomicBoolean(false)
    private var snapshot: Snapshot? = null
    private var deliveredTo: IBinder? = null
    private var deliveredSequence = 0L
    private var clearedThrough = 0L
    private data class Snapshot(val context: Context, val sequence: Long, val width: Int, val height: Int,
        val capturedNanos: Long, val memory: SharedMemory)

    private fun startReplay() {
        if (!replayStarted.compareAndSet(false, true)) return
        Executors.newSingleThreadScheduledExecutor {
            Thread(it, "FreeformMaskReplay").apply { isDaemon = true }
        }.scheduleWithFixedDelay({
            runCatching {
                synchronized(replayLock) {
                    val frame = snapshot ?: return@synchronized
                    val endpoint = resolveSenderEndpoint(frame.context) ?: return@synchronized
                    if (!ClockMaskReplayPolicy.shouldReplay(frame.sequence, deliveredSequence, deliveredTo == endpoint))
                        return@synchronized
                    if (supportsBootstrap(endpoint) && sendSnapshot(endpoint, frame, TRANSACTION_BOOTSTRAP)) {
                        deliveredTo = endpoint
                        deliveredSequence = frame.sequence
                        Log.i(TAG, "CI94 stationary clock-mask replay sequence=${frame.sequence} ageMs=" +
                            (System.nanoTime() - frame.capturedNanos) / 1_000_000)
                    }
                }
            }.onFailure { Log.w(TAG, "Clock-mask bootstrap retry failed", it) }
        }, 1, 1, TimeUnit.SECONDS)
    }

    fun registerSystemUiReceiver(context: Context, receiver: Receiver): Boolean {
        registeredReceiver = receiver
        systemUiContext = context.applicationContext ?: context
        val endpoint = systemUiEndpoint ?: createEndpoint().also { systemUiEndpoint = it }
        val extras = Bundle().apply { putBinder(EXTRA_ENDPOINT, endpoint) }
        return runCatching {
            val response = context.contentResolver.call(PROVIDER_URI, METHOD_REGISTER, null, extras)
            response?.getBoolean(EXTRA_REGISTERED, false) == true
        }.onFailure { Log.w(TAG, "Clock-mask Binder registration failed", it) }.getOrDefault(false)
    }

    fun send(
        context: Context,
        sequence: Long,
        width: Int,
        height: Int,
        alpha: ByteArray,
        capturedNanos: Long,
    ): Boolean = send(context, sequence, width, height, alpha.size, capturedNanos) { it.put(alpha) }

    fun send(context: Context, sequence: Long, width: Int, height: Int, alpha: ByteBuffer,
        capturedNanos: Long): Boolean =
        send(context, sequence, width, height, alpha.remaining(), capturedNanos) { it.put(alpha.duplicate()) }

    private fun send(context: Context, sequence: Long, width: Int, height: Int, byteCount: Int,
        capturedNanos: Long, write: (ByteBuffer) -> Unit): Boolean {
        if (!ClockMaskTransportPolicy.isValidFrame(width, height, byteCount)) return false
        var memory: SharedMemory? = null
        return try {
            memory = SharedMemory.create("freeform-clock-mask", byteCount)
            val mapped = memory.mapReadWrite()
            try { write(mapped) } finally { SharedMemory.unmap(mapped) }
            check(memory.setProtect(OsConstants.PROT_READ))
            synchronized(replayLock) {
                if (sequence <= maxOf(clearedThrough, snapshot?.sequence ?: 0L)) return false
                val frame = Snapshot(context.applicationContext ?: context, sequence, width, height, capturedNanos, requireNotNull(memory))
                snapshot?.memory?.close()
                snapshot = frame
                memory = null // The snapshot owns exactly one read-only SharedMemory.
                startReplay()
                val endpoint = resolveSenderEndpoint(context) ?: return false
                sendSnapshot(endpoint, frame, TRANSACTION_MASK).also { sent ->
                    if (sent) { deliveredTo = endpoint; deliveredSequence = sequence }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Clock-mask Binder send failed", error)
            false
        } finally {
            memory?.close()
        }
    }

    private fun sendSnapshot(endpoint: IBinder, frame: Snapshot, code: Int): Boolean {
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeLong(frame.sequence)
            data.writeInt(frame.width)
            data.writeInt(frame.height)
            data.writeLong(frame.capturedNanos)
            data.writeTypedObject(frame.memory, 0)
            endpoint.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } catch (error: RemoteException) {
            senderEndpoint.compareAndSet(endpoint, null)
            Log.w(TAG, "Clock-mask snapshot send failed", error)
            false
        } finally { data.recycle() }
    }

    private fun supportsBootstrap(endpoint: IBinder): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            if (!endpoint.transact(TRANSACTION_CAPABILITIES, data, reply, 0)) return false
            reply.readException()
            reply.readInt() >= 2
        } catch (_: RemoteException) { false }
        finally { data.recycle(); reply.recycle() }
    }

    fun createMemory(alpha: ByteBuffer): SharedMemory {
        val memory = SharedMemory.create("freeform-paired-mask", alpha.remaining())
        try {
            val mapped = memory.mapReadWrite()
            try { mapped.put(alpha.duplicate()) } finally { SharedMemory.unmap(mapped) }
            check(memory.setProtect(OsConstants.PROT_READ))
            return memory
        } catch (error: Exception) { memory.close(); throw error }
    }

    fun sendPrepared(context: Context, sequence: Long, width: Int, height: Int, memory: SharedMemory,
        capturedNanos: Long, transaction: SurfaceControl.Transaction): Boolean {
        if (!ClockMaskTransportPolicy.isValidFrame(width, height, memory.size)) return false
        // A previous asynchronous snapshot must not be replayed after entering
        // the paired transaction path, even while that path is stationary.
        discardSnapshot(sequence)
        val endpoint = resolveSenderEndpoint(context) ?: return false
        // One-way Binder success cannot tell us whether an older receiver read
        // the appended transaction. Negotiate before handing it a BLAST buffer.
        if (!supportsPairedFrames(endpoint)) return false
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeLong(sequence)
            data.writeInt(width)
            data.writeInt(height)
            data.writeLong(capturedNanos)
            data.writeTypedObject(memory, 0)
            data.writeTypedObject(transaction, 0)
            endpoint.transact(TRANSACTION_PAIR, data, null, IBinder.FLAG_ONEWAY)
        } catch (error: Exception) {
            senderEndpoint.compareAndSet(endpoint, null)
            Log.w(TAG, "Paired clock-mask Binder send failed", error)
            false
        } finally { data.recycle() }
    }

    private fun supportsPairedFrames(endpoint: IBinder): Boolean {
        if (pairedEndpoint.get() == endpoint && endpoint.isBinderAlive) return true
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            if (!endpoint.transact(TRANSACTION_CAPABILITIES, data, reply, 0)) return false
            reply.readException()
            (reply.readInt() >= 1).also { if (it) pairedEndpoint.set(endpoint) }
        } catch (error: RemoteException) {
            false
        } finally { data.recycle(); reply.recycle() }
    }

    fun clear(context: Context, sequence: Long, sessionEnded: Boolean): Boolean {
        discardSnapshot(sequence)
        val endpoint = resolveSenderEndpoint(context) ?: return false
        val data = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeLong(sequence)
            data.writeInt(if (sessionEnded) 1 else 0)
            endpoint.transact(TRANSACTION_CLEAR, data, null, IBinder.FLAG_ONEWAY)
        } catch (error: RemoteException) {
            senderEndpoint.compareAndSet(endpoint, null)
            Log.w(TAG, "Clock-mask clear failed", error)
            false
        } finally {
            data.recycle()
        }
    }

    private fun discardSnapshot(sequence: Long) {
        synchronized(replayLock) {
            clearedThrough = maxOf(clearedThrough, sequence)
            if (sequence > (snapshot?.sequence ?: 0L)) {
                snapshot?.memory?.close()
                snapshot = null
                deliveredTo = null
                deliveredSequence = 0L
            }
        }
    }

    private fun resolveSenderEndpoint(context: Context): IBinder? {
        senderEndpoint.get()?.takeIf { it.isBinderAlive }?.let { return it }
        senderEndpoint.set(null)
        val now = System.nanoTime()
        if (now < nextLookupNanos) return null
        nextLookupNanos = now + 1_000_000_000L
        val resolved = runCatching {
            context.contentResolver.call(PROVIDER_URI, METHOD_GET_ENDPOINT, null, null)
                ?.getBinder(EXTRA_ENDPOINT)
        }.onFailure { Log.w(TAG, "Clock-mask endpoint lookup failed", it) }.getOrNull()
            ?.takeIf { it.isBinderAlive } ?: return null
        return if (senderEndpoint.compareAndSet(null, resolved)) resolved else senderEndpoint.get()
    }

    private fun createEndpoint(): Binder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            if (code != TRANSACTION_MASK && code != TRANSACTION_CLEAR && code != TRANSACTION_PAIR &&
                code != TRANSACTION_CAPABILITIES && code != TRANSACTION_BOOTSTRAP)
                return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            val callingUid = Binder.getCallingUid()
            val context = systemUiContext ?: return false
            if (callingUid !in validatedWriterUids) {
                val packages = context.packageManager.getPackagesForUid(callingUid).orEmpty().toSet()
                if (!DepthFrameAccessPolicy.canPublishClockMask(packages)) {
                    Log.w(TAG, "Rejected clock-mask Binder caller uid=$callingUid packages=$packages")
                    return false
                }
                validatedWriterUids.add(callingUid)
            }
            if (code == TRANSACTION_CAPABILITIES) {
                reply?.writeNoException()
                reply?.writeInt(2)
                return true
            }
            val sequence = data.readLong()
            if (code == TRANSACTION_CLEAR) {
                val sessionEnded = data.readInt() != 0
                registeredReceiver?.onMask(sequence, 0, 0, 0, null, sessionEnded, null, false)
                return true
            }
            val width = data.readInt()
            val height = data.readInt()
            val capturedNanos = data.readLong()
            val memory = data.readTypedObject(SharedMemory.CREATOR) ?: return false
            val transaction = if (code == TRANSACTION_PAIR) data.readTypedObject(SurfaceControl.Transaction.CREATOR) else null
            if (!ClockMaskTransportPolicy.isValidFrame(width, height, memory.size)) {
                memory.close()
                transaction?.close()
                return false
            }
            val receiver = registeredReceiver
            if (receiver == null) {
                memory.close()
                transaction?.let { try { it.apply() } finally { it.close() } }
            } else receiver.onMask(sequence, width, height, capturedNanos, memory, false, transaction,
                code == TRANSACTION_BOOTSTRAP)
            return true
        }
    }

    private const val TAG = "FreeformUnbounded"
    private const val DESCRIPTOR = "com.freeform.unbounded.ICLockscreenMask"
    private const val TRANSACTION_MASK = IBinder.FIRST_CALL_TRANSACTION
    private const val TRANSACTION_CLEAR = IBinder.FIRST_CALL_TRANSACTION + 1
    private const val TRANSACTION_CAPABILITIES = IBinder.FIRST_CALL_TRANSACTION + 2
    private const val TRANSACTION_PAIR = IBinder.FIRST_CALL_TRANSACTION + 3
    private const val TRANSACTION_BOOTSTRAP = IBinder.FIRST_CALL_TRANSACTION + 4
    private const val METHOD_REGISTER = "register-clock-mask"
    private const val METHOD_GET_ENDPOINT = "get-clock-mask-endpoint"
    private const val EXTRA_ENDPOINT = "clock_mask_endpoint"
    private const val EXTRA_REGISTERED = "clock_mask_registered"
    private val PROVIDER_URI: Uri = Uri.parse("content://com.freeform.unbounded.depth-frames")
}

internal object ClockMaskTransportPolicy {
    const val MAX_DIMENSION = 4096
    const val MAX_PIXELS = 4_000_000

    fun isValidFrame(width: Int, height: Int, byteCount: Int): Boolean =
        width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION && byteCount in 1..MAX_PIXELS &&
            width.toLong() * height == byteCount.toLong()
}
