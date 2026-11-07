package io.github.seashell666.autosupersaver

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行在 system_server（或 powerkeeper）进程内的核心逻辑。
 */
object AutoSaver {
    private const val TAG = "AutoSuperSaver"
    private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA)

    @Volatile
    private var started = false

    var logger: ((String) -> Unit)? = null

    fun start(ctx: Context) {
        if (started) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ctx.registerReceiver(receiver, filter)

        registerTestObserver(ctx)
        startPolling(ctx)
        started = true

        AutoSaver.logger?.invoke("模块已注入 ${ctx.packageName}")
        writeLog(
            ctx.contentResolver,
            "已启动，当前电量 ${currentPercent(ctx)}%，冷却 ${Prefs.cooldownMinutes(ctx.contentResolver)} 分钟"
        )
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val percent = if (level >= 0 && scale > 0) level * 100 / scale else currentPercent(context)
                val charging = plugged != 0 ||
                        status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                evaluate(context, percent, charging)
            } catch (t: Throwable) {
                writeLog(context.contentResolver, "处理电量事件异常: $t")
            }
        }
    }

    fun evaluate(ctx: Context, percent: Int, charging: Boolean) {
        val cr = ctx.contentResolver
        if (!Prefs.enabled(cr)) return
        if (charging) return

        val threshold = Prefs.threshold(cr)
        if (percent > threshold) return

        val now = System.currentTimeMillis()
        val last = Prefs.lastTrigger(cr)
        val cooldownMs = Prefs.cooldownMinutes(cr) * 60_000L
        if (last > 0 && now - last < cooldownMs) {
            val left = (cooldownMs - (now - last)) / 60_000L
            writeLog(cr, "电量 $percent% 达到阈值，但冷却中（剩余 $left 分钟）")
            return
        }

        fire(ctx, "电量 ${percent}% ≤ ${threshold}% 且未充电")
    }

    fun fire(ctx: Context, reason: String) {
        val cr = ctx.contentResolver
        var ok = false
        var detail: String

        detail = try {
            Settings.System.putInt(cr, Prefs.KEY_MIUI_EXTREME, 1)
            ok = Settings.System.getInt(cr, Prefs.KEY_MIUI_EXTREME, 0) == 1
            "写入 ${Prefs.KEY_MIUI_EXTREME}=1 ${if (ok) "成功" else "未生效"}"
        } catch (t: Throwable) {
            "写入失败: $t"
        }

        if (!ok) {
            val command = Prefs.command(cr)
            val result = Trigger.run(command)
            ok = result.ok
            detail = "$detail；命令: $command → ${result.output.take(120)}"
        }

        Prefs.setLastTrigger(cr, System.currentTimeMillis())
        writeLog(cr, "触发（$reason）\n$detail")
    }

    private fun registerTestObserver(ctx: Context) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                try {
                    val v = Settings.System.getLong(ctx.contentResolver, Prefs.KEY_TEST_AT, 0L)
                    if (v > 0 && v != lastTestAt) {
                        lastTestAt = v
                        fire(ctx, "手动测试")
                    }
                } catch (_: Throwable) {
                }
            }
        }
        try {
            lastTestAt = Settings.System.getLong(ctx.contentResolver, Prefs.KEY_TEST_AT, 0L)
            ctx.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Prefs.KEY_TEST_AT), false, observer
            )
        } catch (t: Throwable) {
            writeLog(ctx.contentResolver, "注册测试监听失败: $t")
        }
    }

    @Volatile
    private var lastTestAt = 0L

    fun currentPercent(ctx: Context): Int {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (t: Throwable) {
            0
        }
    }

    fun isChargingNow(ctx: Context): Boolean {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.isCharging
        } catch (t: Throwable) {
            false
        }
    }

    /** 兜底轮询：广播在某些机型/场景下不一定送达 system_server，每分钟校正一次。 */
    private fun startPolling(ctx: Context) {
        val handler = Handler(Looper.getMainLooper())
        val task = object : Runnable {
            override fun run() {
                try {
                    if (Prefs.enabled(ctx.contentResolver)) {
                        val real = currentPercent(ctx)
                        val pct = Settings.System.getInt(
                            ctx.contentResolver, Prefs.KEY_DEBUG_PERCENT, 0
                        ).takeIf { it > 0 } ?: real
                        val chg = isChargingNow(ctx)
                        if (pct <= Prefs.threshold(ctx.contentResolver)) {
                            AutoSaver.logger?.invoke("轮询：$pct% charging=$chg")
                        }
                        evaluate(ctx, pct, chg)
                    }
                } catch (_: Throwable) {
                }
                handler.postDelayed(this, 60_000L)
            }
        }
        handler.postDelayed(task, 60_000L)
    }

    fun writeLog(cr: ContentResolver, msg: String) {
        val line = "${timeFmt.format(Date())}  $msg"
        AutoSaver.logger?.invoke(line)
        try {
            val old = Settings.System.getString(cr, Prefs.KEY_LAST_LOG).orEmpty()
            val merged = (line + "\n" + old).lines().take(20).joinToString("\n")
            Settings.System.putString(cr, Prefs.KEY_LAST_LOG, merged)
        } catch (_: Throwable) {
        }
    }
}
