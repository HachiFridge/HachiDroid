<div align="center">
  <h1>HachiDroid ビルドガイド</h1>
  <p><b>環境要件、セットアップ、およびコンパイル手順</b></p>

  <p><a href="../BUILDING.md">English</a> | <a href="BUILDING-fil.md">Filipino</a> | 日本語 | <a href="BUILDING-ko.md">한국어</a> | <a href="BUILDING-ru.md">Русский</a> | <a href="BUILDING-vi.md">Tiếng Việt</a> | <a href="BUILDING-zh_cn.md">简体中文</a> | <a href="BUILDING-zh_tw.md">繁體中文</a></p>
</div>

---

## 事前に必要なもの

作業環境に以下のツールがインストールされていることを確認してください:

- **JDK 21**: `gradle/gradle-daemon-jvm.properties` で固定されています。[Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) または [Amazon Corretto 21](https://aws.amazon.com/corretto/) を推奨します。
- **Android SDK**:
  - SDK プラットフォーム: `platforms;android-37` (Android 17)
  - ビルドツール: `build-tools;37.0.0`
  - プラットフォームツール: `platform-tools`
- **Android NDK** *(任意 — Astraea の端末上復元のみ)*: r29 以降（r30 で確認済み）。16 KB ページアラインメントのため r27 以上が必要です。
- **Rust ツールチェーン** *(任意 — 同じ機能)*: 安定版 Rust 1.85 以降と `aarch64-linux-android` ターゲット（`rustup target add aarch64-linux-android`）。
- **Git**: バージョン管理用。

> **注意**: 以下のコマンドは Unix 構文 (`./gradlew`) を使用しています。Windows の場合はコマンドプロンプトまたは PowerShell で `gradlew.bat` を使用してください。

---

## 環境設定

1. **リポジトリのクローン**:
   ```bash
   git clone https://github.com/HachiFridge/HachiDroid.git
   cd HachiDroid
   ```

2. **Android SDK の設定**:
   プロジェクトのルートディレクトリに Android SDK のパスを指定した `local.properties` ファイルを作成します:
   ```properties
   sdk.dir=/path/to/android/sdk
   ```
   *Linux の例*: `sdk.dir=/home/username/Android/Sdk`  
   *Windows の例*: `sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk`

   CLI ツール経由で SDK コンポーネントをインストールする場合:
   ```bash
   sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
   ```

---

## アプリケーションのビルド

### デバッグビルド
バンドルされている `debug.keystore` で署名されたデバッグ APK をコンパイルします:
```bash
./gradlew assembleDebug
```
出力先: `app/build/outputs/apk/debug/app-debug.apk`

### リリースビルド
最適化および R8 による難読化が施されたリリース APK をコンパイルします:
```bash
./gradlew assembleRelease
```
出力先: `app/build/outputs/apk/release/app-release.apk`

独自のキーストアでリリース APK に署名するには、ビルド前に以下の環境変数を設定してください:
```bash
export SIGNING_KEY_FILE="/path/to/release.keystore"
export SIGNING_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="your_key_alias"
export SIGNING_KEY_PASSWORD="your_key_password"
```
*(Windows PowerShell の場合は `$env:VARIABLE_NAME = "value"` を使用)*  
`SIGNING_KEY_FILE` が未指定の場合、リリースビルドは自動的にデバッグキーストアへフォールバックします。

### ネイティブブリッジ（任意）

Astraea の端末上ライブラリ復元は `app/src/main/rust/senbei-jni` にあり、`buildSenbeiJni` Gradle タスクが Cargo 経由でコンパイルして `lib/arm64-v8a/libsenbei_jni.so` にします。

```bash
rustup target add aarch64-linux-android
cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

実際のライブラリを使うテストは、自分で用意したフィクスチャを指定した場合のみ実行されます。
```bash
SENBEI_TEST_LIB="/path/to/libil2cpp.so" \
  cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Gradle は `local.properties` の `ndk.dir`、次に `ANDROID_NDK_ROOT` / `ANDROID_NDK_HOME`、最後に SDK 内の最新 NDK の順で NDK を探します。cargo や NDK がない場合、タスクはスキップされ `.so` は同梱されず、アプリは復元アクションを表示しません。通常のビルドは成功します。

### テストの実行
コードベース全体の単体テストを実行します:
```bash
./gradlew testDebugUnitTest
```

---

## アプリケーションのビルドアーキテクチャ＆アセット

- **Extensions アセット (`extensions.dex`)**: `src/main/extensions` に配置されている DocumentsProvider コンポーネントは、アセットのパッケージ化前にビルド時に自動でコンパイルおよび dex 化され、`app/src/main/assets/extensions.dex` に組み込まれます。
- **オープンソースライセンス**: `aboutlibraries` プラグインにより、ビルド時にオープンソースライセンス定義が `raw/aboutlibraries.json` に自動生成されます。
- **ネイティブブリッジ (`libsenbei_jni.so`)**: `app/src/main/rust/senbei-jni` の Cargo クレートを `buildSenbeiJni` タスクがビルドし、`app/build/generated/senbeiJniLibs` に配置して arm64-v8a の JNI ライブラリとして同梱します。[Senbei](https://github.com/Tenshou170/Senbei)（AGPL-3.0-only）をリンクします — [NOTICE](../NOTICE) を参照。

---

## アプリケーションのビルド仕様一覧

| 項目 | 設定値 | 備考 |
| :--- | :--- | :--- |
| **Compile SDK** | 37 (Android 17) | 最新プラットフォーム API |
| **Min SDK** | 24 (Android 7.0) | アクティブな Android デバイスの 99% 以上に対応 |
| **Target SDK** | 37 (Android 17) | 最新の Android OS との完全な互換性 |
| **Java バイトコード** | Java 21 | API 24+ 環境向けに D8 で脱糖（desugar） |
| **Kotlin** | 2.4.21 | AGP 統合ツールチェーン |
| **Compose BOM** | 2026.09.00 | Material 3 & Compose UI 基盤 |
| **Gradle** | 9.8.1 | ラッパー配布版 |
| **AGP** | 9.4.1 | Android Gradle Plugin |
| **ネイティブブリッジ** | Rust + NDK（任意） | `libsenbei_jni.so`、arm64-v8a のみ |

---

## トラブルシューティング

- **"Unsupported class file major version"**: `JAVA_HOME` または IDE の Gradle JDK が Java 21 と一致していません。Android Studio の設定 (`Settings → Build, Execution, Deployment → Build Tools → Gradle`) で Gradle JDK を 21 に指定してください。
- **SDK プラットフォームが見つからない**: `sdkmanager` または Android Studio の SDK Manager で `platforms;android-37` が正しくインストールされているか確認してください。
- **gradlew の実行権限エラー**: Linux / macOS の場合は `chmod +x gradlew` を実行してください。
- **復元アクションがない / `libsenbei_jni.so` がない**: cargo または NDK が見つからず `buildSenbeiJni` がスキップされました。両方を導入して（*ネイティブブリッジ* 参照）再ビルドしてください。他の機能には影響しません。
- **cargo が Android リンカーを見つけられない**: `ANDROID_NDK_ROOT`（または `local.properties` の `ndk.dir`）を設定し、`aarch64-linux-android24-clang` を解決できるようにしてください。
