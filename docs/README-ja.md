<div align="center">
  <img src="../images/AppIcon.png" width="128" height="128" alt="HachiDroid icon">
  <h1>HachiDroid</h1>
  <p><b>UM:PD と PC:RD 向け 次世代Android MODパッチャー＆マネージャー</b></p>

  <p><a href="../README.md">English</a> | <a href="README-fil.md">Filipino</a> | 日本語 | <a href="README-ko.md">한국어</a> | <a href="README-ru.md">Русский</a> | <a href="README-vi.md">Tiếng Việt</a> | <a href="README-zh_cn.md">简体中文</a> | <a href="README-zh_tw.md">繁體中文</a></p>

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

HachiDroid は、モバイルゲームのクライアントに MOD ランタイムを注入・パッチ適用するためのオープンソース Android アプリケーションです。**UM:PD** 向けの **Hachimi** を扱う **Tracen** と、**PC:RD (JP)** 向けの **DaringTact** を扱う **Astraea** の 2 つのゲームファミリーを搭載し、ホーム画面の 1 つの操作で切り替えられます。Jetpack Compose と Material 3 Expressive デザインでゼロから構築された、[UmaPatcher-Edge](https://github.com/Tenshou170/UmaPatcher-Edge) の現代的な後継ツールです。

---

## 主な機能

- **マルチクライアント対応**: ワンタップで切り替え可能な複数エディション対応:
  - 日本版 (JP)
  - 繁体字中国語版 (TW / HK)
  - 韓国版 (KR)
  - グローバル版 (EN)
- **柔軟なパッチ対象**:
  - **インストール済みゲーム**: デバイスに既にインストールされているクライアントを直接検出してパッチ。
  - **APKファイル**: 単一APK、分割APK、XAPKファイルを選択してパッチ。
- **多様なインストール・出力モード**:
  - **Direct Install (Root)**: 再インストール不要で、パッチ済みライブラリを直接マウント。
  - **Shizuku (昇格ADB)**: Root権限なしで分割APKやXAPKを直接インストール。
  - **パッケージインストーラー**: システム標準のセッションインストール。
  - **ファイルを出力**: パッチ済みの単一APKまたはXAPKをストレージに直接保存。
  - **レガシーインストーラー**: 従来のシステムパッケージインストーラー。
- **Hachimi MOD管理**:
  - GitHub リリースから最新の Hachimi-Edge ランタイムを自動取得・更新。
  - ローカルのカスタム `.so` バイナリをワンタップでサイドロード。
- **プラグインシステム**: 個別の有効/無効切り替えが可能なネイティブ `.so` プラグインの管理。
- **内蔵アプリアップデーター**: SHA-256 チェックサム整合性検証および更新履歴ビューアー付きのアプリ内更新確認。
- **最新ストレージ・DocumentsProvider**: ファイルマネージャーから `/data/data` へのアクセスを可能にする DocumentsProvider エクスポート（Android 16 QPR2+ および Android 17+ で必要）。
- **UmaPatcher-Edge からの移行**: 従来の UmaPatcher-Edge から署名キーストア、プラグイン、設定をワンクリックで自動移行。
- **ゲームモード**: モードごとの配色とダッシュボードを備えた Tracen（UM:PD・Hachimi）と Astraea（PC:RD JP・DaringTact）。
- **端末上でのライブラリ展開 (Astraea)**: 内蔵の [Senbei](https://github.com/Tenshou170/Senbei) エンジンで、CrackProof で保護された `libil2cpp.so` と `libnative.so` をゲームの arm64 スプリットから直接展開します。PC 用ツールも再アップロードも不要です。arm64 端末のみ対応。
- **一方通行のパッチ警告**: Astraea モードは、パッチ適用済みの PC:RD を純正状態に戻せないことを明示します。未改造で遊ぶには Play ストアから再インストールが必要です。

---

## ゲームモード

ヒーローカードのドロップダウンでゲームファミリーを選ぶと、その選択がアプリ全体（配色、ダッシュボードのタイル、パッチの流れ）に反映されます。

| モード | ゲーム | ランタイム | 状態 |
| :--- | :--- | :--- | :--- |
| **Tracen** | UM:PD (JP / TW-HK / KR / EN) | Hachimi-Edge | パッチフロー全体 |
| **Astraea** | PC:RD (JP) | DaringTact | 分割APKのパッチ、ライブラリ展開、インストール／書き出し |

Astraea はインストール済みアプリではなくゲームの分割APKをパッチします。base スプリットにはクリーンな Application クラスと、CrackProof シェルを迂回するマニフェストの書き換えを適用し、arm64 の config スプリットには `lib__*__.so` のスタブを削除したうえで `libil2cpp.so`、`libnative.so`、`libdaringtact.so` を書き込みます。結果は再署名され、Shizuku、システムのパッケージインストーラー、または分割APK／XAPK としての書き出しでインストールできます。

アプリ内でも明示していますが、パッチ済みビルドは独自の鍵で署名されるためストア版を更新できません（先にゲームをアンインストールしてください）。また純正状態への復元はありません。パッチが取り除くシェルは元に戻せず、戻る手段は Play ストアからの再インストールだけです。

---

## 動作要件

| 項目 | 最小要件 | 備考 |
| :--- | :--- | :--- |
| **Android OS** | Android 7.0 (API Level 24) 以上 | Android 17 (API Level 37) 向けに構築・テスト済み |
| **対象アプリ** | サポート対象の UM:PD クライアント | 日本版、繁体字版、韓国版、グローバル版 |
| **Astraea 対象** | PC:RD (JP) | arm64-v8a スプリット。端末上での復元は arm64 端末が必要 |
| **Root (任意)** | Magisk / KernelSU / APatch | Direct Install モードでのみ必要 |
| **Shizuku (任意)** | Shizuku v13 以上 | Rootなしでの分割APK / XAPKインストール用 |

---

## クイックスタート

1. **[Releases](https://github.com/HachiFridge/HachiDroid/releases)** ページから HachiDroid をインストールします。
2. アプリを起動し、**パッチスタジオ** で対象クライアントを選択します。
3. 入力ソース（**インストール済みゲーム** または **APKファイル**）を選択します。
4. 希望する **インストール方式**（Direct Root、Shizuku、パッケージインストーラー、ファイルを出力）を選択します。
5. **パッチを開始** をタップし、画面の案内に従います。

詳細ガイド: **[Android での Hachimi インストール手順](https://hachimi.noccu.art/docs/hachimi/installing-android)**

---

## ソースからのビルド

ビルド環境の準備および Gradle コマンドの詳細は **[BUILDING-ja.md](BUILDING-ja.md)** をご覧ください。

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
./gradlew assembleRelease
```

---

## コミュニティ＆サポート

- **Discord**: [Discord サーバー](https://discord.gg/YjBgmuqqYr) に参加して情報共有やサポートを受けることができます。
- **Issues**: バグ報告や機能要望は GitHub Issues よりお寄せください。

---

## ライセンス

本プロジェクトは GNU General Public License v3.0 (GPL-3.0) の下で公開されています。詳細は [LICENSE](../LICENSE) および [NOTICE](../NOTICE) を参照してください。

### Senbei ブリッジ

任意の端末上復元は、CrackProof アンパッカー [Senbei](https://github.com/Tenshou170/Senbei)（**AGPL-3.0-only**）をリンクします。`libsenbei_jni.so` を同梱するビルドは、上記の GPL-3.0 に加えて AGPL-3.0 の義務を負います。[NOTICE](../NOTICE) を参照してください。Rust ツールチェーンのない環境ではブリッジは単に含まれません。
