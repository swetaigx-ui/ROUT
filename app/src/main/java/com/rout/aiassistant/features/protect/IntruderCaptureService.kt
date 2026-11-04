package com.rout.aiassistant.features.protect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.io.File

/**
 * Takes one front-camera photo after failed unlock attempts (opt-in feature).
 * Runs as a foreground service only for the seconds needed to capture.
 */
class IntruderCaptureService : LifecycleService() {
    companion object {
        const val CH = "rout_intruder"
        fun start(ctx: Context) {
            val i = Intent(ctx, IntruderCaptureService::class.java)
            ContextCompat.startForegroundService(ctx, i)
        }
        fun photosDir(ctx: Context) = File(ctx.filesDir, "intruders").apply { mkdirs() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(
            NotificationChannel(CH, "Intruder photo", NotificationManager.IMPORTANCE_MIN))
        val notif: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("ROUT security")
            .setContentText("Intruder photo le raha hai…")
            .setSilent(true).build()
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(71, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(71, notif)

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return START_NOT_STICKY
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val capture = ImageCapture.Builder().build()
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, capture)
                val file = File(photosDir(this), "intruder_${System.currentTimeMillis()}.jpg")
                val out = ImageCapture.OutputFileOptions.Builder(file).build()
                capture.takePicture(out, ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(res: ImageCapture.OutputFileResults) { provider.unbindAll(); stopSelf() }
                        override fun onError(e: ImageCaptureException) { provider.unbindAll(); stopSelf() }
                    })
            } catch (_: Exception) { stopSelf() }
        }, ContextCompat.getMainExecutor(this))
        return START_NOT_STICKY
    }
}
