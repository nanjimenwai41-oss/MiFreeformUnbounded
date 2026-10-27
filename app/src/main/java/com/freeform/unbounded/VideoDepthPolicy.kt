package com.freeform.unbounded

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import android.view.View
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Adapter for the depth implementation already shipped with the clock library.
 *
 * Dynamic video wallpapers and Super wallpapers are rendered by different
 * applications, but SystemUI owns the same depth-avoidance evaluator for both.
 * Keeping this bridge reflection based lets the module work across vendor dex
 * revisions without linking against a private MIUI class at install time.
 */
internal object VideoDepthPolicy {
    private const val DEPTH_UTILS = "com.miui.clock.utils.avoid.ClockDepthAvoidRuleUtils"
    private const val TAG = "FreeformUnbounded"
    private const val KEYGUARD_PANEL_CONTROLLER =
        "com.android.keyguard.panel.KeyguardPanelViewController"
    // The RGBA frame is posted to MIUI's existing foreground Surface. The
    // deducted ImageView is opaque on the target build and must not be used
    // for this layer.
    private const val FOREGROUND_COMPOSITING_ENABLED = true

    private val superTypeMarkers = setOf(
        "super",
        "super_wallpaper",
        "superwallpaper",
        "blank_super_wallpaper",
        "aod_using_super_wallpaper",
    )

    private val superMagicTypes = setOf(100000, 100001)

    data class SafeBounds(val top: Float, val bottom: Float)

