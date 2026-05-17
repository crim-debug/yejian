package com.nightshield.screenfilter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat

/**
 * 屏幕遮罩服务
 *
 * 核心原理：通过 WindowManager 创建全屏半透明遮罩层，
 * 叠加在所有应用之上，实现比系统最低亮度更暗的效果。
 * 同时支持蓝光过滤（暖色调遮罩）。
 */
class ScreenFilterService : Service() {

    companion object {
        const val CHANNEL_ID = "screen_filter_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "NightShield"

        // Intent 参数键
        const val EXTRA_DIM_LEVEL = "dim_level"       // 0~100，0=最暗，100=无遮罩
        const val EXTRA_FILTER_INTENSITY = "filter_intensity" // 0~100，0=无过滤，100=最强暖色
        const val EXTRA_ACTION = "action"
        const val ACTION_UPDATE = "update"
        const val ACTION_STOP = "stop"

        // 运行时状态
        var isRunning = false
            private set
        var currentDimLevel = 50    // 默认中等遮罩
        var currentFilterIntensity = 30 // 默认轻度蓝光过滤
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var isOverlayShown = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Log.d(TAG, "Service onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, dimLevel=${intent?.getIntExtra(EXTRA_DIM_LEVEL, -1)}, filterIntensity=${intent?.getIntExtra(EXTRA_FILTER_INTENSITY, -1)}")
        
        when {
            intent == null -> {
                // 服务被系统重启，使用保存的状态
                startForegroundAndShowOverlay()
            }
            intent.action == ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            intent.hasExtra(EXTRA_DIM_LEVEL) || intent.hasExtra(EXTRA_FILTER_INTENSITY) -> {
                intent.getIntExtra(EXTRA_DIM_LEVEL, currentDimLevel).let {
                    currentDimLevel = it
                }
                intent.getIntExtra(EXTRA_FILTER_INTENSITY, currentFilterIntensity).let {
                    currentFilterIntensity = it
                }
                if (isOverlayShown) {
                    updateOverlay()
                } else {
                    showOverlay()
                }
                updateNotification()
            }
            else -> {
                startForegroundAndShowOverlay()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        removeOverlay()
        isRunning = false
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * 启动前台服务并显示遮罩
     */
    private fun startForegroundAndShowOverlay() {
        startForeground(NOTIFICATION_ID, buildNotification())
        showOverlay()
        isRunning = true
        Log.d(TAG, "Foreground service started, isRunning=$isRunning")
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 构建通知
     */
    private fun buildNotification(): Notification {
        // 点击通知打开主界面
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 关闭按钮
        val stopIntent = Intent(this, ScreenFilterService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dimPercent = 100 - currentDimLevel
        val contentText = if (currentFilterIntensity > 0) {
            getString(R.string.notification_dim_text, dimPercent) +
                    getString(R.string.notification_filter_text, currentFilterIntensity)
        } else {
            getString(R.string.notification_dim_text, dimPercent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.turn_off),
                stopPendingIntent
            )
            .build()
    }

    /**
     * 更新通知内容
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    /**
     * 显示遮罩层
     */
    private fun showOverlay() {
        if (isOverlayShown && overlayView != null) {
            updateOverlay()
            return
        }

        val color = calculateFilterColor()
        Log.d(TAG, "showOverlay: color=0x${Integer.toHexString(color)}, dimLevel=$currentDimLevel, filterIntensity=$currentFilterIntensity")

        overlayView = FrameLayout(this).apply {
            setBackgroundColor(color)
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
            isOverlayShown = true
            Log.d(TAG, "Overlay added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay: ${e.message}")
            isOverlayShown = false
            overlayView = null
        }
    }

    /**
     * 更新遮罩颜色
     */
    private fun updateOverlay() {
        val color = calculateFilterColor()
        Log.d(TAG, "updateOverlay: color=0x${Integer.toHexString(color)}")
        overlayView?.setBackgroundColor(color)
    }

    /**
     * 移除遮罩层
     */
    private fun removeOverlay() {
        try {
            overlayView?.let {
                windowManager?.removeView(it)
                Log.d(TAG, "Overlay removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove overlay: ${e.message}")
        }
        overlayView = null
        isOverlayShown = false
    }

    /**
     * 计算遮罩颜色
     *
     * @return ARGB 颜色值
     */
    private fun calculateFilterColor(): Int {
        // dimLevel: 0 = 最暗(全黑遮罩), 100 = 无遮罩
        // 转换为 alpha: dimLevel=0 → alpha=200(很暗), dimLevel=100 → alpha=0(透明)
        val dimAlpha = ((100 - currentDimLevel) / 100f * 200).toInt().coerceIn(0, 255)

        // filterIntensity: 0 = 无过滤, 100 = 最强暖色
        // 暖色 RGB: (255, 180, 80) - 琥珀色
        val filterRatio = currentFilterIntensity / 100f

        // 混合黑色和暖色
        val r = (255 * filterRatio).toInt()
        val g = (180 * filterRatio).toInt()
        val b = (80 * filterRatio).toInt()

        val color = android.graphics.Color.argb(dimAlpha, r, g, b)
        Log.d(TAG, "calculateFilterColor: dimAlpha=$dimAlpha, r=$r, g=$g, b=$b, color=0x${Integer.toHexString(color)}")
        return color
    }
}
