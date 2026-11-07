package io.github.seashell666.autosupersaver.xposed

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.seashell666.autosupersaver.AutoSaver
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * libxposed API 102 入口。
 *
 * system_server 里模块加载得比 ActivityThread 就绪更早，因此这里用
 * "延迟 + 重试" 的方式等系统上下文可用后，再启动电量监听。
 */
class ModuleEntry : XposedModule() {

    private var startScheduled = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        log(Log.INFO, TAG, "loaded in ${param.processName} (systemServer=${param.isSystemServer})")
        if (param.isSystemServer) scheduleStart()
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        log(Log.INFO, TAG, "onSystemServerStarting")
        scheduleStart()
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!param.packageName.contains("powerkeeper")) return
        try {
            val appClass = Class.forName("android.app.Application", false, param.classLoader)
            val attach = appClass.getDeclaredMethod("attach", Context::class.java)
            hook(attach).setPriority(PRIORITY_DEFAULT).intercept { chain ->
                val result = chain.proceed()
                try {
                    val ctx = chain.getArg(0) as Context
                    AutoSaver.logger = { msg -> log(Log.INFO, TAG, msg) }
                    AutoSaver.start(ctx)
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "powerkeeper 启动失败", t)
                }
                result
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "powerkeeper hook 失败", t)
        }
    }

    @Synchronized
    private fun scheduleStart() {
        if (startScheduled) return
        startScheduled = true

        val handler = Handler(Looper.getMainLooper())
        var attempt = 0

        val task = object : Runnable {
            override fun run() {
                attempt++
                try {
                    val ctx = systemContext()
                    AutoSaver.logger = { msg -> log(Log.INFO, TAG, msg) }
                    AutoSaver.start(ctx)
                } catch (t: Throwable) {
                    if (attempt < 40) {
                        handler.postDelayed(this, 3000)
                    } else {
                        log(Log.ERROR, TAG, "system_server 内启动失败（已重试 $attempt 次）", t)
                    }
                }
            }
        }
        handler.postDelayed(task, 1500)
    }

    private fun systemContext(): Context {
        val at = Class.forName("android.app.ActivityThread")
        val current = at.getDeclaredMethod("currentActivityThread").invoke(null)
            ?: throw IllegalStateException("ActivityThread 未就绪")
        val getSystemContext = at.getDeclaredMethod("getSystemContext")
        getSystemContext.isAccessible = true
        return getSystemContext.invoke(current) as? Context
            ?: throw IllegalStateException("systemContext 为空")
    }

    companion object {
        private const val TAG = "AutoSuperSaver"
    }
}
