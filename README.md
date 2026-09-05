# Google Weather Shortcut

在桌面创建一个直达 **Google App 原生天气页**（`WeatherActivity`）的快捷方式。

## 为什么需要 root / Shizuku

Google App 的天气 Activity（`com.google.android.apps.search.weather.WeatherActivity`）**未导出（exported=false）**，
任何普通应用都无法直接 `startActivity` 拉起它（实测报 `Permission Denial: not exported`）。

因此本 App 采用**双通道 shell 执行**方式拉起原生天气页：

| 通道 | 要求 | 原理 |
|---|---|---|
| Root | 已 root（Magisk/KernelSU） | `su -c am start -n ...` |
| Shizuku | 免 root，adb 无线调试授权一次 | `Shizuku.newProcess` 以 shell 权限执行 `am start -n ...` |

## 功能

1. **打开原生天气**：一键拉起 `WeatherActivity`（非网页版，无浏览器 UI）
2. **固定桌面快捷方式**：通过 `ShortcutManager.requestPinShortcut()` 在桌面创建"谷歌天气"图标（支持自定义图标）

## 使用

1. 安装 APK（`app-debug.apk`，见 Releases / Actions Artifacts）
2. 首次使用：
   - **已 root**：直接点「打开 Google 原生天气」即可
   - **未 root**：先安装 [Shizuku](https://shizuku.rikka.app/)，用 adb 授权后点「授权 Shizuku」
3. 点「固定到桌面」→ 系统弹窗确认 → 桌面出现"谷歌天气"图标

## 快捷方式原理

```
桌面图标(谷歌天气)
  └─> LaunchActivity（本 App，透明页）
       └─> am start -n com.google.android.googlequicksearchbox/com.google.android.apps.search.weather.WeatherActivity
             (root / Shizuku shell)
            └─> Google App 原生天气页
```

快捷方式本身无需特殊权限即可固定（`requestPinShortcut`），只有**启动**天气需要 shell 权限。

## 构建

GitHub Actions 自动构建（push main / 打 tag v* 即出 Release）：

```bash
gradle assembleDebug
```

输出：`app/build/outputs/apk/debug/app-debug.apk`

## 为什么不是网页快捷方式

`https://www.google.com/gaweather` 等 URL 打开的是**网页版 / Assistant 卡片**，与原生 `WeatherActivity` 是两套不同实现（已实测 dumpsys 确认：gaweather deeplink 绑定的是 `MainAssistantDeeplinkAnimated` 而非 `WeatherActivity`）。要原生页只能 shell 直启。