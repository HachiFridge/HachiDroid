<div align="center">
  <h1>HachiDroid 构建指南</h1>
  <p><b>环境前置依赖、配置与编译说明</b></p>

  <p><a href="../BUILDING.md">English</a> | <a href="BUILDING-fil.md">Filipino</a> | <a href="BUILDING-ja.md">日本語</a> | <a href="BUILDING-ko.md">한국어</a> | <a href="BUILDING-ru.md">Русский</a> | <a href="BUILDING-vi.md">Tiếng Việt</a> | 简体中文 | <a href="BUILDING-zh_tw.md">繁體中文</a></p>
</div>

---

## 前置依赖

请确保工作站已安装以下开发工具：

- **JDK 21**：已在 `gradle/gradle-daemon-jvm.properties` 中锁定。推荐使用 [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) 或 [Amazon Corretto 21](https://aws.amazon.com/corretto/)。
- **Android SDK**：
  - SDK 平台：`platforms;android-37` (Android 17)
  - 构建工具：`build-tools;37.0.0`
  - 平台工具：`platform-tools`
- **Android NDK** *（可选 — 仅用于 Astraea 的设备内还原）*：r29 或更高（已在 r30 上验证）。16 KB 页对齐要求 r27 以上。
- **Rust 工具链** *（可选 — 同一功能）*：稳定版 Rust 1.85 以上，并安装 `aarch64-linux-android` 目标（`rustup target add aarch64-linux-android`）。
- **Git**：版本控制工具。

> **注意**：以下命令均采用 Unix 终端语法 (`./gradlew`)。在 Windows 系统中，请在命令提示符或 PowerShell 中使用 `gradlew.bat`。

---

## 环境准备

1. **克隆代码库**：
   ```bash
   git clone https://github.com/HachiFridge/HachiDroid.git
   cd HachiDroid
   ```

2. **配置 Android SDK 路径**：
   在项目根目录下创建 `local.properties` 文件并指定 Android SDK 路径：
   ```properties
   sdk.dir=/path/to/android/sdk
   ```
   *Linux 示例*：`sdk.dir=/home/username/Android/Sdk`  
   *Windows 示例*：`sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk`

   若通过命令行工具安装 SDK 组件：
   ```bash
   sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
   ```

---

## 开始构建

### Debug 构建
编译带有内置 `debug.keystore` 签名的测试版 APK：
```bash
./gradlew assembleDebug
```
产物路径：`app/build/outputs/apk/debug/app-debug.apk`

### Release 构建
编译经过 R8 代码压缩与优化的高性能 Release 版 APK：
```bash
./gradlew assembleRelease
```
产物路径：`app/build/outputs/apk/release/app-release.apk`

若需使用自己的签名密钥对 Release APK 签名，请在构建前设置以下环境变量：
```bash
export SIGNING_KEY_FILE="/path/to/release.keystore"
export SIGNING_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="your_key_alias"
export SIGNING_KEY_PASSWORD="your_key_password"
```
*(在 Windows PowerShell 中，使用 `$env:VARIABLE_NAME = "value"` 格式)*  
若未配置 `SIGNING_KEY_FILE`，Release 构建将自动回退使用自带的调试密钥。

### 原生桥接（可选）

Astraea 的设备内库还原位于 `app/src/main/rust/senbei-jni`，由 `buildSenbeiJni` Gradle 任务通过 Cargo 编译为 `lib/arm64-v8a/libsenbei_jni.so`。

```bash
rustup target add aarch64-linux-android
cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

使用真实库的测试只有在你提供自己准备的样本时才会执行。
```bash
SENBEI_TEST_LIB="/path/to/libil2cpp.so" \
  cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Gradle 依次从 `local.properties` 的 `ndk.dir`、`ANDROID_NDK_ROOT` / `ANDROID_NDK_HOME`、SDK 中最新的 NDK 查找 NDK。若缺少 cargo 或 NDK，该任务会被跳过，不会打包 `.so`，应用会隐藏还原入口，普通构建仍可成功。

### 运行单元测试
运行代码库的完整单元测试套件：
```bash
./gradlew testDebugUnitTest
```

---

## 构建架构与资产说明

- **Extensions 资产 (`extensions.dex`)**：位于 `src/main/extensions` 的 DocumentsProvider 代码会在打包资产前自动编译并转换为 dex，最终嵌入于 `app/src/main/assets/extensions.dex`。
- **开源许可证**：构建期间 `aboutlibraries` 插件会自动将依赖许可元数据生成至 `raw/aboutlibraries.json`。
- **原生桥接（`libsenbei_jni.so`）**：`buildSenbeiJni` 任务编译 `app/src/main/rust/senbei-jni` 的 Cargo crate，输出到 `app/build/generated/senbeiJniLibs`，再作为 arm64-v8a JNI 库打包。它链接 [Senbei](https://github.com/Tenshou170/Senbei)（AGPL-3.0-only）——见 [NOTICE](../NOTICE)。

---

## 构建规格明细

| 属性 | 设定值 | 备注 |
| :--- | :--- | :--- |
| **Compile SDK** | 37 (Android 17) | 最新平台 API |
| **Min SDK** | 24 (Android 7.0) | 覆盖 99%+ 的活跃 Android 设备 |
| **Target SDK** | 37 (Android 17) | 现代 Android 系统完全兼容 |
| **Java 字节码** | Java 21 | 经由 D8 脱糖以兼容 API 24+ 运行环境 |
| **Kotlin** | 2.4.21 | AGP 内置编译器 |
| **Compose BOM** | 2026.09.00 | Material 3 与 Compose UI 基础库 |
| **Gradle** | 9.8.1 | Wrapper 封装版本 |
| **AGP** | 9.4.1 | Android Gradle 插件 |
| **原生桥接** | Rust + NDK（可选） | `libsenbei_jni.so`，仅 arm64-v8a |

---

## 常见问题排查

- **"Unsupported class file major version"**：当前 `JAVA_HOME` 或 IDE 的 Gradle JDK 未匹配 Java 21。请在 Android Studio 设置中将 Gradle JDK 指定为 21（`Settings → Build, Execution, Deployment → Build Tools → Gradle`）。
- **缺少 SDK 平台**：请确保已通过 `sdkmanager` 或 Android Studio SDK Manager 正确安装 `platforms;android-37`。
- **gradlew 无执行权限**：在 Linux / macOS 下执行 `chmod +x gradlew`。
- **没有还原入口 / 缺少 `libsenbei_jni.so`**：未找到 cargo 或 NDK，`buildSenbeiJni` 被跳过。安装两者后（见 *原生桥接*）重新构建；其余功能不受影响。
- **cargo 找不到 Android 链接器**：设置 `ANDROID_NDK_ROOT`（或 `local.properties` 中的 `ndk.dir`），使其能定位 `aarch64-linux-android24-clang`。
