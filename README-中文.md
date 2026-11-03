# 米游签 Android 独立版（Android 16 适配工程）

本工程将项目 Python 核心和 Web 控制台放入 APK，通过 Chaquopy 在手机本地运行；WebView 连接 `127.0.0.1:5890`。无需外部服务器。目标 SDK 36（Android 16），最低 Android 8.0，包含 ARM64/ARM32 ABI。

## Android 16 适配内容

- `compileSdk 36`、`targetSdk 36`。
- 使用 Android 14+ 的 `specialUse` 前台服务类型，并声明用途说明，避免错误地把长期运行进程标为 `dataSync`。该类型在 Google Play 上架时需要按 Play 政策申报审核；侧载安装也仍受系统后台限制。
- 前台服务持续显示通知；Android 13+ 请求通知权限。
- 开机广播尝试恢复用户已启用的服务；设备厂商可能限制自启动。
- 提供电池优化豁免设置入口，建议用户主动允许。
- Python 项目原有每日调度线程在本地服务进程中运行；应用被强行停止、系统清理进程、关机或权限撤销期间无法执行。

## 构建 APK

1. 使用 Android Studio 打开本目录。
2. 安装 Android SDK Platform 36、JDK 17；Gradle 版本按 wrapper/项目配置同步。
3. 执行 `gradle :app:assembleDebug`。
4. APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`。
5. 也可以推送到 GitHub 后手动运行 `.github/workflows/build-apk.yml`，下载 Actions artifact。

## 首次运行

1. 安装 APK 并打开，允许通知权限。
2. 点击“设置电池不受限制”，在系统页面选择允许/不受限制。
3. 按页面提示完成首次配置和账号登录。
4. 在管理界面启用每日自动任务并保存时间。
5. 不要在系统设置中强行停止应用；部分品牌手机还需单独开启自启动、允许后台活动。

## 已知限制 / 重要说明

- 这是 Android 16 适配工程，不代表已在真实 Android 16 设备上通过安装、扫码、签到和重启测试。本工作环境未执行 Android SDK/Gradle 编译，不能保证当前 ZIP 已能直接生成可运行 APK。
- Android 的 `specialUse` 前台服务需要用户可感知且符合系统/应用商店政策；Google Play 分发需额外审核声明。
- Android 16、厂商 ROM、Doze、省电模式和用户强制停止都可能影响后台运行；无法承诺 100% 永久保活或准点执行。
- Chaquopy 依赖需要在 Gradle 同步时下载；请检查全部 Python 依赖在目标 ABI 上均有可用 wheel。尤其 Pillow 及其原生库需实际构建验证。
- 首次启动配置、数据目录、二维码登录和全部功能仍需在真机上测试。

## 数据安全

账号凭据保存在应用私有目录。不要分享 `config.yaml`、凭据文件或日志。卸载应用通常会清除应用私有数据。
