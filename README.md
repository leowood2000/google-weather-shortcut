# Google Weather Shortcut

在桌面创建一个直达 **Google App 原生天气页**（`WeatherActivity`）的快捷方式。

## 为什么需要 root / Shizuku

Google App 的天气 Activity（`com.google.android.apps.search.weather.WeatherActivity`）**未导出（exported=false）**，
任何普通应用都无法直接 `startActivity` 拉起它（实测报 `Permission Denial: not exported`）。

因此本 App 采用**双通道 shell 执行**方式拉起原生天气页：

| 通道 | 要求 | 原理 | 启动能力 |
|---|---|---|---|
| Root | 已 root（Magisk/KernelSU） | `su -c am start -n ...` | 可启动 private Activity |
| Shizuku (Root) | Shizuku 以 root 启动 | `Shizuku.newProcess` 执行 `am start` | 等同 root |
| Shizuku (ADB) | Shizuku 以 adb shell 启动 | 同上，但身份为 shell | **受限**，未必能启动 private Activity |

App 状态页会自动检测 Shizuku 运行身份并标注启动能力。
Shizuku(ADB) 通道不保证可用，建议优先使用 Root 或 Shizuku(Root)。

## 功能

1. **打开原生天气**：一键拉起 `WeatherActivity`（非网页版，无浏览器 UI）
2. **固定桌面快捷方式**：通过 `ShortcutManager.requestPinShortcut()` 在桌面创建"谷歌天气"图标
3. **状态诊断**：自动检测 Google App / WeatherActivity / Root / Shizuku 状态，显示可用通道

## 使用

1. 安装 APK（`app-release.apk`，见 Releases / Actions Artifacts）
2. 首次使用：
   - **已 root**：在 root 管理器（KernelSU/Magisk）中给 "Google 天气" 授权，然后直接点「打开 GOOGLE 原生天气」
   - **未 root**：先安装 [Shizuku](https://shizuku.rikka.app/) 并以 root 或 adb 启动 Shizuku 服务，点「授权 SHIZUKU」授权本 App
3. 点「固定到桌面」→ 系统弹窗确认 → 桌面出现"谷歌天气"图标

> **注意**：App 重装后 KernelSU root 授权和 Shizuku 授权需重新授予。
> 从 v1.2.0 起签名固定，后续更新可直接覆盖安装，无需卸载旧版。

## 快捷方式原理

```
桌面图标(谷歌天气)
  └─> LaunchActivity（本 App，透明页）
       └─> 前置检查（Google App 已安装? WeatherActivity 存在?）
       └─> am start -n com.google.android.googlequicksearchbox/com.google.android.apps.search.weather.WeatherActivity
             (root / Shizuku shell)
             └─> Google App 原生天气页
```

快捷方式本身无需特殊权限即可固定（`requestPinShortcut`），只有**启动**天气需要 shell 权限。

## 构建

GitHub Actions 自动构建（push main / 打 tag v* 即出 Release）：

```bash
gradle assembleRelease    # 签名 release APK（需 CI Secrets）
gradle assembleDebug      # debug APK（无需签名）
```

输出：
- Release: `app/build/outputs/apk/release/app-release.apk`
- Debug: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Release 仅包含签名 release APK；debug APK 可从 Actions Artifacts 下载。

## 技术细节

- **Shizuku 执行**：通过反射调用 `newProcess`（private API），末尾追加 `echo __GWS_EXIT_CODE__=$?` 获取真实退出码，不依赖 `waitFor()`/`exitValue()`（Shizuku Process 包装类行为非标准）
- **超时检测**：通过线程 `isAlive` 判断是否超时，超时后 `destroyForcibly` + 判失败
- **启动前检查**：`launchWeather()` 先检查 Google App 和 WeatherActivity 是否存在，避免无意义 shell 调用
- **签名**：RSA 2048，有效期 10000 天，密钥存为 GitHub Secrets

## 为什么不是网页快捷方式

`https://www.google.com/gaweather` 等 URL 打开的是**网页版 / Assistant 卡片**，与原生 `WeatherActivity` 是两套不同实现（已实测 dumpsys 确认：gaweather deeplink 绑定的是 `MainAssistantDeeplinkAnimated` 而非 `WeatherActivity`）。要原生页只能 shell 直启。
