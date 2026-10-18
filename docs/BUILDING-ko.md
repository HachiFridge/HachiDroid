<div align="center">
  <h1>HachiDroid 빌드 가이드</h1>
  <p><b>사전 요구 사항, 환경 설정 및 컴파일 안내</b></p>

  <p><a href="../BUILDING.md">English</a> | <a href="BUILDING-fil.md">Filipino</a> | <a href="BUILDING-ja.md">日本語</a> | 한국어 | <a href="BUILDING-ru.md">Русский</a> | <a href="BUILDING-vi.md">Tiếng Việt</a> | <a href="BUILDING-zh_cn.md">简体中文</a> | <a href="BUILDING-zh_tw.md">繁體中文</a></p>
</div>

---

## 사전 요구 사항

개발 환경에 다음 도구들이 설치되어 있는지 확인하세요:

- **JDK 21**: `gradle/gradle-daemon-jvm.properties`에 지정되어 있습니다. [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) 또는 [Amazon Corretto 21](https://aws.amazon.com/corretto/)을 권장합니다.
- **Android SDK**:
  - SDK 플랫폼: `platforms;android-37` (Android 17)
  - 빌드 도구: `build-tools;37.0.0`
  - 플랫폼 도구: `platform-tools`
- **Android NDK** *(선택 — Astraea의 기기 내 복원 전용)*: r29 이상(r30에서 확인). 16 KB 페이지 정렬을 위해 r27 이상이 필요합니다.
- **Rust 툴체인** *(선택 — 같은 기능)*: 안정판 Rust 1.85 이상과 `aarch64-linux-android` 타깃(`rustup target add aarch64-linux-android`).
- **Git**: 버전 관리용.

> **참고**: 아래 명령어는 Unix 셸 구문(`./gradlew`)을 기준으로 합니다. Windows의 명령 프롬프트나 PowerShell에서는 `gradlew.bat`을 사용하세요.

---

## 환경 구성

1. **저장소 복제**:
   ```bash
   git clone https://github.com/HachiFridge/HachiDroid.git
   cd HachiDroid
   ```

2. **Android SDK 경로 설정**:
   프로젝트 루트에 Android SDK 위치를 가리키는 `local.properties` 파일을 생성합니다:
   ```properties
   sdk.dir=/path/to/android/sdk
   ```
   *Linux 예시*: `sdk.dir=/home/username/Android/Sdk`  
   *Windows 예시*: `sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk`

   CLI로 SDK 구성 요소를 설치하는 경우:
   ```bash
   sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
   ```

---

## 애플리케이션 빌드

### 디버그 빌드
기본 제공되는 `debug.keystore`로 서명된 디버그 APK를 생성합니다:
```bash
./gradlew assembleDebug
```
출력 경로: `app/build/outputs/apk/debug/app-debug.apk`

### 릴리스 빌드
최적화 및 R8 난독화가 적용된 릴리스 APK를 빌드합니다:
```bash
./gradlew assembleRelease
```
출력 경로: `app/build/outputs/apk/release/app-release.apk`

자체 키스토어로 릴리스 APK를 서명하려면 빌드하기 전에 다음 환경 변수를 설정하세요:
```bash
export SIGNING_KEY_FILE="/path/to/release.keystore"
export SIGNING_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="your_key_alias"
export SIGNING_KEY_PASSWORD="your_key_password"
```
*(Windows PowerShell에서는 `$env:VARIABLE_NAME = "value"` 형식 사용)*  
`SIGNING_KEY_FILE`을 지정하지 않으면 릴리스 빌드는 자동으로 기본 디버그 키를 사용합니다.

### 네이티브 브리지 (선택)

Astraea의 기기 내 라이브러리 복원은 `app/src/main/rust/senbei-jni`에 있으며, `buildSenbeiJni` Gradle 태스크가 Cargo로 컴파일해 `lib/arm64-v8a/libsenbei_jni.so`로 만듭니다.

```bash
rustup target add aarch64-linux-android
cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

실제 라이브러리를 사용하는 테스트는 직접 준비한 픽스처를 지정할 때만 실행됩니다.
```bash
SENBEI_TEST_LIB="/path/to/libil2cpp.so" \
  cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Gradle은 `local.properties`의 `ndk.dir`, 그다음 `ANDROID_NDK_ROOT` / `ANDROID_NDK_HOME`, 마지막으로 SDK 내 최신 NDK 순으로 NDK를 찾습니다. cargo나 NDK가 없으면 태스크가 건너뛰어지고 `.so`가 포함되지 않으며 앱은 복원 동작을 숨깁니다. 일반 빌드는 그대로 성공합니다.

### 테스트 실행
코드베이스 전체의 단위 테스트를 실행합니다:
```bash
./gradlew testDebugUnitTest
```

---

## 애플리케이션 빌드 아키텍처 및 에셋

- **Extensions 에셋 (`extensions.dex`)**: `src/main/extensions`에 위치한 DocumentsProvider 구성 요소는 빌드 도중 에셋 패키징 전에 자동으로 컴파일 및 dex 변환되어 `app/src/main/assets/extensions.dex`에 포함됩니다.
- **오픈소스 라이선스**: 빌드 시 `aboutlibraries` 플러그인이 `raw/aboutlibraries.json`에 라이선스 메타데이터를 자동 생성합니다.
- **네이티브 브리지 (`libsenbei_jni.so`)**: `app/src/main/rust/senbei-jni`의 Cargo 크레이트를 `buildSenbeiJni` 태스크가 빌드해 `app/build/generated/senbeiJniLibs`에 배치하고 arm64-v8a JNI 라이브러리로 패키징합니다. [Senbei](https://github.com/Tenshou170/Senbei)(AGPL-3.0-only)를 링크합니다 — [NOTICE](../NOTICE) 참고.

---

## 애플리케이션 빌드 사양

| 속성 | 값 | 비고 |
| :--- | :--- | :--- |
| **Compile SDK** | 37 (Android 17) | 최신 플랫폼 API |
| **Min SDK** | 24 (Android 7.0) | 활성 Android 기기의 99% 이상 지원 |
| **Target SDK** | 37 (Android 17) | 최신 Android OS 완벽 호환 |
| **Java 바이트코드** | Java 21 | API 24+ 런타임을 위해 D8에서 디슈거링됨 |
| **Kotlin** | 2.4.21 | 통합 AGP 툴체인 |
| **Compose BOM** | 2026.09.00 | Material 3 및 Compose UI 기반 |
| **Gradle** | 9.8.1 | 래퍼 배포판 |
| **AGP** | 9.4.1 | Android Gradle 플러그인 |
| **네이티브 브리지** | Rust + NDK (선택) | `libsenbei_jni.so`, arm64-v8a 전용 |

---

## 문제 해결

- **"Unsupported class file major version"**: 활성화된 `JAVA_HOME` 또는 IDE의 Gradle JDK가 Java 21과 일치하지 않습니다. Android Studio 설정(`Settings → Build, Execution, Deployment → Build Tools → Gradle`)에서 Gradle JDK를 21로 변경하세요.
- **SDK 플랫폼 누락**: `sdkmanager` 또는 Android Studio SDK Manager를 통해 `platforms;android-37`이 설치되었는지 확인하세요.
- **gradlew 실행 권한 오류**: Linux/macOS 환경에서는 `chmod +x gradlew`를 실행하세요.
- **복원 동작이 없음 / `libsenbei_jni.so` 없음**: cargo 또는 NDK를 찾지 못해 `buildSenbeiJni`가 건너뛰어졌습니다. 둘 다 설치하고(*네이티브 브리지* 참고) 다시 빌드하세요. 나머지 기능은 영향을 받지 않습니다.
- **cargo가 Android 링커를 찾지 못함**: `ANDROID_NDK_ROOT`(또는 `local.properties`의 `ndk.dir`)를 설정해 `aarch64-linux-android24-clang`을 찾을 수 있게 하세요.
