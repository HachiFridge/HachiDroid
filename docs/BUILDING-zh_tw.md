<div align="center">
  <h1>HachiDroid 建置指南</h1>
  <p><b>環境需求、設定與編譯詳細指示</b></p>

  <p><a href="../BUILDING.md">English</a> | <a href="BUILDING-fil.md">Filipino</a> | <a href="BUILDING-ja.md">日本語</a> | <a href="BUILDING-ko.md">한국어</a> | <a href="BUILDING-ru.md">Русский</a> | <a href="BUILDING-vi.md">Tiếng Việt</a> | <a href="BUILDING-zh_cn.md">简体中文</a> | 繁體中文</p>
</div>

---

## 建置需求

請確認您的開發環境已安裝下列工具：

- **JDK 21**：已鎖定於 `gradle/gradle-daemon-jvm.properties`。建議使用 [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) 或 [Amazon Corretto 21](https://aws.amazon.com/corretto/)。
- **Android SDK**：
  - SDK 平台：`platforms;android-37` (Android 17)
  - 建置工具：`build-tools;37.0.0`
  - 平台工具：`platform-tools`
- **Android NDK** *（選用 — 僅用於 Astraea 的裝置內還原）*：r29 或更新（已於 r30 驗證）。16 KB 頁面對齊要求 r27 以上。
- **Rust 工具鏈** *（選用 — 同一功能）*：穩定版 Rust 1.85 以上，並安裝 `aarch64-linux-android` 目標（`rustup target add aarch64-linux-android`）。
- **Git**：版本控制工具。

> **注意**：以下指令皆以 Unix 終端機語法 (`./gradlew`) 為基準。在 Windows 環境下，請於命令提示字元或 PowerShell 中使用 `gradlew.bat`。

---

## 設定步驟

1. **複製專案庫**：
   ```bash
   git clone https://github.com/HachiFridge/HachiDroid.git
   cd HachiDroid
   ```

2. **設定 Android SDK 路徑**：
   於專案根目錄建立 `local.properties` 檔案並指明 Android SDK 位置：
   ```properties
   sdk.dir=/path/to/android/sdk
   ```
   *Linux 範例*：`sdk.dir=/home/username/Android/Sdk`  
   *Windows 範例*：`sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk`

   若使用 CLI 命令行工具安裝 SDK 元件：
   ```bash
   sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
   ```

---

## 開始建置

### Debug 建置
編譯帶有內附 `debug.keystore` 簽署的測試版 APK：
```bash
./gradlew assembleDebug
```
成品位置：`app/build/outputs/apk/debug/app-debug.apk`

### Release 建置
編譯經過 R8 程式碼混淆與最佳化的高效能 Release 正式版 APK：
```bash
./gradlew assembleRelease
```
成品位置：`app/build/outputs/apk/release/app-release.apk`

若要使用個人簽章金鑰簽署 Release APK，請在建置前匯出下列環境變數：
```bash
export SIGNING_KEY_FILE="/path/to/release.keystore"
export SIGNING_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="your_key_alias"
export SIGNING_KEY_PASSWORD="your_key_password"
```
*(在 Windows PowerShell 中，請使用 `$env:VARIABLE_NAME = "value"` 語法)*  
若未指定 `SIGNING_KEY_FILE`，Release 建置會自動退回使用隨附的除錯金鑰。

### 原生橋接（選用）

Astraea 的裝置內程式庫還原位於 `app/src/main/rust/senbei-jni`，由 `buildSenbeiJni` Gradle 工作透過 Cargo 編譯為 `lib/arm64-v8a/libsenbei_jni.so`。

```bash
rustup target add aarch64-linux-android
cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

使用真實程式庫的測試只有在你提供自行準備的樣本時才會執行。
```bash
SENBEI_TEST_LIB="/path/to/libil2cpp.so" \
  cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Gradle 會依序從 `local.properties` 的 `ndk.dir`、`ANDROID_NDK_ROOT` / `ANDROID_NDK_HOME`、SDK 中最新的 NDK 尋找 NDK。若缺少 cargo 或 NDK，該工作會被略過，不會封裝 `.so`，應用程式會隱藏還原入口，一般建置仍可成功。

### 執行單元測試
執行涵蓋程式庫的完整單元測試：
```bash
./gradlew testDebugUnitTest
```

---

## 建置架構與資產說明

- **Extensions 資產 (`extensions.dex`)**：位於 `src/main/extensions` 的 DocumentsProvider 元件會在資產封裝前於建置期間自動編譯並轉為 dex，隨後嵌入至 `app/src/main/assets/extensions.dex`。
- **開源授權聲明**：建置期間 `aboutlibraries` 外掛程式會自動將相依套件授權資訊匯出至 `raw/aboutlibraries.json`。
- **原生橋接（`libsenbei_jni.so`）**：`buildSenbeiJni` 工作編譯 `app/src/main/rust/senbei-jni` 的 Cargo crate，輸出至 `app/build/generated/senbeiJniLibs`，再以 arm64-v8a JNI 程式庫封裝。它連結 [Senbei](https://github.com/Tenshou170/Senbei)（AGPL-3.0-only）——見 [NOTICE](../NOTICE)。

---

## 建置規格表

| 屬性 | 設定值 | 說明 |
| :--- | :--- | :--- |
| **Compile SDK** | 37 (Android 17) | 最新平台 API |
| **Min SDK** | 24 (Android 7.0) | 涵蓋 99% 以上的現役 Android 裝置 |
| **Target SDK** | 37 (Android 17) | 完整相容現代 Android 系統 |
| **Java 位元組碼** | Java 21 | 透過 D8 脫糖以相容 API 24+ 執行環境 |
| **Kotlin** | 2.4.21 | AGP 內建編譯器 |
| **Compose BOM** | 2026.09.00 | Material 3 與 Compose UI 核心 |
| **Gradle** | 9.8.1 | Wrapper 發行版本 |
| **AGP** | 9.4.1 | Android Gradle 外掛程式 |
| **原生橋接** | Rust + NDK（選用） | `libsenbei_jni.so`，僅 arm64-v8a |

---

## 常見問題排查

- **"Unsupported class file major version"**：目前系統中的 `JAVA_HOME` 或 IDE 的 Gradle JDK 與 Java 21 不相符。請在 Android Studio 設定中將 Gradle JDK 指定為 21（`Settings → Build, Execution, Deployment → Build Tools → Gradle`）。
- **找不到 SDK 平台**：請確認已透過 `sdkmanager` 或 Android Studio SDK 管理器安裝 `platforms;android-37`。
- **gradlew 權限不足**：在 Linux / macOS 下執行 `chmod +x gradlew`。
- **沒有還原入口 / 缺少 `libsenbei_jni.so`**：找不到 cargo 或 NDK，`buildSenbeiJni` 被略過。安裝兩者後（見 *原生橋接*）重新建置；其餘功能不受影響。
- **cargo 找不到 Android 連結器**：設定 `ANDROID_NDK_ROOT`（或 `local.properties` 中的 `ndk.dir`），讓它能定位 `aarch64-linux-android24-clang`。
