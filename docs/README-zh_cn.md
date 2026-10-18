<div align="center">
  <img src="../images/AppIcon.png" width="128" height="128" alt="HachiDroid icon">
  <h1>HachiDroid</h1>
  <p><b>面向 UM:PD 与 PC:RD 的次世代 Android MOD 补丁与管理工具</b></p>

  <p><a href="../README.md">English</a> | <a href="README-fil.md">Filipino</a> | <a href="README-ja.md">日本語</a> | <a href="README-ko.md">한국어</a> | <a href="README-ru.md">Русский</a> | <a href="README-vi.md">Tiếng Việt</a> | 简体中文 | <a href="README-zh_tw.md">繁體中文</a></p>

  <p>
    <a href="https://github.com/HachiFridge/HachiDroid/releases"><img src="https://img.shields.io/github/v/release/HachiFridge/HachiDroid?color=brightgreen&amp;logo=github&amp;style=for-the-badge" alt="Latest Release"></a>
    <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Android-7.0+-3DDC84?logo=android&amp;logoColor=white&amp;style=for-the-badge" alt="Android Support"></a>
    <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&amp;logoColor=white&amp;style=for-the-badge" alt="Kotlin Version"></a>
  </p>
  <p>
    <a href="https://discord.gg/YjBgmuqqYr"><img src="https://dcbadge.limes.pink/api/server/https://discord.gg/YjBgmuqqYr" alt="Discord Server"></a>
    <a href="../LICENSE"><img src="https://img.shields.io/badge/License-GPL%203.0-blue.svg?style=flat-square" alt="License"></a>
  </p>
</div>

