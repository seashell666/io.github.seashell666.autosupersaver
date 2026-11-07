# AutoSuperSaver v1.0.0

小米手机自动进入「超级省电模式」的 Xposed 模块。电量低于设定阈值（默认 17%）且未充电时，自动让系统进入超级省电；只负责进入，退出由你手动操作。

基于 **Vector（libxposed API 102）**，Kotlin 编写，单 APK 同时是模块和设置界面。

## 下载

| 文件 | 说明 | SHA-256 |
|---|---|---|
| `AutoSuperSaver-1.0.0-release.apk` | 正式版（2.0 MB），推荐 | `f6b4c03870e8ccf54cfa75c032b5db6289bbce1676e265da40385c25060e30ef` |
| `AutoSuperSaver-1.0.0-debug.apk` | 调试版（2.3 MB），带更多日志 | `b3fca22b9a851198b0aedeaad483892f44303980b9f459226fe72957ade569a0` |

两个包使用同一证书签名（`CN=AutoSuperSaver`，SHA-256 `1ecf1b4d…344abb0`），可以互相覆盖安装。

## 前提条件

- 手机已 root（Magisk / KernelSU），Zygisk 已开启
- 已安装 **Vector 2.x**（API 102）且框架处于激活状态
- MIUI 13 / 14 或 HyperOS（系统里存在「超级省电」入口）

## 安装步骤

1. 安装 APK。
2. 打开 **Vector 管理器 → 模块**，启用「自动超级省电」，确认作用域包含「系统框架」。
   用命令行启用时需要额外加作用域，否则不会注入 `system_server`：
   ```
   su -c '/data/adb/modules/zygisk_vector/cli modules enable io.github.seashell666.autosupersaver'
   su -c '/data/adb/modules/zygisk_vector/cli scope add io.github.seashell666.autosupersaver system/0'
   ```
3. **重启手机**（注入只发生在系统启动时）。
4. 打开 App，授予「修改系统设置」权限，设置阈值与冷却时间。
5. **首次使用：手动在系统里开启一次超级省电，弹窗里勾选「不再提示」再确定。**
   不做这一步，之后每次自动触发都会停在确认弹窗上。

## 功能

- 阈值可配（5%–50%，默认 17%），改完立即生效
- 冷却时间可配（默认 27 分钟）
- 有线 / 无线 / 反向充电均视为充电，充电时不触发
- 双通道判定：电量广播 + 每分钟兜底轮询
- 触发命令可配，换机型不必重新编译
- 界面内「立即测试」按钮和实时日志

## 已验证

小米 11（M2011K2C）/ Android 13 / MIUI 14.0.10.0 / Vector 2.2：

- 注入 `system_server` 成功，开机自启
- 电量 10%、未充电 → 自动进入超级省电
- 冷却期内不重复触发
- 充电状态下不触发
- 整机重启后自动恢复

## 已知限制

- 「不再提示」标记存在 `com.miui.securitycenter` 的 `sp_superpower.xml`，清除安全中心数据后会重新弹窗
- 超级省电下大部分应用不可用，模块设置界面可能打不开，请提前配置
- 其它机型若入口不同，需要修改触发命令

## 卸载 / 恢复

在 Vector 里关闭模块并重启即可。若手机当前正处于超级省电，手动点系统退出按钮，或执行：

```
adb shell settings put system EXTREME_POWER_SAVE_MODE_OPEN 0
adb shell settings put system POWER_SAVE_MODE_OPEN 0
```

模块本身不联网、不上传任何数据。

---

完整说明见 [README](https://github.com/seashell666/io.github.seashell666.autosupersaver#readme) ｜ 变更记录见 [CHANGELOG](https://github.com/seashell666/io.github.seashell666.autosupersaver/blob/main/CHANGELOG.md)
