package com.example.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SathiScreenShareService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var captureWidth = 720
    private var captureHeight = 1280
    private var captureDensity = 320

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post {
                releaseProjection()
                stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            releaseProjection()
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode == Activity.RESULT_OK && resultData != null) {
                startForegroundSafely()
                startScreenCapture(resultCode, resultData)
            } else {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSafely() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sathi Live Screen Share",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SathiScreenShareService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("সাথী স্ক্রিন শেয়ার চালু আছে")
            .setContentText("সাথী আপনার ফোনের স্ক্রিন দেখে সাহায্য করতে প্রস্তুত")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(openAppIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "বন্ধ করুন", stopIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startScreenCapture(resultCode: Int, resultData: Intent) {
        releaseProjection()

        val metrics = resources.displayMetrics
        val srcWidth = metrics.widthPixels.coerceAtLeast(720)
        val srcHeight = metrics.heightPixels.coerceAtLeast(1280)
        val scale = if (srcWidth > 720) 720f / srcWidth.toFloat() else 1f
        captureWidth = (srcWidth * scale).toInt().coerceAtLeast(360)
        captureHeight = (srcHeight * scale).toInt().coerceAtLeast(640)
        captureDensity = metrics.densityDpi

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        val projection = mgr?.getMediaProjection(resultCode, resultData)
        if (projection == null) {
            stopSelf()
            return
        }

        captureThread = HandlerThread("SathiScreenCaptureThread").apply { start() }
        val handler = Handler(captureThread!!.looper)
        captureHandler = handler

        projection.registerCallback(projectionCallback, mainHandler)
        mediaProjection = projection

        createVirtualDisplaySurface(projection, handler)
        _isSharing.value = true
    }

    private fun createVirtualDisplaySurface(projection: MediaProjection, handler: Handler) {
        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader ->
                var image: Image? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        imageToBitmap(image)?.let { bmp ->
                            val previous = latestFrameRef.getAndSet(bmp)
                            if (previous != null && previous !== bmp && !previous.isRecycled) {
                                // Let GC reclaim older unused frames smoothly
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Frame capture warning", e)
                } finally {
                    try {
                        image?.close()
                    } catch (_: Exception) {
                    }
                }
            }, handler)
        }

        virtualDisplay = projection.createVirtualDisplay(
            "SathiScreenShare",
            captureWidth,
            captureHeight,
            captureDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + (rowPadding / pixelStride.coerceAtLeast(1))
        val rawBitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        rawBitmap.copyPixelsFromBuffer(buffer)
        return if (paddedWidth != image.width) {
            Bitmap.createBitmap(rawBitmap, 0, 0, image.width, image.height).also {
                rawBitmap.recycle()
            }
        } else {
            rawBitmap
        }
    }

    private fun releaseProjection() {
        _isSharing.value = false
        latestFrameRef.set(null)
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null

        try {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        } catch (_: Exception) {
        }
        mediaProjection = null

        try {
            captureThread?.quitSafely()
        } catch (_: Exception) {
        }
        captureThread = null
        captureHandler = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProjection()
    }

    companion object {
        private const val TAG = "SathiScreenShare"
        private const val CHANNEL_ID = "sathi_screen_share_channel"
        private const val NOTIFICATION_ID = 7862

        const val ACTION_START = "com.example.overlay.SCREEN_SHARE_START"
        const val ACTION_STOP = "com.example.overlay.SCREEN_SHARE_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private val _isSharing = MutableStateFlow(false)
        val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

        private val latestFrameRef = AtomicReference<Bitmap?>(null)

        fun startService(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, SathiScreenShareService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, SathiScreenShareService::class.java))
        }

        suspend fun captureLatestFrame(): Bitmap? = withContext(Dispatchers.Default) {
            var frame = latestFrameRef.get()
            var waited = 0
            while ((frame == null || frame.isRecycled) && _isSharing.value && waited < 10) {
                delay(120L)
                frame = latestFrameRef.get()
                waited++
            }
            val safe = frame?.takeIf { !it.isRecycled } ?: return@withContext null
            try {
                safe.copy(Bitmap.Config.ARGB_8888, false)
            } catch (_: Exception) {
                null
            }
        }
    }
}