HachiDroid 是一个开源 Android 应用，用于向移动游戏客户端注入并打补丁模组运行时。它内置两个游戏系列：面向 **UM:PD** 的 **Hachimi** 运行时 **Tracen**，以及面向**PC:RD（日服）**的 **DaringTact** 运行时 **Astraea**，可在主界面一键切换。它基于 Jetpack Compose 与 Material 3 Expressive 设计从零构建，是 [UmaPatcher-Edge](https://github.com/Tenshou170/UmaPatcher-Edge) 的现代继任者。

---

## 主要特性

- **多客户端支持**：支持一键切换并管理多个客户端版本：
  - 日服 (JP)
  - 繁中服 (TW / HK)
  - 韩服 (KR)
  - 国际服 (EN)
- **灵活的补丁目标**：
  - **已安装的游戏**：直接检测并修补设备上已安装的客户端。
  - **APK 文件**：支持选择并修补单 APK、拆分 APK 安装包或 XAPK 文件。
- **多种安装与导出模式**：
  - **Direct Install (Root)**：无需重新安装，直接将补丁库挂载到运行中的应用中。
  - **Shizuku (提权 ADB)**：无需 Root 权限即可直接安装拆分 APK 和 XAPK。
  - **应用安装程序**：标准系统会话安装。
  - **导出文件**：直接将打好补丁的单 APK 或 XAPK 保存到存储空间。
  - **传统安装程序**：面向旧环境的备用系统安装程序。
- **Hachimi Mod 管理**：
  - 自动从 GitHub 发布版本获取并更新最新 Hachimi-Edge 运行时。
  - 一键侧载本地自定义 `.so` 二进制文件。
- **插件系统**：支持侧载并独立启用/停用额外的原生 `.so` 插件。
- **内置应用更新程序**：具备 SHA-256 完整性校验和更新日志查看器的应用内更新检查。
- **现代存储与 DocumentsProvider**：可选导出 DocumentsProvider，允许文件管理器访问 `/data/data`（Android 16 QPR2+ 及 Android 17+ 必需）。
- **UmaPatcher-Edge 迁移**：一键自动从旧版 UmaPatcher-Edge 迁移签名密钥库、插件和配置偏好。
- **游戏模式**：Tracen（UM:PD · Hachimi）与 Astraea（PC:RD 日服 · DaringTact），各有独立配色与仪表盘。
- **设备内库解包（Astraea）**：通过内置的 [Senbei](https://github.com/Tenshou170/Senbei) 引擎，直接从游戏的 arm64 拆分包中解包受 CrackProof 保护的 `libil2cpp.so` 与 `libnative.so`，无需电脑工具，也无需重新上传任何文件。仅支持 arm64 设备。
- **单向补丁警告**：Astraea 模式会明确提示，已打补丁的 PC:RD 无法恢复原版；若要游玩原版，需从 Play 商店重新安装。

---

## 运行模式

在英雄卡片的下拉菜单中选择游戏系列，选择会作用于整个应用：配色、仪表盘磁贴以及补丁流程。

| 模式 | 游戏 | 运行时 | 状态 |
| :--- | :--- | :--- | :--- |
| **Tracen** | UM:PD (JP / TW-HK / KR / EN) | Hachimi-Edge | 完整补丁流程 |
| **Astraea** | PC:RD（日服） | DaringTact | 拆分包补丁、库文件解包、安装或导出 |

Astraea 打的是游戏的拆分包，而不是已安装的应用：base 拆分包会换上干净的 Application 类，并改写清单以绕过 CrackProof 外壳；arm64 config 拆分包则会删除 `lib__*__.so` 桩库，并写入 `libil2cpp.so`、`libnative.so` 和 `libdaringtact.so`。结果会重新签名，可通过 Shizuku、系统安装程序安装，或导出为拆分包 APK / XAPK。

应用内也会明确说明两点：补丁版用你的密钥签名，无法更新商店版本（请先卸载游戏）；也没有原版还原——补丁移除的外壳无法恢复，唯一的办法是从 Play 商店重新安装。

---

## 运行要求

| 组件 | 最低要求 | 说明 |
| :--- | :--- | :--- |
| **Android 系统** | Android 7.0 (API Level 24)+ | 针对 Android 17 (API Level 37) 构建并测试 |
| **目标应用** | 支持的 UM:PD 客户端 | 日服、繁中服、韩服或国际服 |
| **Astraea 目标** | PC:RD（日服） | arm64-v8a 拆分包；设备内还原需 arm64 设备 |
| **Root (可选)** | Magisk / KernelSU / APatch | 仅在 Direct Install 模式下必需 |
| **Shizuku (可选)** | Shizuku v13+ | 用于免 Root 安装拆分 APK / XAPK |

---

## 快速入门

1. 从 **[Releases](https://github.com/HachiFridge/HachiDroid/releases)** 页面安装 HachiDroid。
2. 打开应用并在 **补丁工作台** 中选择目标客户端。
3. 选择输入来源（**已安装的游戏** 或 **APK 文件**）。
4. 选择偏好的 **安装方式**（Direct Root、Shizuku、应用安装程序或导出文件）。
5. 点击 **开始打补丁** 并按屏幕提示操作。

详细指南：**[在 Android 上安装 Hachimi](https://hachimi.noccu.art/docs/hachimi/installing-android)**

---

## 从源码构建

环境配置及 Gradle 构建命令已详细记录在 **[BUILDING-zh_cn.md](BUILDING-zh_cn.md)**。

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
./gradlew assembleRelease
```

---

## 社区与支持

- **Discord**：加入我们的 [Discord 社区](https://discord.gg/YjBgmuqqYr) 进行交流与反馈。
- **Issues**：通过 GitHub Issues 提交错误报告或功能建议。

---

## 许可证

本项目基于 GNU General Public License v3.0 (GPL-3.0) 许可证开源 — 详情见 [LICENSE](../LICENSE) 与 [NOTICE](../NOTICE) 文件。

### Senbei 桥接

可选的设备内还原会链接 CrackProof 解包工具 [Senbei](https://github.com/Tenshou170/Senbei)（**AGPL-3.0-only**）。因此包含 `libsenbei_jni.so` 的构建在上面的 GPL-3.0 之外还需遵守 AGPL-3.0；详见 [NOTICE](../NOTICE)。没有 Rust 工具链时，该桥接不会被包含。
