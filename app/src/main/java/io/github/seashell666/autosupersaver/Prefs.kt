package io.github.seashell666.autosupersaver

import android.content.ContentResolver
import android.provider.Settings

/**
 * 配置统一存在 Settings.System 里：system_server 侧可无权限读取，
 * 设置页侧需要用户授予「修改系统设置」权限即可写入。
 */
object Prefs {
    const val KEY_ENABLED = "ass_enabled"
    const val KEY_THRESHOLD = "ass_threshold"
    const val KEY_COOLDOWN = "ass_cooldown"
    const val KEY_LAST_TRIGGER = "ass_last_trigger"
    const val KEY_COMMAND = "ass_command"
    const val KEY_TEST_AT = "ass_test_at"
    const val KEY_LAST_LOG = "ass_last_log"
    const val KEY_DEBUG_PERCENT = "ass_debug_percent"

    /** MIUI 超级省电模式的系统设置键，写入 1 即触发进入。 */
    const val KEY_MIUI_EXTREME = "EXTREME_POWER_SAVE_MODE_OPEN"

    const val DEFAULT_THRESHOLD = 17
    const val DEFAULT_COOLDOWN = 27
    const val DEFAULT_COMMAND = "settings put system EXTREME_POWER_SAVE_MODE_OPEN 1"

    fun enabled(cr: ContentResolver): Boolean =
        Settings.System.getInt(cr, KEY_ENABLED, 1) == 1

    fun setEnabled(cr: ContentResolver, on: Boolean) {
        Settings.System.putInt(cr, KEY_ENABLED, if (on) 1 else 0)
    }

    fun threshold(cr: ContentResolver): Int =
        Settings.System.getInt(cr, KEY_THRESHOLD, DEFAULT_THRESHOLD).coerceIn(1, 60)

    fun setThreshold(cr: ContentResolver, v: Int) {
        Settings.System.putInt(cr, KEY_THRESHOLD, v.coerceIn(1, 60))
    }

    fun cooldownMinutes(cr: ContentResolver): Int =
        Settings.System.getInt(cr, KEY_COOLDOWN, DEFAULT_COOLDOWN).coerceIn(1, 720)

    fun setCooldown(cr: ContentResolver, v: Int) {
        Settings.System.putInt(cr, KEY_COOLDOWN, v.coerceIn(1, 720))
    }

    fun command(cr: ContentResolver): String =
        Settings.System.getString(cr, KEY_COMMAND).orEmpty().ifBlank { DEFAULT_COMMAND }

    fun setCommand(cr: ContentResolver, v: String) {
        Settings.System.putString(cr, KEY_COMMAND, v)
    }

    fun lastTrigger(cr: ContentResolver): Long =
        Settings.System.getLong(cr, KEY_LAST_TRIGGER, 0L)

    fun setLastTrigger(cr: ContentResolver, v: Long) {
        Settings.System.putLong(cr, KEY_LAST_TRIGGER, v)
    }
}
