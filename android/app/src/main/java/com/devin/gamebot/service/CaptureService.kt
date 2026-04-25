package com.devin.gamebot.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.devin.gamebot.GameBotApp
import com.devin.gamebot.MainActivity
import com.devin.gamebot.R
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground service that owns the [MediaProjection] and writes captured frames to disk.
 *
 * Phase 0 behavior:
 *   - Acquire MediaProjection from the result intent.
 *   - Capture one frame every [CAPTURE_INTERVAL_MS] and save the latest to
 *     `Pictures/AIGameBot/latest.jpg`.
 *   - User opens the file to verify whether `FLAG_SECURE` blocks capture on the target game.
 */
class CaptureService : Service() {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (resultCode == 0 || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()

        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(resultCode, data).also { mp ->
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped")
                    cleanup()
                    stopSelf()
                }
            }, null)
        }

        startVirtualDisplay()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, GameBotApp.CHANNEL_CAPTURE)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun startVirtualDisplay() {
        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    saveLatest(cropped)
                    bitmap.recycle()
                    cropped.recycle()
                } catch (t: Throwable) {
                    Log.w(TAG, "Capture frame failed", t)
                } finally {
                    image.close()
                }
            }, null)
        }

        virtualDisplay = projection?.createVirtualDisplay(
            "AIGameBot",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null,
        )
    }

    private fun saveLatest(bmp: Bitmap) {
        try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "AIGameBot"
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "latest.jpg")
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            // Throttled log, otherwise every frame fills the log.
            if (System.currentTimeMillis() % 2000 < 100) {
                Log.i(TAG, "Saved frame to ${file.absolutePath}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Save failed", t)
        }
    }

    private fun cleanup() {
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        virtualDisplay = null
        imageReader = null
        projection = null
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CaptureService"
        private const val NOTIF_ID = 1001
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val CAPTURE_INTERVAL_MS = 500L

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, CaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaptureService::class.java))
        }
    }
}
