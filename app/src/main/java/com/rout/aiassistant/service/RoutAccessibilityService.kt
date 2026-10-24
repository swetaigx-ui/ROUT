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

    companion object {
        @Volatile var instance: RoutAccessibilityService? = null
            private set
        val isEnabled: Boolean get() = instance != null

        /** Heuristic only - see ScreenSafety. Fail closed on any match. */
    }

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { if (instance === this) instance = null }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** True when the foreground content looks sensitive. Fail closed. */
    fun isSensitiveScreen(): Boolean {
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
