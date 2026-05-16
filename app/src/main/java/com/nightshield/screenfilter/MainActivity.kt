package com.nightshield.screenfilter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

/**
 * 主界面 Activity
 *
 * 功能：
 * 1. 主开关：启动/停止遮罩服务
 * 2. 亮度滑块：调节遮罩深度（0~100）
 * 3. 蓝光过滤滑块：调节暖色强度（0~100）
 * 4. 开机自启开关
 * 5. 悬浮窗权限引导
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "nightshield_prefs"
        private const val KEY_DIM_LEVEL = "dim_level"
        private const val KEY_FILTER_INTENSITY = "filter_intensity"
        private const val KEY_BOOT_ENABLED = "boot_enabled"
    }

    private lateinit var prefs: SharedPreferences

    // UI 组件
    private lateinit var switchMain: SwitchMaterial
    private lateinit var seekBarDim: SeekBar
    private lateinit var textDimValue: TextView
    private lateinit var seekBarFilter: SeekBar
    private lateinit var textFilterValue: TextView
    private lateinit var switchBoot: SwitchMaterial
    private lateinit var textStatus: TextView
    private lateinit var textPermissionHint: TextView

    // 使用 Activity Result API 替代已废弃的 startActivityForResult
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        updatePermissionHint()
        if (canDrawOverlays()) {
            switchMain.isChecked = true
            startFilterService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 强制深色主题（夜间应用）
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        initViews()
        loadSavedSettings()
        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionHint()
        updateServiceStatus()
    }

    /**
     * 初始化视图组件
     */
    private fun initViews() {
        switchMain = findViewById(R.id.switch_main)
        seekBarDim = findViewById(R.id.seekbar_dim)
        textDimValue = findViewById(R.id.text_dim_value)
        seekBarFilter = findViewById(R.id.seekbar_filter)
        textFilterValue = findViewById(R.id.text_filter_value)
        switchBoot = findViewById(R.id.switch_boot)
        textStatus = findViewById(R.id.text_status)
        textPermissionHint = findViewById(R.id.text_permission_hint)

        // 主开关
        switchMain.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startFilterService()
            } else {
                stopFilterService()
            }
        }

        // 亮度滑块
        seekBarDim.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val dimLevel = 100 - progress // 反转：滑块向右=更亮，向左=更暗
                textDimValue.text = "${progress}%"
                prefs.edit { putInt(KEY_DIM_LEVEL, dimLevel) }
                if (ScreenFilterService.isRunning) {
                    updateService()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 蓝光过滤滑块
        seekBarFilter.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                textFilterValue.text = "${progress}%"
                prefs.edit { putInt(KEY_FILTER_INTENSITY, progress) }
                if (ScreenFilterService.isRunning) {
                    updateService()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 开机自启开关
        switchBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(KEY_BOOT_ENABLED, isChecked) }
        }
    }

    /**
     * 加载保存的设置
     */
    private fun loadSavedSettings() {
        val dimLevel = prefs.getInt(KEY_DIM_LEVEL, 50)
        val filterIntensity = prefs.getInt(KEY_FILTER_INTENSITY, 30)
        val bootEnabled = prefs.getBoolean(KEY_BOOT_ENABLED, false)

        seekBarDim.progress = 100 - dimLevel
        textDimValue.text = "${100 - dimLevel}%"
        seekBarFilter.progress = filterIntensity
        textFilterValue.text = "${filterIntensity}%"
        switchBoot.isChecked = bootEnabled
    }

    /**
     * 检查并更新权限提示
     */
    private fun updatePermissionHint() {
        if (canDrawOverlays()) {
            textPermissionHint.text = getString(R.string.permission_overlay_granted)
            textPermissionHint.setOnClickListener(null)
        } else {
            textPermissionHint.text = getString(R.string.permission_overlay_required_click)
            textPermissionHint.setOnClickListener {
                requestOverlayPermission()
            }
        }
    }

    /**
     * 更新服务状态显示
     */
    private fun updateServiceStatus() {
        if (ScreenFilterService.isRunning) {
            switchMain.isChecked = true
            textStatus.text = getString(R.string.filter_running)
        } else {
            switchMain.isChecked = false
            textStatus.text = getString(R.string.filter_stopped)
        }
    }

    /**
     * 检查是否有悬浮窗权限
     */
    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    /**
     * 请求悬浮窗权限（使用 Activity Result API）
     */
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlays()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    /**
     * 启动遮罩服务
     */
    private fun startFilterService() {
        if (!canDrawOverlays()) {
            switchMain.isChecked = false
            showPermissionDialog()
            return
        }

        // 请求通知权限（Android 13+）
        requestNotificationPermission()

        val dimLevel = prefs.getInt(KEY_DIM_LEVEL, 50)
        val filterIntensity = prefs.getInt(KEY_FILTER_INTENSITY, 30)

        val intent = Intent(this, ScreenFilterService::class.java).apply {
            putExtra(ScreenFilterService.EXTRA_DIM_LEVEL, dimLevel)
            putExtra(ScreenFilterService.EXTRA_FILTER_INTENSITY, filterIntensity)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        updateServiceStatus()
        Toast.makeText(this, R.string.filter_running, Toast.LENGTH_SHORT).show()
    }

    /**
     * 停止遮罩服务
     */
    private fun stopFilterService() {
        val intent = Intent(this, ScreenFilterService::class.java)
        stopService(intent)
        updateServiceStatus()
        Toast.makeText(this, R.string.filter_stopped, Toast.LENGTH_SHORT).show()
    }

    /**
     * 更新服务参数（不重启服务）
     */
    private fun updateService() {
        val dimLevel = prefs.getInt(KEY_DIM_LEVEL, 50)
        val filterIntensity = prefs.getInt(KEY_FILTER_INTENSITY, 30)

        val intent = Intent(this, ScreenFilterService::class.java).apply {
            action = ScreenFilterService.ACTION_UPDATE
            putExtra(ScreenFilterService.EXTRA_DIM_LEVEL, dimLevel)
            putExtra(ScreenFilterService.EXTRA_FILTER_INTENSITY, filterIntensity)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * 请求通知权限
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!notificationManager.areNotificationsEnabled()) {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
                startActivity(intent)
            }
        }
    }

    /**
     * 显示权限引导对话框
     */
    private fun showPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_overlay_required)
            .setMessage(R.string.permission_overlay_message)
            .setPositiveButton(R.string.go_to_settings) { _, _ ->
                requestOverlayPermission()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
