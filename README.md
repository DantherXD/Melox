
<div align="center">

<img src="assets/Melox-icon.png" alt="Melox 应用图标" width="96"><br>

# [Melox](https://github.com/Inefy-03/Melox)

#### 一个基于  <a href="https://github.com/compose-miuix-ui/miuix">Miuix</a> 的 Android 本地音乐播放器</strong>

![Android](https://img.shields.io/badge/Android-9%2B-3DDC84?style=flat&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue)
![Miuix](https://img.shields.io/badge/Miuix-0.9.3-4F6BED?style=flat)
![Media3](https://img.shields.io/badge/Media3-1.11.0-4F6BED?style=flat)

</div>

---

## 项目简介

Melox 是一款基于 Jetpack Compose、Miuix 和 AndroidX Media3 构建的 Android 本地音乐播放器。

## 功能特性

### 音乐库与首页

- 支持扫描系统媒体库，也支持把扫描范围限制到指定文件夹
- 歌曲、专辑、艺术家和文件夹页面都支持搜索、排序与字母索引
- 专辑和艺术家有独立详情页，点击列表项即可从当前页面队列开始播放
- 首页提供随机推荐和最近添加
- 支持音乐库统计
- 可选扫描刷新策略
- 支持文件夹范围设置，支持屏蔽文件夹

### 播放、队列与恢复

- 支持拖动进度、上一首、下一首，以及顺序播放、单曲循环和随机队列
- 队列可以打开、清空、移除单首歌曲，也可以把歌曲加入下一首播放或当前队列
- 应用会保存队列、当前曲目和播放进度；重新打开或进程重启后恢复，但不会擅自自动播放
- 外部音频文件通过系统入口打开后，可以直接交给 Melox 播放

### 本地歌词与曲目信息

- 支持逐字/逐词时间轴、翻译行、字号和字重调整
- 可以选择歌词对齐方式、非当前行模糊，以及是否在歌词页隐藏播放控制
- 歌曲信息页展示标题、艺术家、专辑、格式、码率、采样率、位深、时长和文件位置
- 安装“音乐标签”或 Lyrico 后，可从歌曲操作跳转编辑

### 播放页与界面设置

- 跟随系统、浅色和深色主题
- 基于当前封面或系统壁纸的动态配色
- 模糊封面、动态流光、悬浮底栏和液态玻璃效果
- 支持 Miuix 与 AOSP 页面过渡动画、顶栏渐进模糊和隐藏底栏
- 播放页标题支持左对齐，过长标题自动滚动显示一次
- 可选默认首页

## 运行要求

- Android 9（API 28）或更高版本
- 液态玻璃等运行时视觉效果需要 Android 13+

## 支持格式与权限

- 支持 AAC、AIFF、ALAC、APE、FLAC、M4A、MP3、MP4、OGA、OGG、OPUS、WAV/WAVE 和 WMA 等本地音频格式；实际可播放范围取决于系统与 Media3 解码器
- 首次扫描需授予音乐读取权限：Android 13 及以上为“音乐和音频”，Android 12 及以下为存储读取权限
- 使用自定义文件夹时，需通过系统文件夹选择器授予目标文件夹及其子目录的访问权限
- 当前 APK 仅提供 `arm64-v8a` 架构

## 下载与安装

- 稳定版下载地址：[GitHub Releases](https://github.com/Inefy-03/Melox/releases)
- 测试版获取渠道：[Telegram 频道](https://t.me/MeloxPlayer)

## 致谢

- [Miuix](https://github.com/compose-miuix-ui/miuix) - UI 组件与设计体系
- [AndroidX Media3](https://developer.android.com/media/media3) - 本地媒体播放与系统媒体会话

Melox 仍在持续开发中，欢迎通过 [Issues](https://github.com/Inefy-03/Melox/issues) 反馈问题或提交 [Pull Request](https://github.com/Inefy-03/Melox/pulls)。
