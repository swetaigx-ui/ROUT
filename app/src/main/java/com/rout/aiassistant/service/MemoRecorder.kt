package com.rout.aiassistant.service

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Voice memos: record/stop/list/play. Files stay in app-private storage,
 * never uploaded. One recording at a time.
 */
object MemoRecorder {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var player: MediaPlayer? = null

    val isRecording: Boolean get() = recorder != null

    private fun dir(context: Context): File = File(context.filesDir, "memos").apply { mkdirs() }

    fun start(context: Context): File? {
        stop(discard = true)
        val f = File(dir(context), "memo-" + System.currentTimeMillis() + ".m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            recorder = r; currentFile = f
            f
        } catch (e: Exception) {
            try { r.release() } catch (_: Exception) {}
            null
        }
    }

    /** Stop the current recording; returns the saved file, or null when discarded/none. */
    fun stop(discard: Boolean = false): File? {
        val r = recorder ?: return null
        recorder = null
        val f = currentFile
        currentFile = null
        try { r.stop() } catch (_: Exception) { if (f != null && discard) f.delete(); return null }
        try { r.release() } catch (_: Exception) {}
        if (discard) { f?.delete(); return null }
        return f
    }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "m4a" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun play(context: Context, file: File, onDone: () -> Unit = {}): Boolean {
        stopPlayback()
        return try {
            val p = MediaPlayer()
            p.setDataSource(file.absolutePath)
            p.setOnCompletionListener { onDone() }
            p.prepare(); p.start()
            player = p
            true
        } catch (e: Exception) { false }
    }

    fun stopPlayback() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
    }
}
