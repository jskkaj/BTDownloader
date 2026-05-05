# 极简磁力下载工具

一个极简的Android磁力下载工具，无广告，代码精简。

## 功能特性
- ✅ 支持磁力链接下载
- ✅ 支持torrent文件导入
- ✅ 全速下载，无限制
- ✅ 极简界面，无广告
- ✅ 无多余功能，专注下载

## 在线编译方法

### 方法1：GitHub Actions 一键构建
1. Fork 本项目到你的GitHub账号
2. 点击 Actions 标签
3. 点击 "Build APK" 工作流
4. 点击 "Run workflow" 按钮
5. 等待2-3分钟，构建完成后在 Artifacts 下载APK

### 方法2：本地编译
1. 使用Android Studio打开项目
2. 等待Gradle同步完成
3. 点击 Build → Build APK(s)

## 核心依赖
- jlibtorrent 2.0.12.7 - BT下载核心引擎
- AndroidX - 现代Android框架

## 权限说明
- INTERNET - 网络下载
- ACCESS_NETWORK_STATE - 网络状态检测
- READ_EXTERNAL_STORAGE - 读取种子文件
- WRITE_EXTERNAL_STORAGE - 保存下载文件
- WAKE_LOCK - 防止下载时休眠

