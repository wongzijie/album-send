![相册快送：Android原片发送至SMB，v0.1.0早期预览](docs/assets/showcase/cover.png)



# 相册快送 · Album Send

**一键把你的相册发送到 NAS / 共享文件夹。**

拍完照片、录完视频，回家连上 Wi-Fi，选中需要的内容，直接送到你自己的 NAS 或电脑共享文件夹。原片不压缩，不需要注册账号，也不经过第三方云服务。

[![Android](https://img.shields.io/badge/Android-8.0%2B-176B55)](#下载安装)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Build](https://github.com/wongzijie/album-send/actions/workflows/android.yml/badge.svg)](https://github.com/wongzijie/album-send/actions/workflows/android.yml)

**[下载 APK](https://github.com/wongzijie/album-send/releases/tag/v0.1.0)** · [English](README.en.md) · [反馈问题](https://github.com/wongzijie/album-send/issues/new/choose) · [开发说明](docs/DEVELOPMENT.md)

> 当前是 **v0.1.0 早期预览版**，使用调试签名。已完成构建及本机 SMB 集成测试，欢迎一起验证真实手机和 NAS 的兼容性。

## 为什么做这个

手机相册越来越大，想把原始照片和视频放到自己的存储里，往往还要接数据线、在文件管理器里来回找目录，或者依赖额外的云服务。

相册快送只专注这一件事：**选中相册里的内容，发送到自己的共享文件夹。** 适合旅行归来整理原片、把大视频移交到电脑，以及日常手动备份。

## 已经能做什么

| 功能 | 使用方式 |
| --- | --- |
| 相册浏览 | 按相册、照片、视频筛选，显示缩略图和视频时长 |
| 连续勾选 | 点击单项，或长按后滑动连续选择；拖到边缘自动滚动 |
| 局域网发现 | 搜索附近开放 SMB 端口的设备，也可手动填写地址 |
| 保存连接 | 保存 NAS / 电脑、账号和常用目标目录，下次直接打开 |
| 多级目录 | 逐级进入共享文件夹中的子目录，选择实际存放位置 |
| 原片发送 | 不压缩、不转码，原文件中的 EXIF 等信息随文件保留 |
| 同名保护 | 同名文件自动加序号，不覆盖已有文件 |
| 后台传输 | 前台服务显示进度，可取消，也可重试未完成项目 |

手机里的原文件会保留。当前为手动选择后发送；**尚不支持自动增量备份、重复文件识别和断点续传**，重复发送会生成带序号的新副本。

## 下载安装

1. 从 **[Releases](https://github.com/wongzijie/album-send/releases/tag/v0.1.0)** 下载 `album-send-0.1.0-preview.apk`。
2. 在 Android 手机上打开 APK，按系统提示允许从当前下载来源安装。
3. 首次运行时授予照片和视频权限。选择全部内容后，后续打开会沿用授权；只授权部分照片时，应用只显示允许访问的内容。

需要 **Android 8.0 或以上**，手机与 NAS / 电脑应能在同一本地网络互通。共享端需要启用 SMB，并让使用的账号具有目标文件夹的写入权限。

## 传输图解：什么时候才算一个文件完成？

![传输图解：什么时候才算一个文件完成？，流程示意而非运行截图](docs/assets/showcase/workflow.png)

先写临时文件，关闭流并检查远端大小，再重命名为正式文件。示意图不是手机实机截图；大小检查不等于整文件哈希，仍需真机和NAS兼容性验证。

[查看全部图片与来源](docs/assets/showcase/README.md)

## 三步发送

1. **选内容**：打开相册，点选照片 / 视频，或长按滑动连续勾选。
2. **选目标**：点右上角“发送”，选择已保存的连接，或搜索局域网、添加共享地址。
3. **送过去**：逐级进入目标子目录，点“发送到这里”。

也可以先从首页“文件夹”添加连接，进入常用位置后选择“保存此文件夹”。长按连接可编辑或删除。

添加连接时，共享名称只填一级共享名，例如 `Photos`，不用填整个路径；子目录在连接后逐级选择。共享名留空时会尝试列出服务器上的共享。如果服务器禁止列举共享，请直接填写共享名。

## 你的文件与账号

- 账号配置通过 Android Keystore 的 AES-GCM 加密后保存在手机上，并排除应用备份。
- 应用没有云端账号系统、广告或分析 SDK。
- 写入采用临时文件，关闭流并校验远端大小后再重命名为正式文件。
- 同名文件自动改名。普通失败或取消会尝试清理当前临时文件。

系统强杀可能留下 `.part` 临时文件；发送结果只做大小校验，不做整文件哈希。当前版本尚未完成全部真机、NAS 厂商或强制 SMB 加密策略验证。更多细节见 [开发与验证说明](docs/DEVELOPMENT.md)。

## 常见问题

**搜索不到 NAS？** 确认连接同一本地网络、SMB 已开启、TCP 445 没有被防火墙拦截。当前自动发现最多检查本地 IPv4 `/24` 网段；也可以直接输入 IP 或主机名。

**能保存到很深的子目录吗？** 可以，逐级进入即可；目录是否可见、能否写入，由共享端账号权限决定。

**支持群晖、威联通、Windows 或 Mac 吗？** 应用面向标准 SMB 共享。具体设备仍需要实际联调，欢迎提交兼容性反馈；本项目不声称已验证所有品牌。

**传一半退出怎么办？** 切到其他应用时通过前台服务继续传输。系统强制停止或超时后不会自动恢复，当前不支持断点续传，需要重新选择发送。

## 一起把它做得更顺手

先把原片发送体验打磨好，再逐步完善增量备份、持久化队列和大文件传输体验。完整方向见 [路线图](docs/ROADMAP.md)。

欢迎提交 [设备兼容性反馈](https://github.com/wongzijie/album-send/issues/new/choose)、功能建议和 PR。反馈时请提供 Android 版本、共享设备及复现步骤，**不要上传真实账号、密码、私密照片或完整网络配置**。贡献方式见 [CONTRIBUTING](CONTRIBUTING.md)。

## 开发

Java 17 · 原生 Android UI · SMB 2 / 3 协商 · min SDK 26 · compile SDK 36。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

GitHub Actions 同时运行构建、静态检查及本机 SMB 集成测试。已有的本地验证结果包含 4 项文件名测试和 3 项 SMB 测试；真机操作验证仍待补充。

## 许可

应用自身代码使用 [MIT License](LICENSE)。第三方依赖保留各自许可证，见 [第三方说明](THIRD_PARTY_NOTICES.md)。

## 更多项目

[A800 动量回测](https://github.com/wongzijie/stock_database) · [OpenClaw 编排经验](https://github.com/wongzijie/OpenClaw-Experience) · [Spark Memory 设计](https://github.com/wongzijie/spark-memory) · [Snapshot 截图工具](https://github.com/wongzijie/Snapshot) · [Buffett Archive 本地检索](https://github.com/wongzijie/buffett-archive)
