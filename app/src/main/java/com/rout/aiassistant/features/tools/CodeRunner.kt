package com.rout.aiassistant.features.tools

import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

/**
 * Code Studio runner: executes JavaScript on-device via Rhino (free, offline).
 * print()/console.log collect into the output pane. 5s safety timeout.
 */
object CodeRunner {
    data class Result(val output: String, val error: String?)

    fun runJs(code: String): Result {
        val out = StringBuilder()
        val ctx = Context.enter()
        try {
            ctx.optimizationLevel = -1 // required on Android (no bytecode gen)
            val scope = ctx.initStandardObjects()
            val printFn = object : org.mozilla.javascript.BaseFunction() {
                override fun call(cx: Context, s: org.mozilla.javascript.Scriptable, thisObj: org.mozilla.javascript.Scriptable?, args: Array<out Any?>): Any? {
                    out.append(args.joinToString(" ") { Context.toString(it) }).append("\n")
                    return null
                }
            }
            ScriptableObject.putProperty(scope, "print", printFn)
            ScriptableObject.putProperty(scope, "console", mapOf("log" to printFn).let { m ->
                val o = ctx.newObject(scope); ScriptableObject.putProperty(o, "log", printFn); o
            })
            val thread = Thread {
                try {
                    val res = ctx.evaluateString(scope, code, "code.js", 1, null)
                    if (res != org.mozilla.javascript.Undefined.instance && res != null)
                        out.append(Context.toString(res))
                } catch (e: Exception) {
                    out.append("Error: ").append(e.message ?: e.javaClass.simpleName)
                }
            }
            thread.isDaemon = true
            thread.start()
            thread.join(5000)
            if (thread.isAlive) {
                thread.interrupt()
                return Result(out.toString(), "Timeout: code 5 second se zyada chala - rok diya.")
            }
            return Result(out.toString().ifBlank { "(koi output nahi - print() use karo)" }, null)
        } catch (e: Exception) {
            return Result(out.toString(), e.message)
        } finally {
            try { Context.exit() } catch (_: Exception) {}
        }
    }
}
