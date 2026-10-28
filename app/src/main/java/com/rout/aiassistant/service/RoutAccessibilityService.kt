package com.rout.aiassistant.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * ROUT screen control (user-enabled in system Accessibility settings; default off).
 *
 * Approved scope, enforced here:
 * - Command-only actions: tap a named element, scroll, Back, Home, Recents.
 * - Screen text is read on-device only, to find what the user named. It is
 *   never sent anywhere by this service. The only path that shares screen
 *   text is an explicit user "ask AI about my screen" request, handled by the
 *   chat flow with a visible disclosure - not here.
 * - Fails closed on sensitive screens: password fields, OTP/verification-code
 *   screens and payment/checkout screens are never read or acted on.
 * - Screen content is untrusted: text on screen can never trigger actions.
 *   Only commands routed from the user's own input reach performCommand().
 */
class RoutAccessibilityService : AccessibilityService() {
    /** Test hook only: forces the sensitive-screen verdict. Always null in production. */
    internal var sensitiveOverrideForTest: (() -> Boolean)? = null
    /** Test hook only: pretend there is no readable window. Null in production. */
    internal var readableForTest: Boolean? = null
    /** Test hook only: labels of nodes under a point. Null in production (real node tree is used). */
    internal var labelsAtForTest: ((Float, Float) -> List<String>?)? = null

    /** True only when a window is readable AND it does not look sensitive. Gestures/typing need this (fail closed). */
    fun readableAndSafe(): Boolean {
        readableForTest?.let { if (!it) return false; return !isSensitiveScreen() }
        val root = rootInActiveWindow ?: return false
        root.recycle()
        return !isSensitiveScreen()
    }

