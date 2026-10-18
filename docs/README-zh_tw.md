<div align="center">
  <img src="../images/AppIcon.png" width="128" height="128" alt="HachiDroid icon">
  <h1>HachiDroid</h1>
  <p><b>面向 UM:PD 與 PC:RD 的次世代 Android MOD 修補與管理工具</b></p>

  <p><a href="../README.md">English</a> | <a href="README-fil.md">Filipino</a> | <a href="README-ja.md">日本語</a> | <a href="README-ko.md">한국어</a> | <a href="README-ru.md">Русский</a> | <a href="README-vi.md">Tiếng Việt</a> | <a href="README-zh_cn.md">简体中文</a> | 繁體中文</p>

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

HachiDroid 是一款開源 Android 應用程式，用於向行動遊戲客戶端注入並修補模組執行階段。它內建兩個遊戲系列：面向 **UM:PD** 的 **Hachimi** 執行階段 **Tracen**，以及面向**PC:RD（日服）**的 **DaringTact** 執行階段 **Astraea**，可於主畫面一鍵切換。它以 Jetpack Compose 與 Material 3 Expressive 設計從零打造，是 [UmaPatcher-Edge](https://github.com/Tenshou170/UmaPatcher-Edge) 的現代繼任者。

---

## 主要特色

- **多客戶端支援**：支援一鍵切換與管理多個客戶端版本：
  - 日服 (JP)
  - 繁中服 (TW / HK)
  - 韓服 (KR)
  - 國際服 (EN)
- **靈活的補丁目標**：
  - **已安裝的遊戲**：直接偵測並修補裝置上現有的遊戲客戶端。
  - **APK 檔案**：支援單一 APK、分割 APK 安裝包或 XAPK 檔案。
- **多元安裝與匯出模式**：
  - **Direct Install (Root)**：無需重新安裝，直接將補丁庫掛載至運作中的應用程式。
  - **Shizuku (提權 ADB)**：無需 Root 權限即可直接安裝分割 APK 與 XAPK。
  - **應用程式安裝程式**：系統標準工作階段安裝。
  - **匯出檔案**：直接將打好補丁的單一 APK 或 XAPK 儲存至儲存空間。
  - **傳統安裝程式**：舊版 Android 環境的後備安裝方案。
- **Hachimi Mod 管理**：
  - 自動自 GitHub 發行版獲取並更新最新的 Hachimi-Edge 執行庫。
  - 支援一鍵側載本機自訂 `.so` 檔案。
- **外掛外掛程式系統**：支援側載並個別啟用/停用額外的原生 `.so` 外掛程式。
- **內建應用程式更新器**：支援 SHA-256 完整性驗證與更新日誌檢視的應用程式內更新檢查。
- **現代儲存與 DocumentsProvider**：可選擇性匯出 DocumentsProvider，開放檔案管理工具存取 `/data/data`（Android 16 QPR2+ 與 Android 17+ 必備）。
- **UmaPatcher-Edge 移轉**：一鍵自動從舊版 UmaPatcher-Edge 匯入簽章金鑰庫、外掛程式與偏好設定。
- **遊戲模式**：Tracen（UM:PD · Hachimi）與 Astraea（PC:RD 日服 · DaringTact），各自擁有獨立配色與儀表板。
- **裝置內程式庫解開（Astraea）**：透過內建的 [Senbei](https://github.com/Tenshou170/Senbei) 引擎，直接從遊戲的 arm64 分割檔解開受 CrackProof 保護的 `libil2cpp.so` 與 `libnative.so`，無需電腦工具，也無需重新上傳任何檔案。僅支援 arm64 裝置。
- **單向修補警告**：Astraea 模式會明確說明，已修補的 PC:RD 無法還原為原版；若要遊玩原版，需從 Play 商店重新安裝。

---

## 執行模式

在英雄卡片的下拉選單中選擇遊戲系列，選擇會套用到整個應用程式：配色、儀表板磚塊以及修補流程。

| 模式 | 遊戲 | 執行階段 | 狀態 |
| :--- | :--- | :--- | :--- |
| **Tracen** | UM:PD (JP / TW-HK / KR / EN) | Hachimi-Edge | 完整修補流程 |
| **Astraea** | PC:RD（日服） | DaringTact | 分割檔修補、程式庫解開、安裝或匯出 |

Astraea 修補的是遊戲的分割檔，而非已安裝的應用程式：base 分割檔會換上乾淨的 Application 類別，並改寫資訊清單以繞過 CrackProof 外殼；arm64 config 分割檔則會刪除 `lib__*__.so` 樁程式庫，並寫入 `libil2cpp.so`、`libnative.so` 與 `libdaringtact.so`。結果會重新簽署，可透過 Shizuku、系統安裝程式安裝，或匯出為分割 APK / XAPK。

應用程式內也會明確說明兩點：修補版以你的金鑰簽署，無法更新商店版本（請先解除安裝遊戲）；也沒有原版還原——修補移除的外殼無法復原，唯一的方法是從 Play 商店重新安裝。

---

## 系統需求

| 元件 | 最低需求 | 備註 |
| :--- | :--- | :--- |
| **Android 系統** | Android 7.0 (API Level 24) 以上 | 針對 Android 17 (API Level 37) 建置與測試 |
| **目標應用程式** | 支援的 UM:PD 客戶端 | 日服、繁中服、韓服或國際服 |
| **Astraea 目標** | PC:RD（日服） | arm64-v8a 分割檔；裝置內還原需 arm64 裝置 |
| **Root (選用)** | Magisk / KernelSU / APatch | 僅在 Direct Install 模式下需要 |
| **Shizuku (選用)** | Shizuku v13 以上 | 用於免 Root 安裝分割 APK / XAPK |

---

## 快速上手

1. 前往 **[Releases](https://github.com/HachiFridge/HachiDroid/releases)** 頁面下載並安裝 HachiDroid。
2. 開啟應用程式，並在 **補丁工作台** 中選擇目標客戶端。
3. 選擇輸入來源（**已安裝的遊戲** 或 **APK 檔案**）。
4. 選擇喜好的 **安裝方式**（Direct Root、Shizuku、應用程式安裝程式或匯出檔案）。
5. 點擊 **開始打補丁** 並依照畫面指示進行操作。

詳細教學指南：**[在 Android 上安裝 Hachimi](https://hachimi.noccu.art/docs/hachimi/installing-android)**

---

## 從原始碼建置

開發環境前置需求及 Gradle 建置指令完整記錄於 **[BUILDING-zh_tw.md](BUILDING-zh_tw.md)**。

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
./gradlew assembleRelease
```

---

## 社群與支援

- **Discord**：歡迎加入我們的 [Discord 伺服器](https://discord.gg/YjBgmuqqYr) 與社群交流並獲取技術支援。
- **Issues**：請透過 GitHub Issues 回報錯誤或提出新功能建議。

---

## 授權條款

本專案採用 GNU General Public License v3.0 (GPL-3.0) 授權條款開放原始碼 — 詳情請參閱 [LICENSE](../LICENSE) 與 [NOTICE](../NOTICE) 檔案。

### Senbei 橋接

選用的裝置內還原會連結 CrackProof 解包工具 [Senbei](https://github.com/Tenshou170/Senbei)（**AGPL-3.0-only**）。因此包含 `libsenbei_jni.so` 的建置除了上述 GPL-3.0 外，還需遵守 AGPL-3.0；詳見 [NOTICE](../NOTICE)。沒有 Rust 工具鏈時，此橋接不會被包含。
