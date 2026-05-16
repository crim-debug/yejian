package com.nightshield.screenfilter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启动接收器
 *
 * 在设备启动完成后，检查用户是否开启了"开机自启"选项，
 * 如果是则自动启动屏幕遮罩服务。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "nightshield_prefs"
        private const val KEY_BOOT_ENABLED = "boot_enabled"
        private const val KEY_DIM_LEVEL = "dim_level"
        private const val KEY_FILTER_INTENSITY = "filter_intensity"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            if (prefs.getBoolean(KEY_BOOT_ENABLED, false)) {
                val dimLevel = prefs.getInt(KEY_DIM_LEVEL, 50)
                val filterIntensity = prefs.getInt(KEY_FILTER_INTENSITY, 30)

                val serviceIntent = Intent(context, ScreenFilterService::class.java).apply {
                    putExtra(ScreenFilterService.EXTRA_DIM_LEVEL, dimLevel)
                    putExtra(ScreenFilterService.EXTRA_FILTER_INTENSITY, filterIntensity)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
