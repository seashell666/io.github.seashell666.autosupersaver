package io.github.seashell666.autosupersaver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private lateinit var enableBox: CheckBox
    private lateinit var thresholdInput: EditText
    private lateinit var cooldownInput: EditText
    private lateinit var commandInput: EditText

    private val handler = Handler(Looper.getMainLooper())

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshStatus(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        loadValues()
        refreshStatus(null)
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onResume() {
        super.onResume()
        loadValues()
        refreshStatus(null)
        scheduleLogRefresh()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Throwable) {
        }
    }

    private fun dp(v: Int): Int = (resources.displayMetrics.density * v).toInt()

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "自动超级省电"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "电量低于阈值且未充电时，自动让系统进入超级省电模式。仅负责进入，退出请手动点系统按钮。\n" +
                    "首次使用请先在系统弹窗里勾选「不再提示」，否则每次进入都要人工确认。"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(6), 0, dp(16))
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(Color.parseColor("#F2F4F5"))
        }
        root.addView(statusText)

        enableBox = CheckBox(this).apply {
            text = "启用自动进入超级省电"
            setPadding(0, dp(16), 0, 0)
        }
        root.addView(enableBox)

        thresholdInput = numberField(root, "触发阈值（电量 ≤ 该值时触发，默认 17）")
        cooldownInput = numberField(root, "冷却时间（分钟，默认 27）")

        root.addView(TextView(this).apply {
            text = "触发命令（默认使用系统省电设置键，可按机型替换）"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(14), 0, dp(4))
        })
        commandInput = EditText(this).apply {
            setTextSize(13f)
            setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
            minLines = 2
            setText(Prefs.DEFAULT_COMMAND)
        }
        root.addView(commandInput)

        root.addView(Button(this).apply {
            text = "保存设置"
            setOnClickListener { save() }
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            setPadding(0, dp(8), 0, dp(8))
        }
        row.addView(Button(this).apply {
            text = "立即测试"
            setOnClickListener { testNow() }
        })
        row.addView(Button(this).apply {
            text = "授予修改系统权限"
            setOnClickListener { requestWriteSettings() }
        })
        row.addView(Button(this).apply {
            text = "打开省电设置"
            setOnClickListener {
                try {
                    startActivity(
                        Intent().setComponent(
                            android.content.ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.powercenter.PowerMainActivity"
                            )
                        )
                    )
                } catch (t: Throwable) {
                    toast("无法打开: $t")
                }
            }
        })
        root.addView(row)

        root.addView(TextView(this).apply {
            text = "运行日志"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(6))
        })
        logText = TextView(this).apply {
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#333333"))
            setTextIsSelectable(true)
        }
        root.addView(logText, ViewGroup.LayoutParams(-1, -2))
        return scroll
    }

    private fun numberField(parent: LinearLayout, label: String): EditText {
        parent.addView(TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(14), 0, dp(4))
        })
        val input = EditText(this).apply {
            setInputType(InputType.TYPE_CLASS_NUMBER)
            setTextSize(16f)
        }
        parent.addView(input)
        return input
    }

    private fun loadValues() {
        val cr = contentResolver
        enableBox.isChecked = Prefs.enabled(cr)
        thresholdInput.setText(Prefs.threshold(cr).toString())
        cooldownInput.setText(Prefs.cooldownMinutes(cr).toString())
        commandInput.setText(Prefs.command(cr))
    }

    private fun save() {
        if (!Settings.System.canWrite(this)) {
            toast("请先授予「修改系统设置」权限")
            requestWriteSettings()
            return
        }
        val cr = contentResolver
        val threshold = thresholdInput.text.toString().toIntOrNull() ?: Prefs.DEFAULT_THRESHOLD
        val cooldown = cooldownInput.text.toString().toIntOrNull() ?: Prefs.DEFAULT_COOLDOWN
        Prefs.setEnabled(cr, enableBox.isChecked)
        Prefs.setThreshold(cr, threshold)
        Prefs.setCooldown(cr, cooldown)
        Prefs.setCommand(cr, commandInput.text.toString().trim())
        toast("已保存")
        refreshStatus(null)
    }

    private fun testNow() {
        if (!Settings.System.canWrite(this)) {
            toast("请先授予「修改系统设置」权限")
            requestWriteSettings()
            return
        }
        Settings.System.putLong(contentResolver, Prefs.KEY_TEST_AT, System.currentTimeMillis())
        toast("已发送测试指令，稍后查看日志")
        handler.postDelayed({ refreshStatus(null) }, 1500)
    }

    private fun requestWriteSettings() {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        } catch (t: Throwable) {
            toast("无法打开权限页: $t")
        }
    }

    private fun refreshStatus(intent: Intent?) {
        val cr = contentResolver
        val percent = batteryPercent(intent)
        val charging = isCharging(intent)
        val last = Prefs.lastTrigger(cr)
        val lastText = if (last > 0) {
            val min = (System.currentTimeMillis() - last) / 60_000L
            "${min} 分钟前"
        } else "从未触发"
        val injected = Settings.System.getString(cr, Prefs.KEY_LAST_LOG).orEmpty().isNotEmpty()
        statusText.text = buildString {
            append("当前电量：").append(percent).append("%")
            append("　").append(if (charging) "充电中" else "未充电")
            append("\n上次触发：").append(lastText)
            append("\n模块状态：").append(if (injected) "已在系统进程运行" else "未检测到运行日志")
        }
        logText.text = Settings.System.getString(cr, Prefs.KEY_LAST_LOG).orEmpty().ifEmpty { "（暂无日志）" }
    }

    private fun scheduleLogRefresh() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            refreshStatus(null)
            scheduleLogRefresh()
        }, 3000)
    }

    private fun batteryPercent(intent: Intent?): Int {
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0 && scale > 0) return level * 100 / scale
        }
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (t: Throwable) {
            0
        }
    }

    private fun isCharging(intent: Intent?): Boolean {
        if (intent != null) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            return plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
        return false
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
