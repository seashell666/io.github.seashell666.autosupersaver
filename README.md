# 自动超级省电（AutoSuperSaver）

小米手机（MIUI / HyperOS）Xposed 模块：**电量低于设定阈值且未充电时，自动让系统进入「超级省电模式」**。

- 基于 **Vector**（LSPosed 继任者）的 **libxposed API 102**
- Kotlin 编写，单 APK 同时是设置界面和 Xposed 模块
- 只负责"进入"，退出由你手动点系统按钮
- 默认阈值 17%，默认冷却 27 分钟

当前版本 **v1.0.0** ｜ [下载 APK](https://github.com/seashell666/io.github.seashell666.autosupersaver/releases/latest) ｜ [更新日志](CHANGELOG.md) ｜ [文档目录](docs/)

## 原理

MIUI 的超级省电模式由 `com.miui.securitycenter`（电量和性能）承载，状态存在系统设置：

```
settings put system EXTREME_POWER_SAVE_MODE_OPEN 1
```

模块注入 `system_server`，监听系统电量变化（1% 粒度），在满足条件时直接写入该键，行为与手动点击开关一致（含切换极简桌面、关闭 5G、限制性能等）。

已验证机型：小米 11（M2011K2C），Android 13 / MIUI 14.0.10.0.TKBCNXM。

## 触发条件

同时满足才会触发：

1. 开关已启用
2. **未充电**（有线 / 无线 / 反向充电都算充电）
3. 电量 ≤ 阈值（默认 17%）
4. 距上次触发已超过冷却时间（默认 27 分钟）

触发后写入 `ass_last_trigger` 时间戳，冷却期内不再重复触发。模块不会做任何退出操作。

## 前提条件

- 手机已 root（Magisk / KernelSU），并开启 Zygisk
- 已安装 **Vector 2.x**（API 102），框架处于激活状态
- 系统为 MIUI 13/14 或 HyperOS（超级省电入口存在即可）

## 安装

1. 安装 APK（debug 或 release 均可，同一签名）。
2. 打开 **Vector 管理器 → 模块 → 启用「自动超级省电」**，确认作用域里有「系统框架」。
   用命令行启用时，作用域不会自动写入，需要额外执行：
   ```
   su -c '/data/adb/modules/zygisk_vector/cli modules enable io.github.seashell666.autosupersaver'
   su -c '/data/adb/modules/zygisk_vector/cli scope add io.github.seashell666.autosupersaver system/0'
   ```
3. 重启手机（system_server 注入只在启动时发生）。
4. 打开 App，授予「修改系统设置」权限（Android 设置里手动打开），设置阈值与冷却时间。
5. **首次使用：手动在系统里开启一次超级省电，弹窗勾选「不再提示」再确定。** 否则每次自动触发都会停在确认弹窗上。

## 使用

- 阈值、冷却时间、触发命令都可在 App 内修改，改完立即生效（存在 `Settings.System`，system_server 直接读）。
- 「立即测试」按钮会让模块立刻执行一次触发，用于验证注入是否成功。
- 日志显示在 App 底部，同时写入 `ass_last_log`。

## 触发命令

默认直接写设置键；若写入失败，回退执行自定义命令（App 内可改）：

```
settings put system EXTREME_POWER_SAVE_MODE_OPEN 1
```

换机型时如入口不同，可直接改这条命令，无需重新编译。

## 调试

不刷机、不等电量掉到 17%，可以直接验证整条链路：

```bash
# 伪造电量读数（>0 生效，0 恢复真实值）
adb shell settings put system ass_debug_percent 10
# 清空冷却，让它立刻触发
adb shell settings put system ass_last_trigger 0
# 看日志
adb shell settings get system ass_last_log
```

验证完记得 `adb shell settings put system ass_debug_percent 0`。

## 已验证

小米 11（M2011K2C）/ Android 13 / MIUI 14.0.10.0 / Vector 2.2（API 102）：

- 模块在 system_server 注入成功，启动日志写入 `ass_last_log`
- 电量 10% ≤ 17% 且未充电 → 自动写入 `EXTREME_POWER_SAVE_MODE_OPEN=1`，系统进入超级省电
- 1 分钟后的下一次判定被冷却拦下（日志显示"冷却中，剩余 25 分钟"）
- 充电状态下不触发（日志/判定均确认）

## 退出超级省电

在系统界面手动退出，或用：

```
adb shell settings put system EXTREME_POWER_SAVE_MODE_OPEN 0
adb shell settings put system POWER_SAVE_MODE_OPEN 0
```

## 已知限制

- 超级省电会关闭大部分应用，模块自己的界面在超级省电下可能无法打开；建议进入前把配置调好。
- 「不再提示」标记存在 `com.miui.securitycenter` 的 `sp_superpower.xml`（`key_superpower_dialog_enable`），清除安全中心数据后会重新弹窗。
- 只保证小米 11 / MIUI 14 验证过的路径；其它机型若入口不同，需要改命令或改代码。
- 模块 Hook 失败不会影响系统，但会写日志并在连续失败后停止尝试。

## 构建

需要 JDK 17、Android SDK（platform 34、build-tools 34）。仓库自带 Gradle Wrapper（8.7），无需本地装 Gradle。

```bash
# 1. 新建 local.properties，写入你的 SDK 路径（不要提交这个文件）
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

# 2. 构建
./gradlew assembleDebug assembleRelease
# 产物：app/build/outputs/apk/{debug,release}/
```

签名：默认读取 `keystore/autosupersaver.jks`（密码见 `app/build.gradle`）。仓库里不含私钥，自己构建请先生成：

```bash
keytool -genkeypair -keystore keystore/autosupersaver.jks -alias autosupersaver \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass autosupersaver -keypass autosupersaver \
  -dname "CN=AutoSuperSaver"
```

没有 keystore 时构建不会失败，只是产物未签名（需自行用 Android Studio 的 debug 签名或 `apksigner` 签一下）。

## 文档

- [需求与技术方案](docs/需求与技术方案.md)：功能范围、三种技术路线对比、验收用例、风险表
- [发布指南](docs/发布指南.md)：仓库设置、Release 附件、上传自检清单
- [v1.0.0 Release Notes](docs/Release-Notes-v1.0.0.md)
- [更新日志](CHANGELOG.md)

## 免责声明

修改系统省电行为存在风险，请自行承担。模块不联网、不上传任何数据；配置仅存在本机 `Settings.System` 中。

## License

MIT
