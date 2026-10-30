package com.freeform.unbounded

import android.os.Build
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Connects the module to the wallpaper process' real EGL swap thread.
 *
 * The native side only supplies a frame after it has verified a varying depth
 * attachment. Java remains responsible for the existing SystemUI transport.
 */
internal object NativeDepthBridge {
    private const val TAG = "FreeformUnbounded"

    // Native 端核对设备 ELF build ID、真实 Engine backend 和 relocation
    // 绑定地址后才安装采集。包名中的 vulkan 不是运行后端的证据。

    @Volatile
    private var loaded = false

    @Synchronized
    fun install(enabled: Boolean): Boolean {
        if (!enabled) {
            Log.i(TAG, "CI50 Filament camera probe skipped; super-wallpaper depth is disabled")
            return false
        }
        if (!loadLibrary()) return false
        runCatching { setEnabled(false) }
            .onFailure { error ->
                Log.w(TAG, "CI48 native observer enable failed", error)
            }
        return runCatching { installNativeHooks() }
            .onFailure { error ->
                Log.w(TAG, "CI50 Filament camera probe install failed", error)
            }
            .getOrDefault(false)
    }

    fun installFrameworkLoaderProbe(): Boolean {
        if (!loadLibrary()) return false
        return runCatching { installFrameworkLoaderProbeNative() }
            .onFailure { error -> Log.w(TAG, "CI58 framework loader probe install failed", error) }
            .getOrDefault(false)
    }

    /** Called from the EGL swap thread; the buffer is valid only for this call. */
    @JvmStatic
    fun onNativeDepthFrame(width: Int, height: Int, bytes: ByteBuffer): Boolean {
        if (!bytes.isDirect) {
            Log.w(TAG, "CI21 native EGL depth frame rejected: non-direct buffer")
            return false
        }
        val source = bytes.duplicate()
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        val result = SuperWallpaperDepthCapture.publishNativeFrame(width, height, source)
        Log.i(TAG, "CI21 native EGL depth result=${result.summary}")
        return result.published
    }

    /** Called from the EGL swap thread with the current color and depth buffers. */
    @JvmStatic
    fun onNativeClockMaskFrame(width: Int, height: Int, capturedNanos: Long, alpha: ByteBuffer): Boolean {
        if (!alpha.isDirect || !ClockMaskTransportPolicy.isValidFrame(width, height, alpha.remaining())) return false
        if (WallpaperFrameSync.offerMask(width, height, capturedNanos, alpha)) return true
        val sent = SuperWallpaperFrameBridge.publishClockMask(width, height, alpha, capturedNanos)
        if (DepthViewDiagnostics.shouldLog("native-clock-mask")) {
            Log.i(TAG, "CI87 GPU alpha mask=${width}x$height bytes=${alpha.remaining()} sent=$sent")
        }
        return sent
    }

    @JvmStatic
    fun onNativeBeforeSwap(capturedNanos: Long) = WallpaperFrameSync.beforeSwap(capturedNanos)

    @JvmStatic
    fun onNativeDepthMaskFrame(width: Int, height: Int, capturedNanos: Long, depth: ByteBuffer): Boolean {
        if (!depth.isDirect) return false
        val result = SuperWallpaperDepthCapture.publishNativeClockMask(
            width, height, depth.duplicate().order(ByteOrder.nativeOrder()).asShortBuffer(), capturedNanos,
        )
        if (DepthViewDiagnostics.shouldLog("native-clock-mask")) {
            Log.i(TAG, "CI74 native depth-only mask result=${result.summary}")
        }
        return result.published
    }

    /** Legacy ABI; the CI74 native publisher uses only onNativeDepthMaskFrame. */
    @JvmStatic
    fun onNativeColorDepthFrame(
        width: Int,
        height: Int,
        color: ByteBuffer,
        depth: ByteBuffer,
    ): Boolean {
        if (!color.isDirect || !depth.isDirect) {
            Log.w(TAG, "CI24 native EGL color/depth frame rejected: non-direct buffer")
            return false
        }
        val source = depth.duplicate()
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        val result = SuperWallpaperDepthCapture.publishNativeColorDepthFrame(
            width,
            height,
            color.duplicate(),
            source,
        )
        if (DepthViewDiagnostics.shouldLog("native-color-depth"))
            Log.i(TAG, "CI65 native EGL color/depth result=${result.summary}")
        return result.published
    }

    private fun loadLibrary(): Boolean {
        if (loaded) return true
        // ShadowHook is optional: its Java loader is not always visible from an
        // LSPosed module class loader. A failed optional load must not prevent
        // ByteHook or the module's own native library from loading.
        val shadowLoaded = loadNative("shadowhook")
        if (shadowLoaded) {
            Log.i(TAG, "CI21 shadowhook library loaded")
        } else {
            Log.w(TAG, "CI21 shadowhook library unavailable; continuing with ByteHook")
        }
        val byteHookLoaded = loadNative("bytehook")
        if (byteHookLoaded) {
            Log.i(TAG, "CI21 bytehook library loaded")
        } else {
            Log.w(TAG, "CI21 bytehook library unavailable")
        }
        val nativeLoaded = loadNative("freeformnative")
        if (nativeLoaded) {
            Log.i(TAG, "CI21 freeformnative library loaded bytehook=$byteHookLoaded shadowhook=$shadowLoaded")
        } else {
            Log.w(TAG, "CI21 freeformnative library unavailable")
        }
        loaded = nativeLoaded
        return nativeLoaded
    }

