<div align="center">
  <img src="../images/AppIcon.png" width="128" height="128" alt="HachiDroid icon">
  <h1>HachiDroid</h1>
  <p><b>Патчер и менеджер модов нового поколения для UM:PD и PC:RD</b></p>

  <p><a href="../README.md">English</a> | <a href="README-fil.md">Filipino</a> | <a href="README-ja.md">日本語</a> | <a href="README-ko.md">한국어</a> | Русский | <a href="README-vi.md">Tiếng Việt</a> | <a href="README-zh_cn.md">简体中文</a> | <a href="README-zh_tw.md">繁體中文</a></p>

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

HachiDroid — это открытое Android-приложение для внедрения и патчинга модовых рантаймов в клиенты мобильных игр. В нём два семейства игр: **Tracen** для **UM:PD** с рантаймом **Hachimi** и **Astraea** для **PC:RD (JP)** с рантаймом **DaringTact**; переключение — одним элементом на главном экране. Приложение написано с нуля на Jetpack Compose и Material 3 Expressive и является современным преемником [UmaPatcher-Edge](https://github.com/Tenshou170/UmaPatcher-Edge).

---

## Основные возможности

- **Поддержка нескольких клиентов**: Быстрое переключение и управление целевыми изданиями в одно касание:
  - Япония (JP)
  - Традиционный китайский (TW / HK)
  - Корея (KR)
  - Глобальная версия (EN)
- **Гибкие цели для патчинга**:
  - **Установленная игра**: Прямое обнаружение и патчинг клиента, уже установленного на устройстве.
  - **Файлы APK**: Выбор и патчинг автономных APK, наборов split-APK или файлов XAPK.
- **Различные режимы установки и экспорта**:
  - **Прямая установка (Root)**: Монтирование пропатченных библиотек в работающее приложение без переустановки.
  - **Shizuku (ADB с повышенными правами)**: Установка split-APK и XAPK без Root-прав.
  - **Системный установщик**: Стандартная установка через сессионный установщик системы.
  - **Экспорт файла**: Сохранение пропатченного автономного APK или XAPK напрямую в память устройства.
  - **Классический установщик**: Резервный установщик для старых версий Android.
- **Управление модом Hachimi**:
  - Автоматическая загрузка и обновление последней среды Hachimi-Edge из релизов GitHub.
  - Возможность загрузки локальных пользовательских бинарных файлов `.so` в одно нажатие.
- **Система плагинов**: Подключение и управление дополнительными нативными плагинами `.so` с раздельным включением.
- **Встроенное обновление**: Проверка обновлений внутри приложения с валидацией контрольных сумм SHA-256 и удобным просмотром списка изменений.
- **Современная работа с хранилищем и DocumentsProvider**: Экспорт DocumentsProvider для доступа файловых менеджеров к папке `/data/data` (требуется на Android 16 QPR2+ и Android 17+).
- **Миграция с UmaPatcher-Edge**: Быстрый перенос ключей подписи, плагинов и настроек из установленного ранее UmaPatcher-Edge.
- **Игровые режимы**: Tracen (UM:PD · Hachimi) и Astraea (PC:RD JP · DaringTact) со своей палитрой и панелью.
- **Распаковка библиотек на устройстве (Astraea)**: встроенный движок [Senbei](https://github.com/Tenshou170/Senbei) распаковывает защищённые CrackProof `libil2cpp.so` и `libnative.so` прямо из arm64-сплита игры — без инструментов для ПК и без повторной загрузки чьих-либо файлов. Только устройства arm64.
- **Предупреждение о необратимости**: режим Astraea прямо сообщает, что пропатченную PC:RD нельзя вернуть к исходному состоянию; чтобы играть без модов, нужна переустановка из Play Маркета.

---

## Игровые режимы

Семейство игр выбирается в выпадающем списке на карточке, и выбор проходит через всё приложение: палитра, плитки панели и процесс патчинга.

| Режим | Игра | Рантайм | Состояние |
| :--- | :--- | :--- | :--- |
| **Tracen** | UM:PD (JP / TW-HK / KR / EN) | Hachimi-Edge | Полный процесс патчинга |
| **Astraea** | PC:RD (JP) | DaringTact | Патчинг сплитов, распаковка библиотек, установка или экспорт |

Astraea патчит сплиты игры, а не установленное приложение: в base-сплит добавляется чистый класс Application и правка манифеста в обход оболочки CrackProof, а в arm64 config-сплит удаляются заглушки `lib__*__.so` и записываются `libil2cpp.so`, `libnative.so` и `libdaringtact.so`. Результат подписывается заново и устанавливается через Shizuku, системный установщик или экспортируется как сплиты APK / XAPK.

Два следствия сказаны и в самом приложении: пропатченная сборка подписана ключом пользователя, поэтому не обновляет версию из магазина (игру нужно сначала удалить), и восстановления к исходному состоянию нет — оболочку, которую убирает патч, вернуть нельзя, единственный путь назад — переустановка из Play Маркета.

---

## Системные требования

| Компонент | Минимальные требования | Примечания |
| :--- | :--- | :--- |
| **ОС Android** | Android 7.0 (API Level 24)+ | Скомпилировано и протестировано для Android 17 (API 37) |
| **Целевое приложение** | Поддерживаемый клиент UM:PD | Издания JP, TW/HK, KR или Global |
| **Цель Astraea** | PC:RD (JP) | arm64-v8a сплит; восстановление на устройстве требует arm64 |
| **Root (опционально)** | Magisk / KernelSU / APatch | Требуется только для режима прямой установки |
| **Shizuku (опционально)** | Shizuku v13+ | Для установки split-APK / XAPK без Root-прав |

---

## Быстрый старт

1. **Установите HachiDroid** со страницы **[Releases](https://github.com/HachiFridge/HachiDroid/releases)**.
2. Откройте приложение и выберите целевой клиент в **Студии патчинга**.
3. Выберите источник (**Установленная игра** или **Файлы APK**).
4. Выберите удобный **Способ установки** (Direct Root, Shizuku, Системный установщик или Экспорт файла).
5. Нажмите **Начать патчинг** и следуйте инструкциям на экране.

Подробное руководство: **[Установка Hachimi на Android](https://hachimi.noccu.art/docs/hachimi/installing-android)**

---

## Сборка из исходного кода

Необходимые инструменты, настройка среды и команды Gradle подробно описаны в **[BUILDING-ru.md](BUILDING-ru.md)**.

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
./gradlew assembleRelease
```

---

## Сообщество и поддержка

- **Discord**: Присоединяйтесь к нашему серверу в [Discord](https://discord.gg/YjBgmuqqYr) для обсуждений и помощи.
- **Issues**: Сообщайте об ошибках и предлагайте новые функции через GitHub Issues.

---

## Лицензия

Проект распространяется под лицензией GNU General Public License v3.0 (GPL-3.0) — подробнее см. в файлах [LICENSE](../LICENSE) и [NOTICE](../NOTICE).

### Мост Senbei

Необязательное восстановление на устройстве линкует [Senbei](https://github.com/Tenshou170/Senbei) — распаковщик CrackProof под лицензией **AGPL-3.0-only**. Поэтому сборки с `libsenbei_jni.so` несут обязательства AGPL-3.0 в дополнение к GPL-3.0 выше; см. [NOTICE](../NOTICE). Без инструментов Rust мост просто не собирается.
