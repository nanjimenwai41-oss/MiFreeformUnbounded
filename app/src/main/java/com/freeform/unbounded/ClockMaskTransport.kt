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

/** Authenticated one-way Binder transport for compact clock masks. */
internal object ClockMaskTransport {
    fun interface Receiver {
        fun onMask(sequence: Long, width: Int, height: Int, capturedNanos: Long, memory: SharedMemory?,
            sessionEnded: Boolean)
    }

    private val senderEndpoint = AtomicReference<IBinder?>(null)
    @Volatile private var systemUiEndpoint: Binder? = null
    @Volatile private var registeredReceiver: Receiver? = null
    @Volatile private var systemUiContext: Context? = null
    private val validatedWriterUids = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var nextLookupNanos = 0L

    fun registerSystemUiReceiver(context: Context, receiver: Receiver): Boolean {
        registeredReceiver = receiver
        systemUiContext = context.applicationContext
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
    ): Boolean {
        if (!ClockMaskTransportPolicy.isValidFrame(width, height, alpha.size)) return false
        val endpoint = resolveSenderEndpoint(context) ?: return false
        val data = Parcel.obtain()
        var memory: SharedMemory? = null
        return try {
            memory = SharedMemory.create("freeform-clock-mask", alpha.size)
            val mapped = memory.mapReadWrite()
            try { mapped.put(alpha) } finally { SharedMemory.unmap(mapped) }
            check(memory.setProtect(OsConstants.PROT_READ))
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeLong(sequence)
            data.writeInt(width)
            data.writeInt(height)
            data.writeLong(capturedNanos)
            data.writeTypedObject(memory, 0)
            endpoint.transact(TRANSACTION_MASK, data, null, IBinder.FLAG_ONEWAY)
        } catch (error: Exception) {
            senderEndpoint.compareAndSet(endpoint, null)
            Log.w(TAG, "Clock-mask Binder send failed", error)
            false
        } finally {
            memory?.close()
            data.recycle()
        }
    }

    fun clear(context: Context, sequence: Long, sessionEnded: Boolean): Boolean {
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
            if (code != TRANSACTION_MASK && code != TRANSACTION_CLEAR) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(DESCRIPTOR)
            val sequence = data.readLong()
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
            if (code == TRANSACTION_CLEAR) {
                val sessionEnded = data.readInt() != 0
                registeredReceiver?.onMask(sequence, 0, 0, 0, null, sessionEnded)
                return true
            }
            val width = data.readInt()
            val height = data.readInt()
            val capturedNanos = data.readLong()
            val memory = data.readTypedObject(SharedMemory.CREATOR) ?: return false
            if (!ClockMaskTransportPolicy.isValidFrame(width, height, memory.size)) {
                memory.close()
                return false
            }
            val receiver = registeredReceiver
            if (receiver == null) memory.close()
            else receiver.onMask(sequence, width, height, capturedNanos, memory, sessionEnded = false)
            return true
        }
    }

    private const val TAG = "FreeformUnbounded"
    private const val DESCRIPTOR = "com.freeform.unbounded.ICLockscreenMask"
    private const val TRANSACTION_MASK = IBinder.FIRST_CALL_TRANSACTION
    private const val TRANSACTION_CLEAR = IBinder.FIRST_CALL_TRANSACTION + 1
    private const val METHOD_REGISTER = "register-clock-mask"
    private const val METHOD_GET_ENDPOINT = "get-clock-mask-endpoint"
    private const val EXTRA_ENDPOINT = "clock_mask_endpoint"
    private const val EXTRA_REGISTERED = "clock_mask_registered"
    private val PROVIDER_URI: Uri = Uri.parse("content://com.freeform.unbounded.depth-frames")
}

internal object ClockMaskTransportPolicy {
    fun isValidFrame(width: Int, height: Int, byteCount: Int): Boolean =
        width in 1..512 && height in 1..512 && width.toLong() * height == byteCount.toLong()
}
