package com.rout.aiassistant.features.protect

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build

/**
 * Phone Dhundo: rings the phone at max volume + flashes torch until stopped.
 * Triggered by SMS command ("<secret> dhundo"), a ROUT voice command, or the in-app button.
 */
object FindPhoneController {
    @Volatile private var player: MediaPlayer? = null
    @Volatile private var flashing = false
    private val handler = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        stopSoundOnly() // restart cleanly
        val appCtx = ctx.applicationContext
        try {
            val am = appCtx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.getStreamMaxVolume(AudioManager.STREAM_ALARM).let { am.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) }
            am.getStreamMaxVolume(AudioManager.STREAM_RING).let { am.setStreamVolume(AudioManager.STREAM_RING, it, 0) }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(appCtx, uri)
                isLooping = true
                prepare(); start()
            }
        } catch (_: Exception) {}
        try {
            val vib = appCtx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600), 0))
            else @Suppress("DEPRECATION") vib.vibrate(600)
        } catch (_: Exception) {}
        // torch strobe
        flashing = true
        val cm = appCtx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var on = false
        val toggle = object : Runnable {
            override fun run() {
                if (!flashing) return
                try {
                    on = !on
                    cm.cameraIdList.firstOrNull()?.let { cm.setTorchMode(it, on) }
                } catch (_: Exception) { flashing = false; return }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(toggle)
    }

    fun stop(ctx: Context) {
        stopSoundOnly()
        flashing = false
        try {
            val cm = ctx.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.firstOrNull()?.let { cm.setTorchMode(it, false) }
        } catch (_: Exception) {}
        try {
            val vib = ctx.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vib.cancel()
        } catch (_: Exception) {}
    }

    private fun stopSoundOnly() {
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        player = null
    }

    fun isRinging() = player != null
}
