package com.freeform.unbounded

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.File

/** Serves frame payloads through URI grants instead of broadcast file descriptors. */
class DepthFrameContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "register-clock-mask" && method != "get-clock-mask-endpoint") {
            return super.call(method, arg, extras)
        }
        val appContext = requireNotNull(context)
        val packages = appContext.packageManager.getPackagesForUid(Binder.getCallingUid()).orEmpty().toSet()
        if (method == "register-clock-mask") {
            if (!DepthFrameAccessPolicy.canRegisterClockMaskReceiver(packages)) {
                throw SecurityException("Only SystemUI may register the clock-mask receiver")
            }
            val endpoint = requireNotNull(extras?.getBinder("clock_mask_endpoint"))
            clockMaskEndpoint = endpoint
            return Bundle().apply { putBoolean("clock_mask_registered", true) }
        }
        if (!DepthFrameAccessPolicy.canPublishClockMask(packages)) {
            throw SecurityException("Clock-mask endpoint denied to $packages")
        }
        return Bundle().apply {
            clockMaskEndpoint?.takeIf { it.isBinderAlive }?.let { putBinder("clock_mask_endpoint", it) }
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        enforceFrameCaller(writing = mode != "r")
        val file = frameFile(uri)
        file.parentFile?.mkdirs()
        if (mode.contains('w')) {
            val kind = uri.lastPathSegment.orEmpty().substringAfter('-', "")
            pruneFiles(file.parentFile, kind)
            return ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_WRITE_ONLY,
            )
        }
        require(file.isFile) { "Missing depth frame ${uri.lastPathSegment}" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        enforceFrameCaller(writing = true)
        return if (frameFile(uri).delete()) 1 else 0
    }

    private fun enforceFrameCaller(writing: Boolean) {
        val appContext = requireNotNull(context)
        val callerUid = Binder.getCallingUid()
        if (callerUid == appContext.applicationInfo.uid) return
        val packages = appContext.packageManager.getPackagesForUid(callerUid).orEmpty().toSet()
        if (!DepthFrameAccessPolicy.canAccess(packages, writing)) {
            throw SecurityException("Frame access denied uid=$callerUid writing=$writing")
        }
    }

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    private fun frameFile(uri: Uri): File {
        val name = uri.lastPathSegment ?: error("Missing frame name")
        require(name.matches(NAME_PATTERN)) { "Invalid frame name" }
        val appContext = requireNotNull(context)
        require(uri.scheme == "content" && uri.authority == "${appContext.packageName}.depth-frames") {
            "Unexpected frame URI authority"
        }
        return File(appContext.cacheDir, "depth-frames/$name.bin")
    }

    private fun pruneFiles(directory: File?, kind: String) {
        val now = System.currentTimeMillis()
        val frames = directory?.listFiles()
            ?.filter { it.isFile && it.name.matches(FILE_PATTERN) && it.name.endsWith("-$kind.bin") }
            .orEmpty()
        frames.filter { now - it.lastModified() > MAX_AGE_MILLIS }
            .forEach { it.delete() }
        frames.asSequence()
            .filter { it.exists() }
            .sortedByDescending { it.name.substringBefore('-').toLongOrNull() ?: 0L }
            .drop(MAX_RETAINED_FRAMES_PER_KIND - 1)
            .forEach { it.delete() }
    }

    private companion object {
        @Volatile var clockMaskEndpoint: IBinder? = null
        val NAME_PATTERN = Regex("[0-9]+-(depth|foreground)")
        val FILE_PATTERN = Regex("[0-9]+-(depth|foreground)\\.bin")
        const val MAX_AGE_MILLIS = 30_000L
        const val MAX_RETAINED_FRAMES_PER_KIND = 12
    }
}
