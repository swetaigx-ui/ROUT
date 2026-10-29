package com.rout.aiassistant.files

import android.content.Context
import android.content.Intent
import com.rout.aiassistant.control.StepResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class FileOp { OPEN, RENAME, DELETE }

/** What the user-visible Activity must do. The user always picks the file in the system picker; ROUT never searches storage on its own. */
data class FileRequest(val op: FileOp, val hint: String, val newName: String)

/** Pure checks shared by the Activity and the tests. */
object FileRules {
    private val BAD = Regex("[/\\\\:*?\"<>|\\u0000-\\u001f]")
    /** Returns an error message, or null when [name] is a safe single file name. */
    fun invalidName(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Naya naam khali hai."
            n.length > 120 -> "Naya naam bahut lamba hai."
            n == "." || n == ".." -> "Ye naam allowed nahi."
            BAD.containsMatchIn(n) -> "Naam mein / \\ : * ? \" < > | jaise characters nahi ho sakte."
            else -> null
        }
    }
}

/** One file request at a time. The Activity completes [pending]; no result means no action happened. */
object FileBridge {
    @Volatile var pending: CompletableDeferred<StepResult>? = null
    @Volatile var request: FileRequest? = null
    var timeoutMs: Long = 180_000

    /** [launch] starts the picker Activity (injectable for tests). */
    suspend fun run(r: FileRequest, launch: () -> Unit): StepResult {
        if (pending != null) return StepResult(false, "Pehla file kaam abhi khula hai. Use khatam ya cancel karo.")
        val d = CompletableDeferred<StepResult>()
        pending = d; request = r
        return try {
            try { launch() } catch (e: Exception) { return StepResult(false, "File picker khul nahi paya (Android ne background se rok diya ho sakta hai). ROUT ko khol kar dobara bolo.") }
            withTimeout(timeoutMs) { d.await() }
        } catch (e: TimeoutCancellationException) {
            StepResult(false, "File picker se koi jawab nahi aaya, kuch nahi kiya.")
        } finally { pending = null; request = null }
    }

    fun launcher(context: Context): () -> Unit = {
        context.startActivity(Intent(context, FilePickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }
}
