<div align="center">
  <h1>Building HachiDroid from Source</h1>
  <p><b>A complete guide to environment setup, dependency configuration, and compilation</b></p>

  <p>English | <a href="docs/BUILDING-fil.md">Filipino</a> | <a href="docs/BUILDING-ja.md">日本語</a> | <a href="docs/BUILDING-ko.md">한국어</a> | <a href="docs/BUILDING-ru.md">Русский</a> | <a href="docs/BUILDING-vi.md">Tiếng Việt</a> | <a href="docs/BUILDING-zh_cn.md">简体中文</a> | <a href="docs/BUILDING-zh_tw.md">繁體中文</a></p>
</div>

---

## Prerequisites

Before building, ensure your workstation meets the following requirements:

### 1. Java Development Kit (JDK)
- **JDK 21** is required (pinned in `gradle/gradle-daemon-jvm.properties` for both Gradle daemon and compilation).
- Recommended distributions: [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) or [Amazon Corretto 21](https://aws.amazon.com/corretto/).

### 2. Android SDK
The following Android SDK components must be installed:
- **SDK Platform**: `platforms;android-37` (Android 17)
- **Build-Tools**: `37.0.0`
- **Platform-Tools**

### 3. Native Bridge Toolchain (Optional)
Required only for compiling the embedded Senbei unpacker bridge (`libsenbei_jni.so`) used by Astraea on-device library unpacking:
- **Android NDK**: r29 or newer (tested with r30; r27+ required for 16 KB page alignment).
- **Rust Toolchain**: Stable Rust 1.85+ with the `aarch64-linux-android` target:
  ```bash
  rustup target add aarch64-linux-android
  ```

### 4. Git
Required to clone the repository and manage version control.

---

## Environment Setup

### Step 1: Clone the Repository

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
```

### Step 2: Configure Android SDK

Create a `local.properties` file in the project root directory:

**Linux / macOS:**
```properties
sdk.dir=/home/username/Android/Sdk
```

**Windows:**
```properties
sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk
```

If installing components via the Android CLI `sdkmanager`:
```bash
sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
```

### Step 3: Configure JDK Path (Optional but Recommended)

Create or edit your global Gradle configuration (`~/.gradle/gradle.properties` or `%USERPROFILE%\.gradle\gradle.properties`):
```properties
org.gradle.java.installations.paths=/path/to/jdk-21
```

---

## Release Signing Configuration

By default, debug builds use the bundled `debug.keystore`. For release builds with custom signing, export the following environment variables:

**Linux / macOS:**
```bash
export SIGNING_KEY_FILE="/path/to/release.keystore"
export SIGNING_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="your_key_alias"
export SIGNING_KEY_PASSWORD="your_key_password"
```

**Windows (PowerShell):**
```powershell
$env:SIGNING_KEY_FILE = "C:\path\to\release.keystore"
$env:SIGNING_STORE_PASSWORD = "your_keystore_password"
$env:SIGNING_KEY_ALIAS = "your_key_alias"
$env:SIGNING_KEY_PASSWORD = "your_key_password"
```

If `SIGNING_KEY_FILE` is not set, release builds fall back to the bundled debug key.

---

## Building the Application

### Debug Build

**Linux / macOS:**
```bash
./gradlew assembleDebug
```

**Windows (Command Prompt):**
```cmd
gradlew.bat assembleDebug
```

**Windows (PowerShell):**
```powershell
.\gradlew.bat assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

**Linux / macOS:**
```bash
./gradlew assembleRelease
```

**Windows (Command Prompt):**
```cmd
gradlew.bat assembleRelease
```

**Windows (PowerShell):**
```powershell
.\gradlew.bat assembleRelease
```

Output APK: `app/build/outputs/apk/release/app-release.apk`

### Native Bridge (Optional)

Astraea's on-device library unpacking crate lives in `app/src/main/rust/senbei-jni` and is compiled automatically by the `buildSenbeiJni` Gradle task when Cargo and NDK are present:

```bash
cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Fixture test against a real library:
```bash
SENBEI_TEST_LIB="/path/to/libil2cpp.so" \
  cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

> [!NOTE]
> Gradle resolves the NDK from `ndk.dir` in `local.properties`, `ANDROID_NDK_ROOT` / `ANDROID_NDK_HOME`, or the newest NDK under the SDK. Without Cargo or an NDK, the task is skipped and the app hides the on-device unpack action; standard builds succeed unaffected.

### Running Unit Tests

Execute the full unit test suite:
```bash
./gradlew testDebugUnitTest
```

### Build with Android Studio

1. Open Android Studio.
2. Select **Open an Existing Project** and navigate to the cloned `HachiDroid` directory.
3. Android Studio will automatically recognize `local.properties`.
4. Ensure Gradle JDK is configured to JDK 21 under `Settings → Build, Execution, Deployment → Build Tools → Gradle`.
5. Build via **Build → Make Project** or **Build → Build Bundle(s) / APK(s) → Build APK(s)**.

---

## Build Architecture & Assets

- **Extensions Asset (`extensions.dex`)**: The DocumentsProvider component located in `src/main/extensions` is automatically compiled and dexed into `app/src/main/assets/extensions.dex` during build time before asset packaging.
- **Open Source Licenses**: The `aboutlibraries` plugin generates open-source license definitions into `raw/aboutlibraries.json` during build.
- **Native Bridge (`libsenbei_jni.so`)**: `app/src/main/rust/senbei-jni` is built by `buildSenbeiJni` and staged into `app/build/generated/senbeiJniLibs` for packaging as an arm64-v8a JNI library. Links [Senbei](https://github.com/Tenshou170/Senbei) (AGPL-3.0-only) — see [NOTICE](NOTICE).

---

## Build Configuration

| Property | Value | Notes |
| :--- | :--- | :--- |
| **Compile SDK** | 37 (Android 17) | Latest Android platform API |
| **Min SDK** | 24 (Android 7.0) | Supported on 99%+ of active Android devices |
| **Target SDK** | 37 (Android 17) | Full compatibility with modern Android OS |
| **Java Bytecode** | Java 21 | Desugared via D8 for API 24+ runtimes |
| **Kotlin** | 2.4.21 | Integrated AGP toolchain |
| **Compose BOM** | 2026.09.00 | Material 3 & Compose UI foundation |
| **Gradle** | 9.8.1 | Wrapper distribution |
| **AGP** | 9.4.1 | Android Gradle Plugin |
| **Native Bridge** | Rust + NDK (Optional) | `libsenbei_jni.so`, arm64-v8a only |

---

## Troubleshooting

### Build Fails with "Unsupported class file major version"
**Cause:** Your active `JAVA_HOME` or IDE Gradle JDK does not match Java 21.  
**Solution:** Set your Gradle JDK to 21 in Android Studio (`Settings → Build, Execution, Deployment → Build Tools → Gradle`), or export `JAVA_HOME`:
```bash
export JAVA_HOME=/path/to/jdk-21
```

### Android SDK not found / Missing SDK Platform
**Cause:** `local.properties` is missing or `platforms;android-37` is not installed.  
**Solution:** Verify `local.properties` points to your SDK directory, and install API 37 via `sdkmanager "platforms;android-37" "build-tools;37.0.0"`.

### Gradle Wrapper Permission Denied (Linux / macOS)
**Solution:**
```bash
chmod +x gradlew
./gradlew assembleDebug
```

### Unpack action missing / no `libsenbei_jni.so`
**Cause:** Cargo or Android NDK was not detected during build time, so `buildSenbeiJni` was skipped.  
**Solution:** Install NDK r29+ and Rust `aarch64-linux-android` target, then set `ANDROID_NDK_ROOT` and re-run the build.

### `cargo` cannot find an Android linker
**Cause:** Toolchain linker resolution failed.  
**Solution:** Set `ANDROID_NDK_ROOT` (or `ndk.dir` in `local.properties`) so Cargo can resolve `aarch64-linux-android24-clang`.

### Out of Memory (OOM) during build
**Solution:** Increase Gradle heap allocation in `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

---

## Notes

- The Gradle wrapper (`gradlew` / `gradlew.bat`) automatically provisions the correct Gradle version.
- First-time builds may take longer while dependencies and plugins are downloaded.
- Task outputs and configuration cache ensure incremental rebuilds are fast.
- For CI/CD pipelines, configure secrets and environment variables for signing keys securely.
