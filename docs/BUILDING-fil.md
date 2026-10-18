<div align="center">
  <h1>Gabay sa Pagbuo ng HachiDroid</h1>
  <p><b>Mga kinakailangan, pag-setup ng kapaligiran, at mga tagubilin sa pag-compile</b></p>

  <p><a href="../BUILDING.md">English</a> | Filipino | <a href="BUILDING-ja.md">日本語</a> | <a href="BUILDING-ko.md">한국어</a> | <a href="BUILDING-ru.md">Русский</a> | <a href="BUILDING-vi.md">Tiếng Việt</a> | <a href="BUILDING-zh_cn.md">简体中文</a> | <a href="BUILDING-zh_tw.md">繁體中文</a></p>
</div>

---

## Mga Kinakailangan

Siguraduhing naka-install ang mga sumusunod na tool sa iyong computer:

- **JDK 21**: Naka-pin sa `gradle/gradle-daemon-jvm.properties`. Inirerekomenda namin ang [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) o [Amazon Corretto 21](https://aws.amazon.com/corretto/).
- **Android SDK**:
  - SDK Platform: `platforms;android-37` (Android 17)
  - Build Tools: `build-tools;37.0.0`
  - Platform Tools: `platform-tools`
- **Android NDK** *(opsyonal — para lang sa on-device unpack ng Astraea)*: r29 o mas bago (nasubukan sa r30). Kailangan ang r27 pataas para sa 16 KB page alignment.
- **Rust toolchain** *(opsyonal — parehong feature)*: stable na Rust 1.85+ na may target na `aarch64-linux-android` (`rustup target add aarch64-linux-android`).
- **Git**: Para sa version control.

> **Tala**: Ang mga utos sa ibaba ay gumagamit ng Unix syntax (`./gradlew`). Sa Windows, gamitin ang `gradlew.bat` sa Command Prompt o PowerShell.

---

## Pag-set up ng Kapaligiran

1. **I-clone ang repository**:
   ```bash
   git clone https://github.com/HachiFridge/HachiDroid.git
   cd HachiDroid
   ```

2. **I-configure ang Android SDK Path**:
   Gumawa ng `local.properties` file sa root directory ng proyekto na naglalaman ng lokasyon ng iyong Android SDK:
   ```properties
   sdk.dir=/path/to/android/sdk
   ```
   *Halimbawa sa Linux*: `sdk.dir=/home/username/Android/Sdk`  
   *Halimbawa sa Windows*: `sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk`

   Kung mag-i-install ng SDK components gamit ang CLI:
   ```bash
   sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
   ```

---

## Pagbuo (Building)

### Debug Build
Nagko-compile ng debug APK na nilagdaan gamit ang kasamang `debug.keystore`:
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build
Nagko-compile ng optimized at R8-minified na release APK:
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

Upang lagdaan ang release APK gamit ang iyong sariling release key, i-export ang mga sumusunod na environment variable bago mag-build:
```bash
export SIGNING_KEY_FILE="/path/to/release.keystore"
export SIGNING_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="your_key_alias"
export SIGNING_KEY_PASSWORD="your_key_password"
```
*(Sa Windows PowerShell, gamitin ang `$env:VARIABLE_NAME = "value"`)*  
Kung hindi tinukoy ang `SIGNING_KEY_FILE`, gagamitin ng release build ang kasamang debug key bilang fallback.

### Native bridge (opsyonal)

Nasa `app/src/main/rust/senbei-jni` ang on-device na pag-unpack ng library ng Astraea; binubuo ito ng `buildSenbeiJni` Gradle task gamit ang Cargo patungong `lib/arm64-v8a/libsenbei_jni.so`.

```bash
rustup target add aarch64-linux-android
cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Tumatakbo lang ang mga test na gumagamit ng totoong library kapag may ibinigay kang sariling fixture.
```bash
SENBEI_TEST_LIB="/path/to/libil2cpp.so" \
  cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Hinahanap ng Gradle ang NDK sa `ndk.dir` ng `local.properties`, pagkatapos sa `ANDROID_NDK_ROOT` / `ANDROID_NDK_HOME`, at sa pinakabagong NDK sa SDK. Kung walang cargo o NDK, nilalaktawan ang task, walang `.so` na nababalot, at itinatago ng app ang unpack action. Matagumpay pa rin ang normal na build.

### Pagsubok (Testing)
Patakbuhin ang mga unit test ng codebase:
```bash
./gradlew testDebugUnitTest
```

---

## Arkitektura ng Build at mga Asset

- **Extensions Asset (`extensions.dex`)**: Ang bahagi ng DocumentsProvider sa `src/main/extensions` ay awtomatikong kinokompile at ginagawang dex file sa oras ng build bago i-package ang mga asset, at inilalagay sa `app/src/main/assets/extensions.dex`.
- **Open Source Licenses**: Ang plugin na `aboutlibraries` ay awtomatikong bumubuo ng mga depinisyon ng lisensya sa `raw/aboutlibraries.json` habang nagbi-build.
- **Native bridge (`libsenbei_jni.so`)**: binubuo ng `buildSenbeiJni` task ang Cargo crate sa `app/src/main/rust/senbei-jni`, inilalagay sa `app/build/generated/senbeiJniLibs` at nababalot bilang arm64-v8a JNI library. Naka-link dito ang [Senbei](https://github.com/Tenshou170/Senbei) (AGPL-3.0-only) — tingnan ang [NOTICE](../NOTICE).

---

## Mga Detalye ng Build

| Property | Value | Tala |
| :--- | :--- | :--- |
| **Compile SDK** | 37 (Android 17) | Pinakabagong platform API |
| **Min SDK** | 24 (Android 7.0) | Sinusuportahan sa 99%+ ng mga aktibong Android device |
| **Target SDK** | 37 (Android 17) | Buong compatibility sa makabagong Android OS |
| **Java Bytecode** | Java 21 | Na-desugar sa pamamagitan ng D8 para sa API 24+ runtimes |
| **Kotlin** | 2.4.21 | Integrated AGP toolchain |
| **Compose BOM** | 2026.09.00 | Foundation ng Material 3 & Compose UI |
| **Gradle** | 9.8.1 | Wrapper distribution |
| **AGP** | 9.4.1 | Android Gradle Plugin |
| **Native bridge** | Rust + NDK (opsyonal) | `libsenbei_jni.so`, arm64-v8a lang |

---

## Pag-troubleshoot

- **"Unsupported class file major version"**: Ang aktibong `JAVA_HOME` o Gradle JDK sa iyong IDE ay hindi tumutugma sa Java 21. Itakda ang iyong Gradle JDK sa 21 sa Android Studio (`Settings → Build, Execution, Deployment → Build Tools → Gradle`).
- **Nawawalang SDK Platform**: Siguraduhing naka-install ang `platforms;android-37` sa pamamagitan ng `sdkmanager` o Android Studio SDK Manager.
- **Permission Denied sa gradlew**: Patakbuhin ang `chmod +x gradlew` sa Linux/macOS.
- **Walang unpack action / walang `libsenbei_jni.so`**: hindi natagpuan ang cargo o NDK kaya nilaktawan ang `buildSenbeiJni`. I-install ang dalawa (tingnan ang *Native bridge*) at i-rebuild; hindi apektado ang ibang bahagi ng app.
- **Hindi mahanap ng cargo ang Android linker**: itakda ang `ANDROID_NDK_ROOT` (o `ndk.dir` sa `local.properties`) para mahanap ang `aarch64-linux-android24-clang`.
