# TriKey

TriKey 将设备上的一个硬件快捷键映射为单击、双击和长按三种动作。模块在 `system_server` 中处理按键。

## 功能

- 打开微信、支付宝入口、系统设置、搜索、相机、录音等页面或应用
- 执行截图、返回、锁屏、状态栏和 ColorOS 识屏动作
- 配置自定义应用或 Intent URI
- 调整按键码、双击间隔和长按阈值
- 熄屏按键时唤醒屏幕并继续启动目标

熄屏唤醒和跳转默认开启，不需要单独设置。没有安全锁屏时，唤醒后直接打开目标；设置了 PIN、图案或密码时，TriKey 请求 Android 显示系统锁屏验证界面，只有系统确认验证成功后才启动目标。取消或验证失败都不会跳转。TriKey 不读取、保存、输入或绕过任何凭据。

截图、返回、锁屏和状态栏动作仍按原有方式即时执行；熄屏验证流程用于页面和应用启动动作。

## 安装与配置

1. 安装 TriKey APK。
2. 在 LSPosed 中启用模块，并将系统框架加入作用域。
3. 重启设备，使 LSPosed 加载 system_server 按键 Hook。
4. 打开 TriKey，为三种手势选择动作并保存。

默认按键码为 `780`。按键码和厂商提供的应用入口因设备而异。“替代系统原动作”默认关闭；打开后，目标按键只执行 TriKey 动作。

## Android 兼容性

应用最低支持 Android 8.0（API 26），兼容目标为 Android 15（API 35）及以上。唤醒时会根据系统实际提供的 API 选择 `PowerManager.wakeUp` 实现；安全锁屏验证使用 Android API 26 起提供的 `KeyguardManager.requestDismissKeyguard`，不依赖单一系统版本的密码界面实现。

硬件按键入口优先使用 ColorOS 系统 Hook，并提供 AOSP 系统策略回退。实际按键码和预设应用入口由厂商及已安装应用决定，因此并非所有预设动作都适用于所有 Android 设备。

## 构建

在仓库根目录运行：

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

GitHub Actions 会运行测试、Lint 并构建 APK。签名版本标签会发布到 GitHub Releases，文件名为 `TriKey-v<版本号>.apk`。
