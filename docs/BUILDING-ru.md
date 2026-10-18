<div align="center">
  <h1>Руководство по сборке HachiDroid</h1>
  <p><b>Системные требования, настройка среды и команды компиляции</b></p>

  <p><a href="../BUILDING.md">English</a> | <a href="BUILDING-fil.md">Filipino</a> | <a href="BUILDING-ja.md">日本語</a> | <a href="BUILDING-ko.md">한국어</a> | Русский | <a href="BUILDING-vi.md">Tiếng Việt</a> | <a href="BUILDING-zh_cn.md">简体中文</a> | <a href="BUILDING-zh_tw.md">繁體中文</a></p>
</div>

---

## Предварительные требования

Убедитесь, что на вашей системе установлены следующие инструменты:

- **JDK 21**: Зафиксирован в `gradle/gradle-daemon-jvm.properties`. Рекомендуется [Eclipse Temurin 21](https://adoptium.net/temurin/releases/?version=21) или [Amazon Corretto 21](https://aws.amazon.com/corretto/).
- **Android SDK**:
  - Платформа SDK: `platforms;android-37` (Android 17)
  - Инструменты сборки: `build-tools;37.0.0`
  - Платформенные утилиты: `platform-tools`
- **Android NDK** *(необязательно — только для восстановления на устройстве в Astraea)*: r29 или новее (проверено на r30). Для выравнивания страниц по 16 KB нужен как минимум r27.
- **Инструменты Rust** *(необязательно — та же функция)*: стабильный Rust 1.85+ с целью `aarch64-linux-android` (`rustup target add aarch64-linux-android`).
- **Git**: Для управления версиями.

> **Примечание**: Команды ниже приведены для Unix-терминалов (`./gradlew`). В Windows используйте `gradlew.bat` в командной строке или PowerShell.

---

## Настройка окружения

1. **Клонирование репозитория**:
   ```bash
   git clone https://github.com/HachiFridge/HachiDroid.git
   cd HachiDroid
   ```

2. **Настройка пути к Android SDK**:
   Создайте файл `local.properties` в корне проекта с указанием расположения вашего Android SDK:
   ```properties
   sdk.dir=/path/to/android/sdk
   ```
   *Пример для Linux*: `sdk.dir=/home/username/Android/Sdk`  
   *Пример для Windows*: `sdk.dir=C:\\Users\\username\\AppData\\Local\\Android\\Sdk`

   Установка компонентов через CLI:
   ```bash
   sdkmanager "platforms;android-37" "build-tools;37.0.0" "platform-tools"
   ```

---

## Сборка приложения

### Сборка приложения Debug
Компиляция отладочного APK с подписью встроенным `debug.keystore`:
```bash
./gradlew assembleDebug
```
Результат: `app/build/outputs/apk/debug/app-debug.apk`

### Сборка приложения Release
Компиляция оптимизированного Release APK с минификацией R8:
```bash
./gradlew assembleRelease
```
Результат: `app/build/outputs/apk/release/app-release.apk`

Для подписи релизного APK собственным ключом экспортируйте следующие переменные окружения перед сборкой:
```bash
export SIGNING_KEY_FILE="/path/to/release.keystore"
export SIGNING_STORE_PASSWORD="your_keystore_password"
export SIGNING_KEY_ALIAS="your_key_alias"
export SIGNING_KEY_PASSWORD="your_key_password"
```
*(В Windows PowerShell используйте синтаксис `$env:VARIABLE_NAME = "value"`)*  
Если `SIGNING_KEY_FILE` не задан, релизная сборка автоматически использует отладочный ключ.

### Нативный мост (необязательно)

Восстановление библиотек на устройстве в Astraea находится в `app/src/main/rust/senbei-jni`; задача Gradle `buildSenbeiJni` собирает его через Cargo в `lib/arm64-v8a/libsenbei_jni.so`.

```bash
rustup target add aarch64-linux-android
cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Тесты с реальной библиотекой выполняются только если указать собственную заготовку.
```bash
SENBEI_TEST_LIB="/path/to/libil2cpp.so" \
  cargo test --release --manifest-path app/src/main/rust/senbei-jni/Cargo.toml
```

Gradle ищет NDK в `ndk.dir` из `local.properties`, затем в `ANDROID_NDK_ROOT` / `ANDROID_NDK_HOME`, затем берёт самый новый NDK в SDK. Без cargo или NDK задача пропускается, `.so` не попадает в сборку, а приложение скрывает действие восстановления. Обычная сборка при этом проходит.

### Запуск тестов
Запуск набора модульных тестов:
```bash
./gradlew testDebugUnitTest
```

---

## Архитектура сборки и ресурсы

- **Ресурс Extensions (`extensions.dex`)**: Компонент DocumentsProvider из `src/main/extensions` автоматически компилируется и упаковывается в dex на этапе сборки перед сборкой assets, помещаясь в `app/src/main/assets/extensions.dex`.
- **Лицензии открытого ПО**: Плагин `aboutlibraries` генерирует определения лицензий зависимостей в `raw/aboutlibraries.json` во время сборки.
- **Нативный мост (`libsenbei_jni.so`)**: задача `buildSenbeiJni` собирает crate `app/src/main/rust/senbei-jni`, кладёт результат в `app/build/generated/senbeiJniLibs` и упаковывает как JNI-библиотеку arm64-v8a. Он линкует [Senbei](https://github.com/Tenshou170/Senbei) (AGPL-3.0-only) — см. [NOTICE](../NOTICE).

---

## Спецификации сборки

| Параметр | Значение | Примечания |
| :--- | :--- | :--- |
| **Compile SDK** | 37 (Android 17) | Актуальный API платформы |
| **Min SDK** | 24 (Android 7.0) | Поддержка более 99% активных устройств Android |
| **Target SDK** | 37 (Android 17) | Полная совместимость с современными версиями Android |
| **Байт-код Java** | Java 21 | Десахаризация через D8 для совместимости с API 24+ |
| **Kotlin** | 2.4.21 | Интегрированный компилятор AGP |
| **Compose BOM** | 2026.09.00 | Основа интерфейса Material 3 и Compose |
| **Gradle** | 9.8.1 | Дистрибутив обертки (wrapper) |
| **AGP** | 9.4.1 | Android Gradle Plugin |
| **Нативный мост** | Rust + NDK (необязательно) | `libsenbei_jni.so`, только arm64-v8a |

---

## Решение проблем

- **"Unsupported class file major version"**: Текущая версия `JAVA_HOME` или Gradle JDK в IDE не соответствует Java 21. Укажите версию JDK 21 в настройках Android Studio (`Settings → Build, Execution, Deployment → Build Tools → Gradle`).
- **Отсутствует платформа SDK**: Убедитесь, что компонент `platforms;android-37` установлен через `sdkmanager` или SDK Manager в Android Studio.
- **Ошибка прав доступа для gradlew**: Выполните команду `chmod +x gradlew` в Linux/macOS.
- **Нет действия восстановления / отсутствует `libsenbei_jni.so`**: не найдены cargo или NDK, поэтому `buildSenbeiJni` был пропущен. Установите оба (см. *Нативный мост*) и пересоберите; остальное приложение работает как обычно.
- **cargo не находит линкер Android**: задайте `ANDROID_NDK_ROOT` (или `ndk.dir` в `local.properties`), чтобы задача нашла `aarch64-linux-android24-clang`.
