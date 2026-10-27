package com.freeform.unbounded

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import java.io.File
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.Collections
import java.util.WeakHashMap

/** One capture per real foreground TextureView; never records notification text. */
internal object DepthViewDiagnostics {
    private val captured = Collections.newSetFromMap(WeakHashMap<TextureView, Boolean>())
    private val loggedAt = mutableMapOf<String, Long>()

    @Synchronized
    fun shouldLog(key: String): Boolean {
        val now = SystemClock.uptimeMillis()
        val previous = loggedAt[key]
        if (previous != null && now - previous < 3_000) return false
        loggedAt[key] = now
        return true
    }

    fun capture(view: TextureView, submitted: Bitmap, holderState: String) {
        if (!captured.add(view)) return
        view.postDelayed({
            runCatching {
                val out = File(view.context.cacheDir, "freeform-depth-view").apply { mkdirs() }
                val tree = StringBuilder("holder $holderState\n")
                var count = 0
                fun walk(node: View, depth: Int) {
                    if (++count > 700) return
                    val xy = IntArray(2).also(node::getLocationOnScreen)
                    val visible = Rect()
                    val shown = node.getGlobalVisibleRect(visible)
                    val id = runCatching { node.resources.getResourceName(node.id) }.getOrDefault("-")
                    tree.append(" ".repeat(depth)).append(node.javaClass.name)
                        .append(" id=$id xy=${xy.joinToString()} size=${node.width}x${node.height}")
                        .append(" visibility=${node.visibility} alpha=${node.alpha} z=${node.z}")
                        .append(" translation=${node.translationX},${node.translationY}")
                        .append(" scale=${node.scaleX},${node.scaleY} clip=${node.clipBounds}")
                        .append(" visible=$shown:$visible")
                    if (node is TextureView) tree.append(" opaque=${node.isOpaque} transform=${node.getTransform(null)}")
                    if (node is ViewGroup) tree.append(" clipChildren=${node.clipChildren} clipPadding=${node.clipToPadding}")
                    tree.append('\n')
                    if (node is ViewGroup) for (i in 0 until node.childCount) walk(node.getChildAt(i), depth + 1)
                }
                walk(view.rootView, 0)
                File(out, "tree.txt").writeText(tree.toString())
                File(out, "submitted.png").outputStream().use { submitted.compress(Bitmap.CompressFormat.PNG, 100, it) }
                view.getBitmap(236, 512)?.let { bitmap ->
                    File(out, "texture.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
                Log.i("FreeformUnbounded", "CI65 one-time foreground view evidence saved=$out nodes=$count")
            }.onFailure { Log.w("FreeformUnbounded", "Foreground view evidence failed", it) }
        }, 1_500)
    }
    /**
     * Dumps only names and shallow runtime types when a private MIUI holder
     * cannot be resolved. This is diagnostic evidence for obfuscated builds;
     * it does not retain the object or change rendering state.
     */
    fun describeReflectionObject(target: Any): String {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        return describeReflectionObject(target, visited, 0)
    }

    private fun describeReflectionObject(
        target: Any,
        visited: MutableSet<Any>,
        depth: Int,
    ): String {
        if (!visited.add(target)) return "<cycle>"
        val fields = mutableListOf<String>()
        var type: Class<*>? = target.javaClass
        while (type != null && depth <= 1) {
            type.declaredFields.forEach { field ->
                if (Modifier.isStatic(field.modifiers) || field.isSynthetic) return@forEach
                val value = runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
                val rendered = when (value) {
                    null -> "null"
                    is android.view.Surface ->
                        "Surface@${System.identityHashCode(value)} valid=${value.isValid}"
                    else -> {
                        val valueType = value.javaClass.name
                        if (depth == 0 &&
                            (valueType.contains("VideoDepthSurfaceHolder") ||
                                valueType.contains("DepthSurface") ||
                                valueType.contains("SurfaceHolder"))
                        ) {
                            "$valueType@${System.identityHashCode(value)}{" +
                                describeReflectionObject(value, visited, depth + 1) + "}"
                        } else {
                            "$valueType@${System.identityHashCode(value)}"
                        }
                    }
                }
                fields += "${field.name}:$rendered"
            }
            type = type.superclass
        }
        return "${target.javaClass.name}{${fields.joinToString(",")}}"
    }

}