    /** Labels (text/description) of every node whose bounds contain the point; null when unreadable. */
    fun labelsAt(x: Float, y: Float): List<String>? {
        labelsAtForTest?.let { return it(x, y) }
        val root = rootInActiveWindow ?: return null
        val out = ArrayList<String>()
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            if (d > 30) return
            val r = android.graphics.Rect(); n.getBoundsInScreen(r)
            if (r.contains(x.toInt(), y.toInt())) {
                out.add(((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "") + " " + (n.viewIdResourceName ?: "")).trim())
            }
            for (i in 0 until n.childCount) { val c = n.getChild(i) ?: continue; try { walk(c, d + 1) } finally { c.recycle() } }
        }
        try { walk(root, 0) } finally { root.recycle() }
        return out
    }

    companion object {
        @Volatile var instance: RoutAccessibilityService? = null
            private set
        val isEnabled: Boolean get() = instance != null

        /** Heuristic only - see ScreenSafety. Fail closed on any match. */
    }

    override fun onServiceConnected() { instance = this }
    internal fun connectForTest() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}


    /** Take a screenshot of the current screen (API 30+) and save it to Pictures/ROUT. */
    fun captureScreenshot(onDone: (Boolean) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < 30) { onDone(false); return }
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
            mainExecutor, object : android.accessibilityservice.AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: android.accessibilityservice.AccessibilityService.ScreenshotResult) {
                    var ok = false
                    try {
                        val bmp = android.graphics.Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        if (bmp != null) {
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "ROUT_" + System.currentTimeMillis() + ".png")
                                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ROUT")
                            }
                            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                contentResolver.openOutputStream(uri)?.use { out ->
                                    bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                }
                                ok = true
                            }
                            bmp.recycle()
                        }
                    } catch (_: Exception) {}
                    onDone(ok)
                }
                override fun onFailure(errorCode: Int) { onDone(false) }
            })
    }

    /** True when the foreground content looks sensitive. Fail closed. */
    fun isSensitiveScreen(): Boolean {
        sensitiveOverrideForTest?.let { return it() }
        val root = rootInActiveWindow ?: return false
        try {
            if (hasPasswordField(root)) return true
            val text = collectText(root, StringBuilder(), 0).toString().lowercase()
            return ScreenSafety.isSensitive(text)
        } finally {
            root.recycle()
        }
    }

    private fun hasPasswordField(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            try { if (hasPasswordField(c)) return true } finally { c.recycle() }
        }
        return false
    }

    private fun collectText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int): StringBuilder {
        if (depth > 24 || out.length > 6000) return out
        node.text?.let { out.append(it).append(' ') }
        node.contentDescription?.let { out.append(it).append(' ') }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            try { collectText(c, out, depth + 1) } finally { c.recycle() }
        }
        return out
    }

    /** On-device screen text for command matching. Null on sensitive screens (fail closed). */
    fun screenTextOnDevice(): String? {
        if (isSensitiveScreen()) return null
        val root = rootInActiveWindow ?: return null
        return try { collectText(root, StringBuilder(), 0).toString().trim().ifBlank { null } }
        finally { root.recycle() }
    }

    fun tapNamedElement(name: String): Boolean {
        if (isSensitiveScreen()) return false
        val root = rootInActiveWindow ?: return false
        try {
            val target = findClickable(root, name.lowercase()) ?: return false
            return try { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
            finally { target.recycle() }
        } finally { root.recycle() }
    }

    private fun findClickable(node: AccessibilityNodeInfo, name: String): AccessibilityNodeInfo? {
        val label = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        if (node.isClickable && node.isEnabled && name.isNotBlank() && label.contains(name)) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            try {
                val hit = findClickable(c, name)
                if (hit != null) return hit
            } finally { c.recycle() }
        }
        return null
    }

    fun scroll(forward: Boolean): Boolean {
        if (isSensitiveScreen()) return false
        val root = rootInActiveWindow ?: return false
        try {
            val node = findScrollable(root) ?: return false
            return try {
                node.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                                   else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            } finally { node.recycle() }
        } finally { root.recycle() }
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            try {
                val hit = findScrollable(c)
                if (hit != null) return hit
            } finally { c.recycle() }
        }
        return null
    }


    // --- 0.7.8 additions: all user-commanded, all fail closed on sensitive screens ---
    fun notifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun quickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    fun lockScreen(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
    fun systemScreenshot(): Boolean =
        android.os.Build.VERSION.SDK_INT >= 28 && performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

    private fun findByLabel(node: AccessibilityNodeInfo, name: String, longClickable: Boolean): AccessibilityNodeInfo? {
        val label = ((node.text?.toString() ?: "") + " " + (node.contentDescription?.toString() ?: "")).lowercase()
        if (node.isEnabled && name.isNotBlank() && label.contains(name) &&
            (if (longClickable) node.isLongClickable else node.isClickable)) return AccessibilityNodeInfo.obtain(node)
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            try { findByLabel(c, name, longClickable)?.let { return it } } finally { c.recycle() }
        }
        return null
    }

    fun longPressNamed(name: String): Boolean {
        if (!readableAndSafe()) return false
        val root = rootInActiveWindow ?: return false
        try {
            val t = findByLabel(root, name.lowercase(), true) ?: return false
            return try { t.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) } finally { t.recycle() }
        } finally { root.recycle() }
    }

    /** Types into the currently focused editable field. Never on sensitive/password screens. */
    fun typeIntoFocused(text: String): Boolean {
        if (!readableAndSafe()) return false
        val root = rootInActiveWindow ?: return false
        try {
            val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            return try {
                if (f.isPassword || !f.isEditable) false else {
                    val b = android.os.Bundle()
                    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                    f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
                }
            } finally { f.recycle() }
        } finally { root.recycle() }
    }

    /** Polls the on-device screen text for [text] up to [timeoutMs]. Nothing leaves the device. */
    suspend fun waitForText(text: String, timeoutMs: Long): Boolean {
        if (!readableAndSafe()) return false
        val end = System.currentTimeMillis() + timeoutMs.coerceIn(500, 10_000)
        while (System.currentTimeMillis() < end) {
            if (screenTextOnDevice()?.lowercase()?.contains(text.lowercase()) == true) return true
            kotlinx.coroutines.delay(300)
        }
        return false
    }

    /** Horizontal/vertical swipe by gesture for carousels and pagers. */
    fun swipe(dir: String): Boolean {
        if (!readableAndSafe()) return false
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat(); val h = m.heightPixels.toFloat()
        val (sx, sy, ex, ey) = when (dir) {
            "left" -> listOf(w * 0.8f, h * 0.5f, w * 0.2f, h * 0.5f)
            "right" -> listOf(w * 0.2f, h * 0.5f, w * 0.8f, h * 0.5f)
            else -> return false
        }
        val path = android.graphics.Path().apply { moveTo(sx, sy); lineTo(ex, ey) }
        val g = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 350)).build()
        return dispatchGesture(g, null, null)
    }


    // --- Bounded coordinate gestures (explicit user numbers only, clamped to the display) ---
    private fun clampX(x: Float) = x.coerceIn(1f, (resources.displayMetrics.widthPixels - 1).toFloat())
    private fun clampY(y: Float) = y.coerceIn(1f, (resources.displayMetrics.heightPixels - 1).toFloat())

    private fun strokePath(pts: List<Pair<Float, Float>>): android.graphics.Path =
        android.graphics.Path().apply {
            moveTo(clampX(pts[0].first), clampY(pts[0].second))
            for (i in 1 until pts.size) lineTo(clampX(pts[i].first), clampY(pts[i].second))
        }

    fun tapXY(x: Float, y: Float, double: Boolean = false): Boolean {
        if (!readableAndSafe()) return false
        if (!pointIsResolvedAndHarmless(x, y)) return false
        val b = android.accessibilityservice.GestureDescription.Builder()
        b.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(strokePath(listOf(x to y)), 0, 60))
        if (double) b.addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(strokePath(listOf(x to y)), 160, 60))
        return dispatchGesture(b.build(), null, null)
    }

    fun dragXY(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        if (!readableAndSafe()) return false
        if (!pointIsResolvedAndHarmless(x1, y1) || !pointIsResolvedAndHarmless(x2, y2)) return false
        val g = android.accessibilityservice.GestureDescription.Builder().addStroke(
            android.accessibilityservice.GestureDescription.StrokeDescription(strokePath(listOf(x1 to y1, x2 to y2)), 0, 500)).build()
        return dispatchGesture(g, null, null)
    }

    /** Two-finger pinch around the screen centre. zoomIn spreads fingers, otherwise pinches them together. */
    fun pinch(zoomIn: Boolean): Boolean {
        if (!readableAndSafe()) return false
        if (android.os.Build.VERSION.SDK_INT < 24) return false
        val m = resources.displayMetrics
        val cx = m.widthPixels / 2f; val cy = m.heightPixels / 2f
        val near = m.widthPixels * 0.08f; val far = m.widthPixels * 0.30f
        val (a, b) = if (zoomIn) near to far else far to near
        val d = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(strokePath(listOf((cx - a) to cy, (cx - b) to cy)), 0, 400))
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(strokePath(listOf((cx + a) to cy, (cx + b) to cy)), 0, 400))
            .build()
        return dispatchGesture(d, null, null)
    }

    /** A coordinate may act only if the node tree resolves something there and no label under it is consequential. */
    fun pointIsResolvedAndHarmless(x: Float, y: Float): Boolean {
        val labels = labelsAt(x, y) ?: return false
        if (labels.isEmpty()) return false
        return labels.none { com.rout.aiassistant.commands.ScreenControl.isSendLike(it) }
    }

    fun describePoint(x: Float, y: Float): String? =
        labelsAt(x, y)?.map { it.trim() }?.lastOrNull { it.isNotBlank() }?.take(40)

    fun displaySize(): Pair<Int, Int> = resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels

    fun back(): Boolean = !isSensitiveScreen() && performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /** Result of one command, for honest user-facing confirmation. */
    sealed class Result {
        data class Ok(val what: String) : Result()
        object BlockedSensitive : Result()
        object NoMatch : Result()
        object ServiceOff : Result()

        val spoken: String
            get() = when (this) {
                is Ok -> "Done - $what"
                BlockedSensitive -> "Ye screen sensitive hai (password, OTP ya payment). Main is screen pe kaam nahi karunga."
                NoMatch -> "Screen pe wo cheez nahi mili."
                ServiceOff -> "Screen control off hai. Settings mein ROUT accessibility on karo."
            }
    }
}
