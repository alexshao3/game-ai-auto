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
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.devin.gamebot.GameBotApp
import com.devin.gamebot.MainActivity
import com.devin.gamebot.R
import com.devin.gamebot.data.repo.RecordingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service that owns a [MediaProjection] for the duration of a recording
 * session and saves frames at a controlled FPS into the session's frames directory.
 *
 * Phase 1 design notes:
 *   - The screen is mirrored into an [ImageReader]. Because the user plays the game
 *     normally during recording, we throttle frame capture instead of taking every
 *     frame the GPU produces — most game animations are visually redundant for
 *     intent extraction.
 *   - Frames are downscaled by [DOWNSCALE_FACTOR] before JPEG encoding to keep
 *     network payload manageable when sending to the backend.
 *   - The service stops itself when the user taps the notification or the
 *     companion stop intent fires.
 */
class RecorderService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var captureJob: Job? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val latestFrame = AtomicReference<Bitmap?>(null)
    private val sessionRef = AtomicReference<RecordingSessionContext?>(null)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop requested")
                stopRecordingAndSelf()
                return START_NOT_STICKY
            }
            else -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
                val sessionId = intent?.getLongExtra(EXTRA_SESSION_ID, -1L) ?: -1L
                val framesDir = intent?.getStringExtra(EXTRA_FRAMES_DIR)
                if (resultCode == 0 || data == null || sessionId < 0 || framesDir == null) {
                    Log.w(TAG, "Missing required extras; stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startRecording(resultCode, data, sessionId, File(framesDir))
            }
        }
        return START_STICKY
    }

    private fun startRecording(resultCode: Int, data: Intent, sessionId: Long, framesDir: File) {
        framesDir.mkdirs()
        startForegroundCompat()

        val mpm = getSystemService(MediaProjectionManager::class.java)
        val mp = mpm.getMediaProjection(resultCode, data)
        projection = mp
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.i(TAG, "MediaProjection stopped")
                stopRecordingAndSelf()
            }
        }, null)

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * width
                    val tmp = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888,
                    )
                    tmp.copyPixelsFromBuffer(plane.buffer)
                    val cropped = if (rowPadding == 0) tmp else
                        Bitmap.createBitmap(tmp, 0, 0, width, height).also { tmp.recycle() }
                    val previous = latestFrame.getAndSet(cropped)
                    previous?.recycle()
                } catch (t: Throwable) {
                    Log.w(TAG, "ImageReader handler failed", t)
                } finally {
                    image.close()
                }
            }, null)
        }

        virtualDisplay = mp.createVirtualDisplay(
            "AIGameBotRecorder",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            null,
        )

        sessionRef.set(RecordingSessionContext(sessionId, framesDir, System.currentTimeMillis()))
        startCaptureLoop()
    }

    private fun startCaptureLoop() {
        captureJob?.cancel()
        captureJob = scope.launch {
            val intervalMs = (1000L / TARGET_FPS).coerceAtLeast(100L)
            var ordinal = 0
            val ctx = sessionRef.get() ?: return@launch
            val repo = RecordingRepository(GameBotApp.appDatabase(applicationContext))
            while (isActive) {
                val frame = latestFrame.getAndSet(null)
                if (frame != null) {
                    val saved = saveBitmap(frame, ctx.framesDir, ordinal)
                    frame.recycle()
                    if (saved != null) {
                        runCatching {
                            repo.saveFrame(
                                sessionId = ctx.sessionId,
                                ordinal = ordinal,
                                tOffsetMs = System.currentTimeMillis() - ctx.startMs,
                                file = saved,
                            )
                        }.onFailure { Log.w(TAG, "saveFrame failed", it) }
                        ordinal++
                    }
                }
                delay(intervalMs)
            }
        }
    }

    private fun saveBitmap(bmp: Bitmap, dir: File, ordinal: Int): File? = try {
        val target = File(dir, "frame_${"%05d".format(ordinal)}.jpg")
        FileOutputStream(target).use { out ->
            val downscaled = if (DOWNSCALE_FACTOR > 1) {
                Bitmap.createScaledBitmap(
                    bmp,
                    bmp.width / DOWNSCALE_FACTOR,
                    bmp.height / DOWNSCALE_FACTOR,
                    true,
                )
            } else bmp
            downscaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            if (downscaled !== bmp) downscaled.recycle()
        }
        target
    } catch (t: Throwable) {
        Log.w(TAG, "Save bitmap failed", t)
        null
    }

    private fun stopRecordingAndSelf() {
        captureJob?.cancel()
        captureJob = null
        try { virtualDisplay?.release() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { projection?.stop() } catch (_: Throwable) {}
        virtualDisplay = null
        imageReader = null
        projection = null
        latestFrame.getAndSet(null)?.recycle()
        sessionRef.set(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundCompat() {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = Intent(this, RecorderService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification: Notification = NotificationCompat.Builder(this, GameBotApp.CHANNEL_CAPTURE)
            .setContentTitle("AI Game Bot — Recording")
            .setContentText("Tap to open. Quay video minh hoạ task của bạn rồi bấm Stop.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPi)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class RecordingSessionContext(
        val sessionId: Long,
        val framesDir: File,
        val startMs: Long,
    )

    companion object {
        private const val TAG = "RecorderService"
        private const val NOTIF_ID = 2001
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_FRAMES_DIR = "frames_dir"
        const val ACTION_STOP = "com.devin.gamebot.action.STOP_RECORDING"
        private const val TARGET_FPS = 2
        private const val DOWNSCALE_FACTOR = 2

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            sessionId: Long,
            framesDir: File,
        ) {
            val intent = Intent(context, RecorderService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_FRAMES_DIR, framesDir.absolutePath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, RecorderService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
