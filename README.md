<div align="center">
  <img src="images/AppIcon.png" width="128" height="128" alt="HachiDroid Logo">
  <h1>HachiDroid</h1>
  <p><b>Next-generation Android mod patcher and manager for UM:PD and PC:RD</b></p>

  <p>English | <a href="docs/README-fil.md">Filipino</a> | <a href="docs/README-ja.md">日本語</a> | <a href="docs/README-ko.md">한국어</a> | <a href="docs/README-ru.md">Русский</a> | <a href="docs/README-vi.md">Tiếng Việt</a> | <a href="docs/README-zh_cn.md">简体中文</a> | <a href="docs/README-zh_tw.md">繁體中文</a></p>

  <p>
    <a href="https://github.com/HachiFridge/HachiDroid/releases"><img src="https://img.shields.io/github/v/release/HachiFridge/HachiDroid?color=brightgreen&logo=github&style=for-the-badge" alt="Latest Release"></a>
    <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Android-7.0+-3DDC84?logo=android&logoColor=white&style=for-the-badge" alt="Android Support"></a>
    <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge" alt="Kotlin Version"></a>
  </p>
  <p>
    <a href="https://discord.gg/YjBgmuqqYr"><img src="https://dcbadge.limes.pink/api/server/https://discord.gg/YjBgmuqqYr" alt="Discord Server"></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%203.0-blue.svg?style=flat-square" alt="License"></a>
  </p>
</div>

---

**HachiDroid** is an advanced, high-performance asset-patching tool and mod manager for Android designed to seamlessly inject mod runtimes and translation assets into supported mobile game clients. It supports two distinct game families switchable with one tap: **Tracen** (handling **Hachimi** for *UM:PD*) and **Astraea** (handling **DaringTact** for *PC:RD (JP)*). Built from scratch with Jetpack Compose and Material 3 Expressive design, it serves as the modern successor to [UmaPatcher-Edge](https://github.com/Tenshou170/UmaPatcher-Edge).

---

## Features

- **Multi-Client Support**: Seamlessly detect and manage multiple client editions with one-tap switching:
  - Japan (JP)
  - Traditional Chinese (TW / HK)
  - Korean (KR)
  - Global (EN)
- **Flexible Patch Targets**:
  - **Installed Game**: Directly inspect and patch the client already installed on your device.
  - **APK Files**: Select and patch standalone APKs, split APK bundles, or XAPK files.
- **Multiple Installation & Export Modes**:
  - **Direct Install (Root)**: Mount patched libraries into the running app without reinstalling.
  - **Shizuku (Elevated ADB)**: Install split APKs and XAPKs directly without requiring root permissions.
  - **Package Installer**: Standard system session installation.
  - **Export File**: Save the patched standalone APK or XAPK directly to storage.
  - **Legacy Installer**: Fallback package installer for older environments.
- **Mod & Plugin Management**:
  - **Automated Mod Updates**: Automatically fetch and update the latest Hachimi-Edge or DaringTact runtime from GitHub releases.
  - **Local Binaries**: Sideload and test local custom `.so` binaries with one tap.
  - **Plugin System**: Sideload and manage extra native `.so` plugins with individual toggle controls.
- **On-Device Library Unpacking (Astraea)**:
  - Unpacks the CrackProof-protected `libil2cpp.so` and `libnative.so` straight from the game's arm64 split using an embedded [Senbei](https://github.com/Tenshou170/Senbei) engine — no desktop tools or manual dump transfers required (arm64 devices only).
- **Modern Storage & DocumentsProvider**:
  - Optional DocumentsProvider export allowing file managers access to `/data/data` (required for Android 16 QPR2+ and Android 17+).
- **Built-in App Updater**:
  - In-app version checks with SHA-256 integrity verification and formatted release changelog viewer.
- **UmaPatcher-Edge Migration**:
  - One-time automatic migration of signing keystores, plugins, and preferences from legacy UmaPatcher-Edge installations.

---

## Game Modes

The dropdown in the hero card picks the game family, and the choice runs through the whole app: colour scheme, dashboard tiles, and the patch flow.

| Mode | Game | Runtime | Supported Flow |
| :--- | :--- | :--- | :--- |
| **Tracen** | UM:PD (JP / TW-HK / KR / EN) | Hachimi-Edge | Full patch flow (Installed app & files) |
| **Astraea** | PC:RD (JP) | DaringTact | Split patching, library unpacking, install or export |

> [!NOTE]
> **Astraea Patching Architecture**  
> Astraea patches the split set instead of an installed app: the base split gets a clean Application class and a manifest redirect past the CrackProof shell, and the arm64 config split gets `libil2cpp.so`, `libnative.so`, and `libdaringtact.so` written in with the `lib__*__.so` stubs removed. The result is re-signed and installs through Shizuku, the system package installer, or an export as split APKs / an XAPK.

> [!WARNING]
> **Important Notices for Astraea Mode**  
> 1. **Store Copy Conflict**: A patched build is signed with your key, so the installer cannot update the store copy. Uninstall the stock game first if installed; your game data lives safely on the server.  
> 2. **One-Way Patch**: Patching is irreversible on the installed build. To revert to stock, uninstall the patched game and reinstall cleanly from the Play Store.

---

## Requirements

| Component | Minimum Requirement | Notes |
| :--- | :--- | :--- |
| **Android OS** | Android 7.0 (API Level 24)+ | Targeted and tested for Android 17 (API Level 37) |
| **Target App (Tracen)** | Supported UM:PD Client | JP, TW/HK, KR, or Global edition |
| **Target App (Astraea)** | PC:RD (JP) | arm64-v8a split; on-device unpack needs arm64 device |
| **Root (Optional)** | Magisk / KernelSU / APatch | Only required for Direct Install mode |
| **Shizuku (Optional)** | Shizuku v13+ | For rootless split APK / XAPK installation |

---

## Download

Grab the latest compiled `.apk` release directly from our **[Releases page](https://github.com/HachiFridge/HachiDroid/releases)**.

---

## Quick Start

1. **Install HachiDroid** from the [Releases](https://github.com/HachiFridge/HachiDroid/releases) page.
2. Launch the app and select your active target client or game mode (**Tracen** or **Astraea**).
3. Choose your input source (**Installed Game** or **APK Files**).
4. Select your preferred **Install Method** (Direct Root, Shizuku, Package Installer, or Export File).
5. Tap **Start patching** and follow the on-screen progress timeline.

For a detailed walkthrough, see the **[Android Installation Guide | Hachimi Edge](https://hachimi.noccu.art/docs/hachimi/installing-android)**.

---

## Building from Source

To compile and package HachiDroid on your workstation, please refer to our comprehensive **[BUILDING.md](BUILDING.md)**.

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
./gradlew assembleRelease
```

---

## Community & Support

- **Discord**: Join our community on [Discord](https://discord.gg/YjBgmuqqYr) for discussions, announcements, and support.
- **Issues**: Report bugs or suggest feature enhancements via GitHub Issues.

---

## License

Distributed under the **GNU General Public License v3.0 (GPL-3.0)** — see the [LICENSE](LICENSE) and [NOTICE](NOTICE) files for details.

### Senbei Bridge (AGPL-3.0)
The optional on-device unpacking links [Senbei](https://github.com/Tenshou170/Senbei), a CrackProof unpacker licensed under **AGPL-3.0-only**. A build that packages `libsenbei_jni.so` therefore carries AGPL-3.0 obligations alongside the GPL-3.0 above; see [NOTICE](NOTICE). Builds made without the Rust toolchain omit the bridge entirely and fall back to libraries the user supplies.
