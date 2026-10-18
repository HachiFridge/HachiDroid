<div align="center">
  <h1>Hướng dẫn xây dựng HachiDroid</h1>
  <p><b>Yêu cầu môi trường, thiết lập và hướng dẫn biên dịch</b></p>

  <p><a href="../BUILDING.md">English</a> | <a href="BUILDING-fil.md">Filipino</a> | <a href="BUILDING-ja.md">日本語</a> | <a href="BUILDING-ko.md">한국어</a> | <a href="BUILDING-ru.md">Русский</a> | Tiếng Việt | <a href="BUILDING-zh_cn.md">简体中文</a> | <a href="BUILDING-zh_tw.md">繁體中文</a></p>
</div>

---

## Yêu cầu môi trường

Đảm bảo các công cụ sau đã được cài đặt trên máy tính của bạn:

- **JDK 21**: Được ghim trong `gradle/gradle-daemon-jvm.properties`. Khuyến nghị sử dụng [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) hoặc [Amazon Corretto 21](https://aws.amazon.com/corretto/).
- **Android SDK**:
  - Nền tảng SDK: `platforms;android-37` (Android 17)
  - Công cụ xây dựng: `build-tools;37.0.0`
  - Công cụ nền tảng: `platform-tools`
- **Android NDK** *(tùy chọn — chỉ cho khôi phục trên thiết bị của Astraea)*: r29 trở lên (đã kiểm tra với r30). Cần tối thiểu r27 để căn trang 16 KB.
- **Bộ công cụ Rust** *(tùy chọn — cùng tính năng)*: Rust 1.85+ bản ổn định với target `aarch64-linux-android` (`rustup target add aarch64-linux-android`).
- **Git**: Dùng để quản lý mã nguồn.

> **Lưu ý**: Các lệnh bên dưới sử dụng cú pháp Unix (`./gradlew`). Trên Windows, hãy sử dụng `gradlew.bat` trong Command Prompt hoặc PowerShell.

---

## Thiết lập môi trường

1. **Sao chép kho lưu trữ**:
   ```bash
   git clone https://github.com/HachiFridge/HachiDroid.git
   cd HachiDroid
   ```

2. **Cấu hình đường dẫn Android SDK**:
   Tạo tệp `local.properties` tại thư mục gốc của dự án với đường dẫn đến Android SDK của bạn:
   ```properties
   sdk.dir=/path/to/android/sdk
   ```
   *Ví dụ trên Linux*: `sdk.dir=/home/username/Android/Sdk`  
   *Ví dụ trên Windows*: `sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk`

   Nếu cài đặt các thành phần SDK qua CLI:
   ```bash
   sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
   ```

---

## Xây dựng ứng dụng

### Bản Debug
Biên dịch tệp APK debug được ký bằng tệp `debug.keystore` đi kèm:
```bash
./gradlew assembleDebug
```
Đầu ra: `app/build/outputs/apk/debug/app-debug.apk`

### Bản Release
Biên dịch tệp APK phát hành đã được tối ưu hóa và làm gọn bằng R8:
```bash
./gradlew assembleRelease
```
Đầu ra: `app/build/outputs/apk/release/app-release.apk`

Để ký bản APK phát hành bằng khóa ký riêng của bạn, hãy thiết lập các biến môi trường sau trước khi xây dựng:
```bash
export SIGNING_KEY_FILE="/path/to/release.keystore"
export SIGNING_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="your_key_alias"
export SIGNING_KEY_PASSWORD="your_key_password"
```
*(Trên Windows PowerShell, sử dụng cú pháp `$env:VARIABLE_NAME = "value"`)*  
Nếu không chỉ định `SIGNING_KEY_FILE`, bản phát hành sẽ tự động sử dụng khóa debug mặc định.

### Cầu nối native (tùy chọn)

Tính năng khôi phục thư viện trên thiết bị của Astraea nằm ở `app/src/main/rust/senbei-jni`; tác vụ Gradle `buildSenbeiJni` biên dịch nó bằng Cargo thành `lib/arm64-v8a/libsenbei_jni.so`.

```bash
rustup target add aarch64-linux-android
cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Các bài test dùng thư viện thật chỉ chạy khi bạn cung cấp tệp mẫu của riêng mình.
```bash
SENBEI_TEST_LIB="/path/to/libil2cpp.so" \
  cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Gradle tìm NDK theo thứ tự: `ndk.dir` trong `local.properties`, rồi `ANDROID_NDK_ROOT` / `ANDROID_NDK_HOME`, cuối cùng là NDK mới nhất trong SDK. Nếu thiếu cargo hoặc NDK, tác vụ bị bỏ qua, không đóng gói `.so` và ứng dụng ẩn thao tác khôi phục. Bản dựng thông thường vẫn thành công.

### Kiểm thử
Chạy toàn bộ các bài kiểm thử đơn vị:
```bash
./gradlew testDebugUnitTest
```

---

## Kiến trúc xây dựng & Tài nguyên

- **Tài nguyên Extensions (`extensions.dex`)**: Thành phần DocumentsProvider tại `src/main/extensions` được tự động biên dịch và tạo thành tệp dex trước khi đóng gói tài nguyên, nằm tại `app/src/main/assets/extensions.dex`.
- **Giấy phép mã nguồn mở**: Plugin `aboutlibraries` tự động tạo thông tin giấy phép tại `raw/aboutlibraries.json` trong quá trình xây dựng.
- **Cầu nối native (`libsenbei_jni.so`)**: tác vụ `buildSenbeiJni` biên dịch crate `app/src/main/rust/senbei-jni`, đặt kết quả vào `app/build/generated/senbeiJniLibs` rồi đóng gói thành thư viện JNI arm64-v8a. Nó liên kết [Senbei](https://github.com/Tenshou170/Senbei) (AGPL-3.0-only) — xem [NOTICE](../NOTICE).

---

## Thông số kỹ thuật xây dựng

| Thuộc tính | Giá trị | Ghi chú |
| :--- | :--- | :--- |
| **Compile SDK** | 37 (Android 17) | API nền tảng mới nhất |
| **Min SDK** | 24 (Android 7.0) | Tương thích hơn 99% thiết bị Android hiện có |
| **Target SDK** | 37 (Android 17) | Hoàn toàn tương thích với Android hiện đại |
| **Java Bytecode** | Java 21 | Được desugar qua D8 cho môi trường API 24+ |
| **Kotlin** | 2.4.21 | Trình biên dịch tích hợp trong AGP |
| **Compose BOM** | 2026.09.00 | Nền tảng Material 3 & Compose UI |
| **Gradle** | 9.8.1 | Bản phân phối wrapper |
| **AGP** | 9.4.1 | Android Gradle Plugin |
| **Cầu nối native** | Rust + NDK (tùy chọn) | `libsenbei_jni.so`, chỉ arm64-v8a |

---

## Khắc phục sự cố

- **"Unsupported class file major version"**: `JAVA_HOME` hoặc Gradle JDK trong IDE không khớp với Java 21. Hãy đặt Gradle JDK thành 21 trong cài đặt Android Studio (`Settings → Build, Execution, Deployment → Build Tools → Gradle`).
- **Thiếu nền tảng SDK**: Đảm bảo `platforms;android-37` đã được cài đặt qua `sdkmanager` hoặc SDK Manager trong Android Studio.
- **Lỗi quyền thực thi gradlew**: Chạy `chmod +x gradlew` trên Linux/macOS.
- **Không có thao tác khôi phục / thiếu `libsenbei_jni.so`**: không tìm thấy cargo hoặc NDK nên `buildSenbeiJni` bị bỏ qua. Cài cả hai (xem *Cầu nối native*) và dựng lại; phần còn lại của ứng dụng không bị ảnh hưởng.
- **cargo không tìm thấy linker Android**: đặt `ANDROID_NDK_ROOT` (hoặc `ndk.dir` trong `local.properties`) để tác vụ tìm được `aarch64-linux-android24-clang`.