    private val sessions = Collections.synchronizedMap(IdentityHashMap<Any, VideoWallpaperCompatibilitySession>())
    private val superWallpaperActive = AtomicBoolean(false)
    private val ordinaryWallpaperActive = AtomicBoolean(false)
    private val sessionGeneration = AtomicLong(0)
    private val foregroundReflectionLogged = AtomicBoolean(false)
    private val evaluatorInitMethods = mutableMapOf<Class<*>, java.lang.reflect.Method>()
    private val foregroundLayerOriginalElevation = Collections.synchronizedMap(
        IdentityHashMap<View, Float>(),
    )
    private val foregroundPaint = android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG,
    )
    private val foregroundSourceRect = Rect()
    private val foregroundDestinationRect = Rect()

    private enum class WallpaperIdentity {
        SUPER,
        ORDINARY,
        UNKNOWN,
    }

    private val wallpaperInfoHolders = listOf(
        "mWallpaperInfo", "wallpaperInfo", "getWallpaperInfo",
        "mCurrentWallpaperInfo", "currentWallpaperInfo", "getCurrentWallpaperInfo",
        "mWallpaperData", "wallpaperData", "getWallpaperData",
        "mCurrentWallpaperData", "currentWallpaperData", "getCurrentWallpaperData",
        "mWallpaperInfoForVideo", "wallpaperInfoForVideo", "getWallpaperInfoForVideo",
        "mLockWallpaperInfo", "lockWallpaperInfo", "getLockWallpaperInfo",
        "mHomeWallpaperInfo", "homeWallpaperInfo", "getHomeWallpaperInfo",
        "mTemplateConfig", "templateConfig", "getTemplateConfig",
        "mLockscreenInfo", "lockscreenInfo", "getLockscreenInfo",
        "constantLockscreenInfo", "mConstantLockscreenInfo", "getConstantLockscreenInfo",
        "mWallpaperManager", "wallpaperManager", "getWallpaperManager",
        "mKeyguardWallpaperManager", "keyguardWallpaperManager",
        "mKeyguardWallpaper", "keyguardWallpaper", "getKeyguardWallpaper",
        "mMiuiKeyguardWallPaperManager", "miuiKeyguardWallPaperManager",
        "mMiuiKeyguardWallpaperManager", "miuiKeyguardWallpaperManager",
        "keyguardPanelViewInjector", "mKeyguardPanelViewInjector",
        "getKeyguardPanelViewController", "keyguardPanelViewController",
        "mKeyguardPanelViewController",
    )

    private val ordinaryTypeMarkers = setOf(
        "image",
        "video",
        "live",
        "ordinary",
        "static",
        "normal",
    )

    fun sessionFor(target: Any, enabled: Boolean): VideoWallpaperCompatibilitySession? {
        if (!enabled || !isSuperWallpaperTarget(target)) return null
        return sessions.getOrPut(target) {
            VideoWallpaperCompatibilitySession {
                sessionGeneration.incrementAndGet()
            }.also {
                it.establish(
                    WallpaperKind.SUPER,
                    depthEnabled = true,
                    // A readback Bitmap is diagnostic data, not MIUI's real
                    // alpha/normal video Surface contract.
                    framePathReady = false,
                    input = readInput(target),
                )
            }
        }
    }

    fun establishSession(target: Any?, enabled: Boolean): VideoDepthSessionSnapshot? {
        val receiver = target ?: return null
        if (!enabled) {
            invalidateSession(receiver)
            return null
        }
        if (!isSuperWallpaperTarget(receiver)) {
            invalidateSession(receiver)
            return null
        }
        superWallpaperActive.set(true)
        val session = sessions[receiver] ?: VideoWallpaperCompatibilitySession {
            sessionGeneration.incrementAndGet()
        }.also {
            sessions[receiver] = it
        }
        session.establish(
            WallpaperKind.SUPER,
            depthEnabled = true,
            framePathReady = false,
            input = readInput(receiver),
        )
        return session.snapshot()
    }

    fun invalidateSession(target: Any?) {
        val shouldClear = target == null || synchronized(sessions) { sessions.containsKey(target) }
        if (shouldClear) {
            invalidateAllSessions("session invalidated", target)
            if (target == null) ordinaryWallpaperActive.set(false)
        }
    }

    fun sessionSnapshot(target: Any?): VideoDepthSessionSnapshot? = target?.let { sessions[it]?.snapshot() }

    fun hasRealDepthFrame(): Boolean = SuperWallpaperFrameBridge.hasDepthFrame()

    /** True only while a verified renderer frame belongs to the active Super wallpaper session. */
    fun shouldForceDepthEffect(): Boolean =
        superWallpaperActive.get() && SuperWallpaperFrameBridge.hasDepthFrame()

    /**
     * HyperOS exposes a ready flag for the stock subject-mask path. A path on
     * disk is deliberately not considered here: those files can belong to the
     * previous wallpaper session and are not proof that this Super wallpaper
     * supplied a current foreground layer.
     */
    fun isDepthInputReady(target: Any?): Boolean {
        if (!isSuperWallpaperTarget(target)) return false
        val ready = (target?.let {
            readObject(
                it,
                "isDepthVideoReady",
                "getDepthVideoReady",
                "mDepthVideoReady",
                "depthVideoReady",
            )
        } as? Boolean) == true
        // A subject mask can survive an image -> Super wallpaper transition
        // on the same SystemUI object. It is not proof that this session has a
        // live renderer frame, so never use it to open the video Surface path.
        return ready
    }

    fun shouldEnableDepthEffect(target: Any?, current: Boolean): Boolean =
        current || (
            isSuperWallpaperTarget(target) &&
                (isDepthInputReady(target) || SuperWallpaperFrameBridge.hasDepthFrame())
            )

    fun contextOf(target: Any?): Context? = target?.let(::findContext) ?: processContext()

    /** Detects the currently selected Super wallpaper from a SystemUI object. */
    fun isSuperWallpaperTarget(target: Any?): Boolean {
        val value = target ?: return false
        return when (identifyWallpaper(value, 0, Collections.newSetFromMap(IdentityHashMap()))) {
            WallpaperIdentity.SUPER -> true.also { markSuperWallpaper() }
            WallpaperIdentity.ORDINARY -> markOrdinaryWallpaper(value)
            WallpaperIdentity.UNKNOWN -> {
                // These helpers are recreated by SystemUI and do not retain the
                // wallpaper info object. Carry only an already-confirmed Super
                // identity across this small set of depth-chain classes. The
                // old Settings fallback was global and incorrectly survived an
                // image -> Super transition.
                val className = value.javaClass.name.lowercase()
                superWallpaperActive.get() &&
                    !ordinaryWallpaperActive.get() &&
            (className.contains("keyguarddepth") ||
                className.contains("keyguardpanelviewcontroller") ||
                className.contains("videodepthsurfaceholder") ||
                className.contains("miuiclockcontroller"))
            }
        }
    }

    fun shouldUseVideoDepth(target: Any?, enabled: Boolean): Boolean =
        enabled && isSuperWallpaperTarget(target)

    /**
     * Applies the same safe-area calculation used by dynamic video wallpaper
     * clocks and repairs the controller/view cache after a stock update.
     */
    fun applyVideoDepth(target: Any?): SafeBounds? {
        val receiver = target ?: return null
        val session = sessionFor(receiver, true)
        writeMember(receiver, 1, "mSetWallpaperSupportDepth")
        writeMember(receiver, true, "wallpaperSupportDepth", "mWallpaperSupportDepth")

        val clockView = readObject(receiver, "mClockView", "clockView", "getClockView")
        if (clockView != null) {
            // The controller delegates the state to its active clock view.
            invokeBooleanSetter(clockView, "setWallpaperSupportDepth", true)
        }

        val bounds = readCurrentSafeBounds(receiver)
        if (bounds != null) {
            applyBounds(receiver, bounds)
            session?.markInitialized(bounds)
        }
        return bounds
    }

    /**
     * Re-enables the static subject-mask evaluator after MIUI's stock state
     * update clears it for Super wallpapers. The fields are deliberately
     * resolved on the live objects: their names and owning class have moved
     * between HyperOS revisions, while the state itself remains private.
     */
    fun forceStaticSubjectDepth(target: Any?): Boolean {
        val receiver = target ?: return false
        // Settings callbacks are delivered through an inner handler whose
        // `this$0` points at the panel controller. Resolve both shapes so a
        // callback cannot immediately overwrite the verified-frame state.
        val candidates = listOfNotNull(
            receiver,
            readObject(receiver, "this\$0", "this0", "outer"),
        ).distinct()
        var forced = false
        candidates.forEach { candidate ->
            val panelEnabled = writeMember(
                candidate,
                true,
                "depthEffectEnable",
                "mDepthEffectEnable",
            )
            // KeyguardDepthInteractor owns the inner switch directly on the
            // current HyperOS build. The nested lookup covers panel-owned
            // instances while the direct write covers the interactor itself.
            val directInteractorEnabled = writeMember(
                candidate,
                true,
                "depthEffectEnableInner",
                "mDepthEffectEnableInner",
            )
            val interactor = readObject(
                candidate,
                "keyguardDepthInteractor",
                "mKeyguardDepthInteractor",
                "getKeyguardDepthInteractor",
            )
            val interactorEnabled = interactor?.let {
                writeMember(
                    it,
                    true,
                    "depthEffectEnableInner",
                    "mDepthEffectEnableInner",
                )
            } ?: false
            forced = forced || panelEnabled || directInteractorEnabled || interactorEnabled
        }
        return forced
    }

    /**
     * Cancels the stock crop_subject parser that may still be running when a
     * verified renderer frame arrives. The parser captures the old wallpaper
     * state and otherwise resumes later with the known null-path gate.
     */
    fun cancelPendingDepthParse(target: Any?): Boolean {
        val job = readObject(target, "depthParseJob") ?: return false
        val cancel = runCatching {
            job.javaClass.methods.firstOrNull {
                it.name == "cancel" && it.parameterTypes.size <= 1
            } ?: job.javaClass.declaredMethods.firstOrNull {
                it.name == "cancel" && it.parameterTypes.size <= 1
            }
        }.getOrNull() ?: return false
        val cancelled = runCatching {
            cancel.isAccessible = true
            if (cancel.parameterTypes.isEmpty()) {
                cancel.invoke(job)
            } else {
                cancel.invoke(job, null)
            }
            true
        }.getOrDefault(false)
        if (cancelled) writeMember(target ?: return false, null, "depthParseJob")
        return cancelled
    }

    /**
     * Installs a verified renderer Bitmap into MIUI's depth evaluator.
     *
     * The Bitmap is a depth map, not a foreground image. Putting it on
     * KeyguardDepthInteractor.deductedImageView paints the entire screen with
     * an opaque grayscale image and bypasses DepthBitmapAvoidConcurrent, which
     * is the component that turns depth into a clock avoid result.
     */
    fun applyVerifiedDepthFrame(target: Any?, frame: Bitmap): Boolean {
        if (frame.isRecycled || frame.width <= 0 || frame.height <= 0) return false
        val receiver = target ?: return false
        val avoid = findDepthBitmapAvoid(receiver)
        if (avoid == null) {
            if (DepthViewDiagnostics.shouldLog("missing-evaluator:${receiver.javaClass.name}"))
                Log.w(TAG, "Verified depth frame rejected: DepthBitmapAvoidConcurrent not found " +
                    "target=${receiver.javaClass.name}")
            return false
        }
        val init = evaluatorInitMethods[avoid.javaClass] ?: allMethods(avoid.javaClass).firstOrNull { method ->
            method.name == "init" && method.parameterTypes.size == 3 &&
                method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                method.parameterTypes[1].name == Bitmap::class.java.name &&
                method.parameterTypes[2].name == java.lang.Boolean::class.java.name
        }
        if (init == null) {
            Log.w(TAG, "Verified depth frame rejected: evaluator init not found " +
                "class=${avoid.javaClass.name} methods=" +
                allMethods(avoid.javaClass).filter { it.name == "init" }
                    .joinToString { it.toGenericString() })
            return false
        }
        evaluatorInitMethods[avoid.javaClass] = init
        val wideColor = readObject(receiver, "context", "mContext")
            ?.let { it as? Context }
            ?.resources
            ?.configuration
            ?.isScreenWideColorGamut == true
        val installed = runCatching {
            init.isAccessible = true
            init.invoke(avoid, wideColor, frame, java.lang.Boolean.TRUE)
            true
        }.onFailure { error ->
            Log.w(TAG, "Unable to install verified depth Bitmap in MIUI evaluator", error)
        }.getOrDefault(false)
        if (!installed) return false

        // init supplies the evaluator; no no-argument refresh exists in the
        // verified OS4 class. Stock lifecycle still owns clock/layout updates.
        if (DepthViewDiagnostics.shouldLog("evaluator-installed")) Log.i(
            TAG,
            "Installed verified depth Bitmap in MIUI evaluator " +
                "size=${frame.width}x${frame.height} wideColor=$wideColor " +
                "evaluator=${avoid.javaClass.name}",
        )
        return true
    }

    /** Places the verified RGBA subject on MIUI's foreground Surface. */
    fun ensureVerifiedForegroundSurface(
        target: Any?,
        keyguardShowingOverride: Boolean? = null,
        reapplyKeyguardForegroundVisibility: Boolean = false,
    ): Boolean {
        val foregroundFrameAvailable = SuperWallpaperFrameBridge.hasRenderableForegroundFrame()
        if (!foregroundFrameAvailable || !isSuperWallpaperTarget(target)) return false
        val receiver = target ?: return false
        // OS4 当前 DEX：holder 属于 KeyguardDepthInteractor，panel 仅持有 interactor。
        val interactor = if (receiver.javaClass.name == "com.android.keyguard.depth.KeyguardDepthInteractor") {
            receiver
        } else readObject(receiver, "keyguardDepthInteractor") ?: return false
        readObject(interactor, "videoDepthSurfaceHolder")?.let { holder ->
            val texture = readObject(holder, "foregroundTextureView") as? android.view.TextureView
            val backgroundTexture = readObject(holder, "backgroundTextureView") as? android.view.TextureView
            val updateMonitor = readObject(interactor, "keyguardUpdateMonitorInjector")
            val powerManager = readObject(interactor, "powerManager")
            val panelKeyguardShowing = (readObject(receiver, "keyguardShowing") as? Boolean)
            val keyguardShowing = keyguardShowingOverride
                ?: panelKeyguardShowing
                ?: (readObject(updateMonitor, "mKeyguardShowing") as? Boolean)
                ?: false
            val deviceInteractive = (readObject(powerManager, "isInteractive") as? Boolean) == true
            val wallpaperIsShow = readObject(holder, "wallpaperIsShow") as? Boolean ?: false
            val panelStatusBarState = (readObject(receiver, "statusBarState") as? Number)?.toInt()
            val isKeyguardPanel = receiver.javaClass.name == KEYGUARD_PANEL_CONTROLLER
            val shouldReapplyPanelVisibility = reapplyKeyguardForegroundVisibility && isKeyguardPanel
            val panelVisibilityAllowsForeground = !shouldReapplyPanelVisibility ||
                KeyguardForegroundPolicy.shouldReapplyKeyguardForegroundVisibility(
                    statusBarState = panelStatusBarState,
                    defaultTheme = readObject(receiver, "isDefaultTheme") as? Boolean,
                    keyguardShowing = keyguardShowing,
                    deviceInteractive = deviceInteractive,
                    wallpaperIsShow = wallpaperIsShow,
                    foregroundFrameAvailable = foregroundFrameAvailable,
                    superWallpaper = isSuperWallpaperTarget(receiver),
                )
            if (shouldReapplyPanelVisibility && !panelVisibilityAllowsForeground &&
                DepthViewDiagnostics.shouldLog("foreground-panel-reapply-blocked")
            ) {
                Log.i(
                    TAG,
                    "CI73 foreground parent reapply held by live state " +
                        "statusBarState=$panelStatusBarState " +
                        "defaultTheme=${readObject(receiver, "isDefaultTheme") as? Boolean} " +
                        "keyguardShowing=$keyguardShowing interactive=$deviceInteractive " +
                        "wallpaperIsShow=$wallpaperIsShow frame=$foregroundFrameAvailable",
                )
            }
            val shouldShow = panelVisibilityAllowsForeground && KeyguardForegroundPolicy.shouldShowVerifiedForeground(
                foregroundFrameAvailable = foregroundFrameAvailable,
                superWallpaper = true,
                keyguardShowing = keyguardShowing,
                deviceInteractive = deviceInteractive,
                wallpaperIsShow = wallpaperIsShow,
            )
            if (texture != null) {
                // The verified native foreground owns its own Surface, so it
                // can remain visible when stock videoAvoidState is false. The
                // background texture is untouched and mirrors stock's latest
                // requested visibility when the keyguard or wallpaper hides.
                texture.visibility = when {
                    shouldShow -> View.VISIBLE
                    !wallpaperIsShow -> View.INVISIBLE
                    else -> backgroundTexture?.visibility ?: View.INVISIBLE
                }
                if (DepthViewDiagnostics.shouldLog("foreground-texture-visibility")) {
                    Log.i(
                        TAG,
                            "CI71 foreground TextureView state " +
                            "keyguardShowing=$keyguardShowing statusBarState=$panelStatusBarState " +
                            "interactive=$deviceInteractive " +
                            "wallpaperIsShow=$wallpaperIsShow " +
                            "current=${texture.visibility}",
                    )
                }
            }
            if (shouldShow) {
                if (shouldReapplyPanelVisibility) {
                    (readObject(receiver, "keyguardForegroundLayer") as? View)?.visibility = View.VISIBLE
                }
                ensureForegroundLayerAboveBackgroundClock(interactor)
            }
            else restoreForegroundLayerElevation(interactor)
            // The verified foreground bitmap contains an alpha mask. CI66's
            // live tree already reports opaque=false; correct the view only if
            // a rebuilt holder currently reports an opaque buffer.
            if (texture != null && texture.isOpaque) {
                runCatching { texture.setOpaque(false) }
                    .onFailure { error ->
                        Log.w(TAG, "Unable to enable alpha on verified foreground TextureView", error)
                    }
                if (DepthViewDiagnostics.shouldLog("foreground-texture-alpha")) {
                    Log.i(TAG, "CI69 foreground TextureView opaque=${texture.isOpaque} after alpha request")
                }
            }
            return true
        }
        if (!surfaceCreationRequested.add(interactor)) return false
        val invoked = invokeNoArg(interactor, "updateVideoDepthSurface")
        if (!invoked) surfaceCreationRequested.remove(interactor)
        Log.i(TAG, "CI62 requested verified OS4 foreground Surface created=$invoked")
        return invoked
    }

    /** OS4 父方法在 interactor 返回后仍会隐藏前景层；成功提交后再检查已确认的父容器。 */
    fun reapplyKeyguardForegroundAfterSurfacePost(target: Any?): Boolean {
        val panel = target ?: return false
        if (panel.javaClass.name != KEYGUARD_PANEL_CONTROLLER) return false
        val parent = readObject(panel, "keyguardForegroundLayer") as? View ?: return false
        val interactor = readObject(panel, "keyguardDepthInteractor") ?: return false
        val holder = readObject(interactor, "videoDepthSurfaceHolder")
        val texture = readObject(holder, "foregroundTextureView") as? android.view.TextureView
        if (parent.visibility == View.VISIBLE && texture?.visibility == View.VISIBLE) return true
        val result = ensureVerifiedForegroundSurface(
            panel,
            reapplyKeyguardForegroundVisibility = true,
        )
        if (result && DepthViewDiagnostics.shouldLog("foreground-parent-after-post")) {
            val parentVisibility = (readObject(panel, "keyguardForegroundLayer") as? View)?.visibility
            val textureVisibility =
                (readObject(holder, "foregroundTextureView") as? android.view.TextureView)?.visibility
            val restored = parentVisibility == View.VISIBLE && textureVisibility == View.VISIBLE
            Log.i(
                TAG,
                "CI73 foreground state after Surface post parent=$parentVisibility " +
                    "texture=$textureVisibility restored=$restored",
            )
        }
        return result
    }

    private val surfaceCreationRequested = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

    private fun ensureForegroundLayerAboveBackgroundClock(interactor: Any) {
        val layer = readObject(interactor, "keyguardForegroundLayer") as? View ?: return
        val clockInjector = readObject(interactor, "keyguardClockInjector") ?: return
        val backgroundClock = readObject(clockInjector, "keyguardClockView") as? View ?: return
        // This is the sibling-layer ordering observed in the installed OS4 tree.
        // Keep all stock clock elevations and lift only the verified subject layer.
        val wantedZ = backgroundClock.z + 0.05f
        val wantedElevation = wantedZ - layer.translationZ
        if (!wantedElevation.isFinite() || kotlin.math.abs(layer.elevation - wantedElevation) < 0.01f) return
        foregroundLayerOriginalElevation.putIfAbsent(layer, layer.elevation)
        layer.elevation = wantedElevation.coerceAtLeast(0f)
        if (DepthViewDiagnostics.shouldLog("foreground-layer-elevation")) {
            Log.i(TAG, "Applied verified subject-layer occlusion " +
                "foregroundZ=${layer.z} clockZ=${backgroundClock.z}")
        }
    }

    private fun restoreForegroundLayerElevation(target: Any?) {
        val receiver = target ?: return
        val interactor = if (receiver.javaClass.name == "com.android.keyguard.depth.KeyguardDepthInteractor")
            receiver else readObject(receiver, "keyguardDepthInteractor") ?: return
        val holder = readObject(interactor, "videoDepthSurfaceHolder")
        val foregroundTexture = readObject(holder, "foregroundTextureView") as? android.view.TextureView
        val backgroundTexture = readObject(holder, "backgroundTextureView") as? android.view.TextureView
        val wallpaperIsShow = readObject(holder, "wallpaperIsShow") as? Boolean ?: false
        val stockVisibility = if (wallpaperIsShow) {
            backgroundTexture?.visibility ?: View.INVISIBLE
        } else {
            View.INVISIBLE
        }
        if (foregroundTexture != null && foregroundTexture.visibility != stockVisibility) {
            foregroundTexture.visibility = stockVisibility
        }
        val layer = readObject(interactor, "keyguardForegroundLayer") as? View ?: return
        foregroundLayerOriginalElevation.remove(layer)?.let { previous -> layer.elevation = previous }
    }

    fun applyVerifiedForegroundFrame(target: Any?, frame: Bitmap): Boolean {
        if (!FOREGROUND_COMPOSITING_ENABLED) return false
        if (frame.isRecycled || frame.width <= 0 || frame.height <= 0) return false
        val surface = findForegroundSurface(target) ?: return false
        return runCatching {
            val canvas = surface.lockHardwareCanvas()
            try {
                if (canvas.width <= 0 || canvas.height <= 0) return@runCatching false
                canvas.drawColor(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.PorterDuff.Mode.CLEAR,
                )
                foregroundSourceRect.set(0, 0, frame.width, frame.height)
                foregroundDestinationRect.set(0, 0, canvas.width, canvas.height)
                canvas.drawBitmap(
                    frame,
                    foregroundSourceRect,
                    foregroundDestinationRect,
                    foregroundPaint,
                )
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
            val owner = if (target?.javaClass?.name == "com.android.keyguard.depth.KeyguardDepthInteractor") target
                else readObject(target, "keyguardDepthInteractor")
            val holder = readObject(owner, "videoDepthSurfaceHolder")
            (readObject(holder, "foregroundTextureView") as? android.view.TextureView)?.let {
                DepthViewDiagnostics.capture(it, frame,
                    listOf("hasNotification", "videoAvoidState", "wallpaperIsShow")
                        .joinToString { name -> "$name=${readObject(holder, name)}" })
            }
            if (DepthViewDiagnostics.shouldLog("foreground-posted")) Log.i(
                TAG,
                "Posted verified RGBA foreground frame to foregroundSurface " +
                    "size=${frame.width}x${frame.height} canvas=${canvas.width}x${canvas.height}",
            )
            true
        }.onFailure { error ->
            Log.w(TAG, "Unable to install verified RGBA foreground frame", error)
        }.getOrDefault(false)
    }

    /** Clears only the foreground layer owned by this bridge during teardown. */
    fun clearVerifiedForegroundFrame(target: Any?): Boolean {
        val surface = findForegroundSurface(target)
        if (surface == null) {
            restoreForegroundLayerElevation(target)
            return false
        }
        return runCatching {
            val canvas = surface.lockCanvas(null)
            try {
                if (canvas.width <= 0 || canvas.height <= 0) return@runCatching false
                canvas.drawColor(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.PorterDuff.Mode.CLEAR,
                )
            } finally {
                surface.unlockCanvasAndPost(canvas)
            }
            restoreForegroundLayerElevation(target)
            true
        }.onFailure { error ->
            Log.w(TAG, "Unable to clear verified RGBA foreground frame", error)
        }.getOrDefault(false)
    }

    private fun findForegroundSurface(target: Any?): android.view.Surface? {
        val receiver = target ?: return null
        // 当前 OS4 的真实 owner 是 KeyguardDepthInteractor。
        val owners = listOfNotNull(
            receiver,
            readObject(receiver, "keyguardDepthInteractor"),
            readObject(
                receiver,
                "keyguardPanelViewController",
                "mKeyguardPanelViewController",
                "getKeyguardPanelViewController",
                "keyguardPanelViewInjector",
                "mKeyguardPanelViewInjector",
            ),
            readObject(receiver, "this\$0", "this0", "outer"),
        ).distinctBy { System.identityHashCode(it) }
        val holder = owners.asSequence()
            .mapNotNull { owner ->
                readObject(
                    owner,
                    "videoDepthSurfaceHolder",
                    "mVideoDepthSurfaceHolder",
                    "getVideoDepthSurfaceHolder",
                ) ?: owner.takeIf {
                    it.javaClass.name.contains("VideoDepthSurfaceHolder")
                }
            }
            .firstOrNull()
        if (holder == null && foregroundReflectionLogged.compareAndSet(false, true)) {
            Log.w(
                TAG,
                "Foreground surface holder lookup failed; " +
                    "target=${DepthViewDiagnostics.describeReflectionObject(receiver)} owners=" +
                    owners.joinToString { DepthViewDiagnostics.describeReflectionObject(it) },
            )
        }
        val surface = readObject(
            holder,
            "foregroundSurface",
            "mForegroundSurface",
            "getForegroundSurface",
        ) as? android.view.Surface ?: return null
        return surface.takeIf { it.isValid }
    }

    private fun findDepthBitmapAvoid(target: Any): Any? {
        val injector = readObject(target, "keyguardClockInjector", "mKeyguardClockInjector")
        val clock = listOfNotNull(
            readObject(target, "keyguardClockView", "mKeyguardClockView", "getKeyguardClockView"),
            readObject(injector, "keyguardClockView", "mKeyguardClockView", "getKeyguardClockView"),
            injector,
            target,
        ).distinctBy { System.identityHashCode(it) }
        val found = clock.firstNotNullOfOrNull { candidate ->
            readObject(candidate, "getDepthBitmapAvoidConcurrent", "depthBitmapAvoidConcurrent", "mDepthBitmapAvoidConcurrent")
        }
        if (DepthViewDiagnostics.shouldLog("evaluator-lookup:${target.javaClass.name}")) Log.i(TAG, "Depth evaluator lookup target=${target.javaClass.name} " +
            "injector=${injector?.javaClass?.name ?: "null"} candidates=${clock.map { it.javaClass.name }} " +
            "found=${found?.javaClass?.name ?: "null"}")
        return found
    }

    private fun allMethods(type: Class<*>): Sequence<java.lang.reflect.Method> = sequence {
        var current: Class<*>? = type
        while (current != null) {
            current.declaredMethods.forEach { yield(it) }
            current = current.superclass
        }
    }

    private fun invokeNoArg(target: Any, name: String): Boolean = runCatching {
        val method = allMethods(target.javaClass).firstOrNull {
            it.name == name && it.parameterTypes.isEmpty()
        } ?: return@runCatching false
        method.isAccessible = true
        method.invoke(target)
        true
    }.onFailure { error ->
        Log.w(TAG, "Unable to invoke MIUI depth refresh $name", error)
    }.getOrDefault(false)

    /**
     * Builds the clock avoid rectangle from the evaluator margins. The
     * evaluator exposes both values as distances from the screen edges, so
     * the bottom coordinate is derived from the lower inset.
     */
    fun safeAvoidRect(target: Any?, bounds: SafeBounds? = null): Rect? {
        val receiver = target ?: return null
        val context = findContext(receiver) ?: return null
        val safeBounds = bounds ?: readCurrentSafeBounds(receiver) ?: return null
        val height = displayHeight(receiver, context)
        if (height <= 0) return null
        val width = displayWidth(receiver, context)
        val top = (safeBounds.top * height).toInt().coerceIn(0, height)
        val bottomInset = (safeBounds.bottom * height).toInt().coerceIn(0, height)
        val bottom = (height - bottomInset).coerceIn(top, height)
        return Rect(0, top, width, bottom).also { sessions[receiver]?.setAvoidRect(it) }
    }

    private fun readInput(target: Any): VideoDepthInput? {
        val context = findContext(target) ?: return null
        val metrics = context.resources.displayMetrics
        val width = displayWidth(target, context)
        val height = displayHeight(target, context)
        if (width <= 0 || height <= 0 || !metrics.density.isFinite() || metrics.density <= 0f) return null
        val rotation = (readObject(target, "getDisplayRotation", "displayRotation", "mRotation") as? Number)?.toInt() ?: 0
        return VideoDepthInput(width, height, metrics.density, rotation, readCurrentSafeBounds(target) != null)
    }

    /** Exposed for deterministic unit tests and compatibility diagnostics. */
    internal fun normalizeBound(value: Float, displayHeight: Int, density: Float): Float? {
        if (!value.isFinite() || displayHeight <= 0 || !density.isFinite() || density <= 0f) return null
        if (value in -0.25f..1.25f) return value.coerceIn(0f, 1f)
        return (value * density / displayHeight.toFloat()).takeIf(Float::isFinite)?.coerceIn(0f, 1f)
    }

    private fun readCurrentSafeBounds(target: Any): SafeBounds? = runCatching {
        val context = findContext(target) ?: return@runCatching null
        val loaders = listOfNotNull(
            target.javaClass.classLoader,
            context.javaClass.classLoader,
            Thread.currentThread().contextClassLoader,
        ).distinct()
        var safeBounds: Any? = null
        for (loader in loaders) {
            val utilsClass = runCatching { Class.forName(DEPTH_UTILS, false, loader) }.getOrNull() ?: continue
            val method = utilsClass.methods.firstOrNull {
                it.name == "getCurrentSafeBounds" && it.parameterTypes.contentEquals(arrayOf(Context::class.java))
            } ?: continue
            val receiver = if (Modifier.isStatic(method.modifiers)) {
                null
            } else {
                utilsClass.fields.firstOrNull { it.name == "INSTANCE" }
                    ?.let { field -> runCatching { field.get(null) }.getOrNull() }
            }
            safeBounds = runCatching { method.invoke(receiver, context) }.getOrNull()
            if (safeBounds != null) break
        }
        val top = readNumber(safeBounds, "getTop", "top")?.toFloat() ?: return@runCatching null
        val bottom = readNumber(safeBounds, "getBottom", "bottom")?.toFloat() ?: return@runCatching null
        val height = displayHeight(target, context)
        val density = context.resources.displayMetrics.density
        val normalizedTop = normalizeBound(top, height, density) ?: return@runCatching null
        val normalizedBottom = normalizeBound(bottom, height, density) ?: return@runCatching null
        SafeBounds(normalizedTop, normalizedBottom)
    }.getOrNull()

    private fun applyBounds(target: Any, bounds: SafeBounds) {
        val height = displayHeight(target, findContext(target) ?: return)
        if (height <= 0) return
        val top = (bounds.top * height).toInt().coerceIn(0, height)
        val bottomInset = (bounds.bottom * height).toInt().coerceIn(0, height)
        val bottom = (height - bottomInset).coerceIn(top, height)
        val candidates = listOfNotNull(
            target,
            readObject(target, "mClockView", "clockView", "getClockView"),
        )
        candidates.forEach { candidate ->
            writeMember(candidate, top, "mTopMin")
            writeMember(candidate, bottom, "mBottomMax")
            writeMember(candidate, Point(top, bottom), "mTopAndBottom")
        }
    }

    private fun findContext(target: Any): Context? =
        readObject(target, "mContext", "context", "getContext") as? Context

    private fun processContext(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val method = activityThread.getDeclaredMethod("currentApplication")
        method.isAccessible = true
        (method.invoke(null) as? Context)?.applicationContext
    }.getOrNull()

    private fun identifyWallpaper(
        value: Any,
        depth: Int,
        visited: MutableSet<Any>,
    ): WallpaperIdentity {
        if (depth > 3 || !visited.add(value)) return WallpaperIdentity.UNKNOWN
        val className = value.javaClass.name.lowercase()
        if (className.contains("superwallpaper") || className.contains("super_wallpaper")) {
            return WallpaperIdentity.SUPER
        }

        val markers = listOfNotNull(
            readObject(value, "getName", "name", "mName", "getCategoryName", "categoryName"),
            readObject(
                value,
                "getKeyguardWallpaperType",
                "getWallpaperType",
                "mKeyguardWallpaperType",
                "keyguardWallpaperType",
                "mWallpaperType",
                "wallpaperType",
            ),
            readObject(value, "getCurrentJson", "currentJson", "mCurrentJson"),
            readObject(value, "getResourceType", "resourceType", "mResourceType"),
            readObject(value, "getSource", "source", "mSource"),
        ).map { it.toString().lowercase() }
        if (markers.any(::isOrdinaryWallpaperType)) return WallpaperIdentity.ORDINARY
        if (markers.any(::isSuperWallpaperMarker)) return WallpaperIdentity.SUPER

        val magicType = (readObject(value, "getMagicType", "mMagicType", "magicType") as? Number)?.toInt()
        if (magicType != null) {
            if (magicType in superMagicTypes) return WallpaperIdentity.SUPER
            if (magicType in ordinaryMagicTypes) return WallpaperIdentity.ORDINARY
        }
        if (readObject(value, "isSuperWallpaper", "getIsSuperWallpaper") == true) {
            return WallpaperIdentity.SUPER
        }

        var hasSuperWallpaper = false
        wallpaperInfoHolders.asSequence()
            .mapNotNull { readObject(value, it) }
            .filter { it !== value }
            .forEach { nested ->
                when (identifyWallpaper(nested, depth + 1, visited)) {
                    WallpaperIdentity.ORDINARY -> return WallpaperIdentity.ORDINARY
                    WallpaperIdentity.SUPER -> hasSuperWallpaper = true
                    WallpaperIdentity.UNKNOWN -> Unit
                }
            }
        return if (hasSuperWallpaper) WallpaperIdentity.SUPER else WallpaperIdentity.UNKNOWN
    }

    private fun isSuperWallpaperMarker(value: String): Boolean {
        val normalized = value.lowercase()
        return superTypeMarkers.any { marker ->
            normalized == marker || (marker != "super" && normalized.contains(marker))
        } ||
            normalized.contains("resourcetype=super_wallpaper") ||
            normalized.contains("resource_type=super_wallpaper") ||
            normalized.contains("source=snowmountain") ||
            normalized.contains("origineresourcepath=snowmountain")
    }

    private fun isOrdinaryWallpaperType(value: String): Boolean {
        val normalized = value.lowercase()
        return ordinaryTypeMarkers.any { marker ->
            normalized == marker ||
                normalized.endsWith("_$marker") ||
                normalized.contains("type=$marker") ||
                normalized.contains("resourcetype=$marker") ||
                normalized.contains("resource_type=$marker") ||
                normalized.contains("wallpapertype=$marker") ||
                normalized.contains("\"type\":\"$marker\"") ||
                normalized.contains("\"wallpapertype\":\"$marker\"")
        }
    }

    private val ordinaryMagicTypes = setOf(20000)

    private fun hasStockSubjectMask(
        value: Any?,
        depth: Int = 0,
        visited: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap()),
    ): Boolean {
        val current = value ?: return false
        if (depth > 4 || !visited.add(current)) return false

        val supportSubject = readObject(
            current,
            "getSupportSubject",
            "isSupportSubject",
            "supportSubject",
            "mSupportSubject",
        ) as? Boolean
        val cropSubject = readObject(
            current,
            "getCropSubject",
            "cropSubject",
            "mCropSubject",
        ) as? String
        val subject = readObject(
            current,
            "getSubject",
            "subject",
            "mSubject",
        ) as? String
        if (supportSubject == true && (!cropSubject.isNullOrBlank() || !subject.isNullOrBlank())) {
            return true
        }

        return wallpaperInfoHolders.asSequence()
            .mapNotNull { readObject(current, it) }
            .filter { it !== current }
            .any { hasStockSubjectMask(it, depth + 1, visited) }
    }

    private fun markOrdinaryWallpaper(target: Any): Boolean {
        ordinaryWallpaperActive.set(true)
        invalidateAllSessions("ordinary wallpaper detected", target, ordinaryDetected = true)
        return false
    }

    private fun markSuperWallpaper() {
        ordinaryWallpaperActive.set(false)
        superWallpaperActive.set(true)
    }

    private fun invalidateAllSessions(reason: String, target: Any?, ordinaryDetected: Boolean = false) {
        surfaceCreationRequested.clear()
        synchronized(sessions) {
            sessions.values.forEach { it.invalidate() }
            sessions.clear()
        }
        superWallpaperActive.set(false)
        if (ordinaryDetected) ordinaryWallpaperActive.set(true)
        SuperWallpaperFrameBridge.clear(contextOf(target), clockMaskSessionEnded = true, reason = reason)
    }

    private fun displayHeight(target: Any, context: Context): Int {
        val viewHeight = (target as? View)?.height ?: 0
        return if (viewHeight > 0) viewHeight else context.resources.displayMetrics.heightPixels
    }

    private fun displayWidth(target: Any, context: Context): Int {
        val viewWidth = (target as? View)?.width ?: 0
        return if (viewWidth > 0) viewWidth else context.resources.displayMetrics.widthPixels
    }

    private fun invokeBooleanSetter(target: Any, name: String, value: Boolean): Boolean = runCatching {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
            }
            if (method != null) {
                method.isAccessible = true
                method.invoke(target, value)
                return@runCatching true
            }
            type = type.superclass
        }
        false
    }.getOrDefault(false)

    private fun writeMember(target: Any, value: Any?, vararg names: String): Boolean = runCatching {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val field = type.declaredFields.firstOrNull { it.name in names }
            if (field != null) {
                if (value is Int && field.type != Int::class.javaPrimitiveType && field.type != Int::class.java) {
                    return@runCatching false
                }
                if (value is Boolean && field.type != Boolean::class.javaPrimitiveType && field.type != Boolean::class.java) {
                    return@runCatching false
                }
                field.isAccessible = true
                field.set(target, value)
                return@runCatching true
            }
            type = type.superclass
        }
        false
    }.getOrDefault(false)

    private fun readObject(target: Any?, vararg names: String): Any? {
        val value = target ?: return null
        var type: Class<*>? = value.javaClass
        while (type != null) {
            val method = type.declaredMethods.firstOrNull { it.name in names && it.parameterTypes.isEmpty() }
            val field = type.declaredFields.firstOrNull { it.name in names }
            if (method != null || field != null) {
                return runCatching {
                    method?.isAccessible = true
                    field?.isAccessible = true
                    method?.invoke(value) ?: field?.get(value)
                }.getOrNull()
            }
            type = type.superclass
        }
        return null
    }

    private fun readNumber(target: Any?, vararg names: String): Number? =
        readObject(target, *names) as? Number
}
