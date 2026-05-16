# 🌙 NightShield - 夜间屏幕亮度调节

一款解决夜间玩手机时，系统最低亮度仍然过亮问题的 Android 应用。

## 功能特点

- 🔅 **超低亮度**：在系统最低亮度基础上，进一步降低屏幕亮度
- 🟠 **蓝光过滤**：暖色调护眼模式，减少蓝光刺激
- 🔔 **通知栏快捷开关**：无需打开应用，一键开启/关闭
- 🔄 **开机自启**：自动恢复上次设置
- ⚡ **实时调节**：拖动滑块即时生效

## 使用方法

1. 下载并安装 APK
2. 打开应用，授予**悬浮窗权限**（必需）
3. 开启主开关
4. 将系统亮度调到最低
5. 通过应用内的滑块进一步降低亮度

## 下载 APK

每次代码更新后，GitHub Actions 会自动编译并发布 APK：

👉 [点击此处查看最新 Releases](https://github.com/你的用户名/NightShield/releases)

或者查看 [Actions 页面](https://github.com/你的用户名/NightShield/actions) 下载最新构建的 APK。

## 自行编译

如果你有 Android 开发环境：

```bash
./gradlew assembleRelease
```

APK 将生成在 `app/build/outputs/apk/release/` 目录。

## 技术原理

通过 Android 的 `SYSTEM_ALERT_WINDOW` 权限创建全屏半透明遮罩层，叠加在所有应用之上：
- **亮度降低**：通过黑色遮罩的透明度控制
- **蓝光过滤**：通过暖色调（琥珀色）遮罩实现

## 系统要求

- Android 7.0 (API 24) 及以上
- 需要悬浮窗权限

## 开源协议

MIT License
