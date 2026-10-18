<div align="center">
  <img src="../images/AppIcon.png" width="128" height="128" alt="HachiDroid icon">
  <h1>HachiDroid</h1>
  <p><b>Susunod-na-henerasyong Android MOD patcher at manager para sa UM:PD at PC:RD</b></p>

  <p><a href="../README.md">English</a> | Filipino | <a href="README-ja.md">日本語</a> | <a href="README-ko.md">한국어</a> | <a href="README-ru.md">Русский</a> | <a href="README-vi.md">Tiếng Việt</a> | <a href="README-zh_cn.md">简体中文</a> | <a href="README-zh_tw.md">繁體中文</a></p>

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

Ang HachiDroid ay isang open-source na Android application na nag-i-inject at nag-pa-patch ng mod runtimes sa mga mobile game client. May dalawang game family ito: **Tracen** para sa **UM:PD** gamit ang **Hachimi**, at **Astraea** para sa **PC:RD (JP)** gamit ang **DaringTact** — pinipili sa isang kontrol sa home screen. Gawa mula sa simula gamit ang Jetpack Compose at Material 3 Expressive, ito ang modernong kahalili ng [UmaPatcher-Edge](https://github.com/Tenshou170/UmaPatcher-Edge).

---

## Pangunahing Tampok

- **Suporta sa Maraming Client**: Madaling lumipat at mag-manage ng iba\'t ibang edisyon ng laro:
  - Japan (JP)
  - Traditional Chinese (TW / HK)
  - Korean (KR)
  - Global (EN)
- **Iba\'t Ibang Patch Target**:
  - **Naka-install na Laro**: Direktang tukuyin at i-patch ang client na nasa device na.
  - **Mga APK File**: Pumili at mag-patch ng standalone APK, split APK bundle, o XAPK file.
- **Maraming Paraan ng Pag-install at Pag-export**:
  - **Direct Install (Root)**: I-mount ang mga na-patch na library nang hindi kailangang mag-reinstall.
  - **Shizuku (Elevated ADB)**: Mag-install ng mga split APK at XAPK nang walang root permission.
  - **Package Installer**: Karaniwang sesyon ng pag-install ng system.
  - **I-export ang File**: Direktang i-save ang na-patch na standalone APK o XAPK sa storage.
  - **Legacy Installer**: Nakaraang paraan ng pag-install para sa mga lumang kapaligiran.
- **Pamamahala ng Hachimi Mod**:
  - Awtomatikong kumuha at mag-update ng pinakabagong Hachimi-Edge runtime mula sa GitHub releases.
  - Mag-sideload ng lokal na pasadyang `.so` binary sa isang pindot lang.
- **Sistema ng Plugin**: Magdagdag at mag-manage ng karagdagang native `.so` plugins na may kanya-kanyang toggle.
- **Built-in App Updater**: May kasamang pagsusuri ng update sa loob ng app, SHA-256 verification, at titingnan ng changelog.
- **Makabagong Storage at DocumentsProvider**: Opsyonal na DocumentsProvider export na nagbibigay-daan sa mga file manager na ma-access ang `/data/data` (kailangan sa Android 16 QPR2+ at Android 17+).
- **Paglipat mula sa UmaPatcher-Edge**: Isang-pindot na awtomatikong paglipat ng mga signing keystore, plugin, at setting mula sa lumang UmaPatcher-Edge.
- **Mga game mode**: Tracen (UM:PD · Hachimi) at Astraea (PC:RD JP · DaringTact) na may sariling palette at dashboard.
- **On-device na pag-unpack ng library (Astraea)**: binubuksan ng built-in na [Senbei](https://github.com/Tenshou170/Senbei) engine ang `libil2cpp.so` at `libnative.so` na protektado ng CrackProof mula mismo sa arm64 split ng laro — walang kailangang PC tool at walang muling pag-upload ng file ng iba. Para sa arm64 device lamang.
- **Babala sa one-way na patch**: sinasabi ng Astraea mode na hindi maibabalik sa orihinal ang naka-patch na PC:RD; para makapaglaro nang walang mod, kailangang i-install muli mula sa Play Store.

---

## Mga Game Mode

Pinipili sa dropdown ng hero card ang game family, at dumadaan ang pinili sa buong app: color scheme, mga tile ng dashboard at ang patch flow.

| Mode | Laro | Runtime | Kalagayan |
| :--- | :--- | :--- | :--- |
| **Tracen** | UM:PD (JP / TW-HK / KR / EN) | Hachimi-Edge | Kumpletong patch flow |
| **Astraea** | PC:RD (JP) | DaringTact | Split patching, pag-unpack ng library, install o export |

Pinapa-patch ng Astraea ang split set ng laro, hindi ang naka-install na app: ang base split ay binibigyan ng malinis na Application class at manifest redirect na lumalampas sa CrackProof shell, at ang arm64 config split ay tinatanggalan ng `lib__*__.so` stubs at nilalagyan ng `libil2cpp.so`, `libnative.so` at `libdaringtact.so`. Nire-re-sign ang resulta at maaaring i-install sa Shizuku, sa system package installer, o i-export bilang split APKs / XAPK.

Dalawang epekto ang sinasabi rin sa app: naka-sign ang naka-patch na build gamit ang sarili mong key kaya hindi nito maa-update ang kopya mula sa store (i-uninstall muna ang laro), at walang pag-restore sa orihinal — hindi na maibabalik ang shell na tinatanggal ng patch, at ang tanging paraan pabalik ay mag-install muli mula sa Play Store.

---

## Mga Kinakailangan

| Bahagi | Minimum na Kinakailangan | Tala |
| :--- | :--- | :--- |
| **Android OS** | Android 7.0 (API Level 24)+ | Ginawa at sinubukan para sa Android 17 (API Level 37) |
| **Target na App** | Sinusuportahang UM:PD Client | Edisyong JP, TW/HK, KR, o Global |
| **Target ng Astraea** | PC:RD (JP) | arm64-v8a split; kailangan ng arm64 device para sa on-device unpack |
| **Root (Opsyonal)** | Magisk / KernelSU / APatch | Kinakailangan lamang para sa Direct Install |
| **Shizuku (Opsyonal)** | Shizuku v13+ | Para sa rootless na pag-install ng split APK / XAPK |

---

## Mabilis na Pagsisimula

1. **I-install ang HachiDroid** mula sa pahina ng **[Releases](https://github.com/HachiFridge/HachiDroid/releases)**.
2. Buksan ang app at piliin ang iyong aktibong client sa **Patch Studio**.
3. Piliin ang iyong input source (**Naka-install na Laro** o **Mga APK File**).
4. Piliin ang iyong nais na **Paraan ng Pag-install** (Direct Root, Shizuku, Package Installer, o I-export ang File).
5. Pindutin ang **Simulan ang pag-patch** at sundin ang mga tagubilin sa screen.

Detalyadong gabay: **[Pag-install ng Hachimi sa Android](https://hachimi.noccu.art/docs/hachimi/installing-android)**

---

## Pagbuo mula sa Source

Ang mga kinakailangan sa pag-compile, pag-setup ng kapaligiran, at mga utos sa Gradle ay nakadetalye sa **[BUILDING-fil.md](BUILDING-fil.md)**.

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
./gradlew assembleRelease
```

---

## Komunidad at Suporta

- **Discord**: Sumali sa aming [Discord server](https://discord.gg/YjBgmuqqYr) para sa mga talakayan at tulong.
- **Issues**: Mag-ulat ng mga bug o magmungkahi ng mga tampok gamit ang GitHub Issues.

---

## Lisensya

Ang proyektong ito ay lisensyado sa ilalim ng GNU General Public License v3.0 (GPL-3.0) — tingnan ang [LICENSE](../LICENSE) at [NOTICE](../NOTICE) para sa mga detalye.

### Senbei bridge

Ginagamit ng opsyonal na on-device unpack ang [Senbei](https://github.com/Tenshou170/Senbei), isang CrackProof unpacker na lisensyado sa ilalim ng **AGPL-3.0-only**. Kaya ang mga build na may `libsenbei_jni.so` ay may AGPL-3.0 na obligasyon bukod sa GPL-3.0 sa itaas; tingnan ang [NOTICE](../NOTICE). Kung walang Rust toolchain, hindi kasama ang bridge.