    /**
     * LSPosed may expose the module through an InMemoryDexFile class loader whose
     * System.loadLibrary namespace does not include the module's extracted libs.
     * Try both the normal loader and the module's absolute nativeLibraryDir.
     */
    private fun loadNative(name: String): Boolean {
        if (loaded) return true
        val firstLoad = runCatching { System.loadLibrary(name) }
        if (firstLoad.isSuccess) return true

        val fileName = "lib$name.so"
        val candidates = linkedSetOf<String>()
        val classLoader = NativeDepthBridge::class.java.classLoader
        var nativeLibraryDir: String? = null
        System.getProperty("java.library.path")
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            ?.forEach { candidates += File(it, fileName).absolutePath }

        // LSPosed can provide an injected class loader whose native namespace
        // cannot see the module APK. Ask that loader for its own resolved path
        // before relying on ActivityThread.currentApplication().
        runCatching {
            val method = ClassLoader::class.java.getDeclaredMethod(
                "findLibrary",
                String::class.java,
            )
            method.isAccessible = true
            method.invoke(classLoader, name) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let(candidates::add)

        runCatching {
            val context = processContext()
            val info = context?.packageManager?.getApplicationInfo("com.freeform.unbounded", 0)
            nativeLibraryDir = info?.nativeLibraryDir
            nativeLibraryDir?.takeIf { it.isNotBlank() }?.let {
                candidates += File(it, fileName).path
            }
        }
        for (candidate in candidates) {
            if (!File(candidate).isFile) {
                Log.w(TAG, "CI21 native candidate missing name=$name path=$candidate")
                continue
            }
            val loaded = runCatching { System.load(candidate) }
            if (loaded.isSuccess) {
                Log.i(TAG, "CI21 loaded $fileName from $candidate")
                return true
            }
            Log.w(
                TAG,
                "CI21 native candidate failed name=$name path=$candidate " +
                    "error=${loaded.exceptionOrNull()?.javaClass?.simpleName}:" +
                    "${loaded.exceptionOrNull()?.message}",
            )
        }
        if (candidates.isEmpty()) {
            Log.w(
                TAG,
                "CI21 native no candidates name=$name " +
                    "libraryPath=${System.getProperty("java.library.path")} " +
                    "classLoader=${classLoader?.javaClass?.name ?: "null"} " +
                    "nativeLibraryDir=${nativeLibraryDir ?: "null"} " +
                    "firstError=${describeLoadFailure(firstLoad.exceptionOrNull())}",
            )
        } else if (name == "freeformnative") {
            Log.w(
                TAG,
                "CI21 native load failed name=$name candidates=${candidates.size} " +
                    "abi=${Build.SUPPORTED_ABIS.joinToString(",")} " +
                    "classLoader=${classLoader?.javaClass?.name ?: "null"} " +
                    "nativeLibraryDir=${nativeLibraryDir ?: "null"} " +
                    "firstError=${describeLoadFailure(firstLoad.exceptionOrNull())}",
            )
        }
        return false
    }

    private fun describeLoadFailure(error: Throwable?): String =
        error?.let { "${it.javaClass.simpleName}:${it.message ?: ""}" } ?: "none"

    private fun processContext(): android.content.Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val currentApplication = activityThread.getDeclaredMethod("currentApplication").apply { isAccessible = true }
        (currentApplication.invoke(null) as? android.content.Context)?.applicationContext
    }.getOrNull() ?: runCatching {
        // LSPosed injects the module into a host process without creating the
        // module Application. The system context still has a PackageManager,
        // which exposes the installed module's extracted nativeLibraryDir.
        val activityThread = Class.forName("android.app.ActivityThread")
        val currentThread = activityThread.getDeclaredMethod("currentActivityThread").apply { isAccessible = true }
            .invoke(null)
        val systemContext = activityThread.getDeclaredMethod("getSystemContext").apply { isAccessible = true }
            .invoke(currentThread) as? android.content.Context
        systemContext?.applicationContext ?: systemContext
    }.getOrNull() ?: runCatching {
        val appGlobals = Class.forName("android.app.AppGlobals")
        val initialApplication = appGlobals.getDeclaredMethod("getInitialApplication").apply { isAccessible = true }
        (initialApplication.invoke(null) as? android.content.Context)?.applicationContext
    }.getOrNull()

    @JvmStatic
    private external fun installNativeHooks(): Boolean

    @JvmStatic
    private external fun installFrameworkLoaderProbeNative(): Boolean

    @JvmStatic
    private external fun setEnabled(enabled: Boolean)

    /** Drops context-owned PBOs before a wallpaper surface/context is rebuilt. */
    @JvmStatic
    fun resetReadback() {
        if (!loaded) return
        runCatching { resetNativeReadback() }
            .onFailure { error -> Log.w(TAG, "CI24 native EGL readback reset failed", error) }
    }

    @JvmStatic
    private external fun resetNativeReadback()
}
