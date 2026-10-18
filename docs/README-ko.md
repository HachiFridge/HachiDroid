<div align="center">
  <img src="../images/AppIcon.png" width="128" height="128" alt="HachiDroid icon">
  <h1>HachiDroid</h1>
  <p><b>UM:PD와 PC:RD를 위한 차세대 Android MOD 패처 및 매니저</b></p>

  <p><a href="../README.md">English</a> | <a href="README-fil.md">Filipino</a> | <a href="README-ja.md">日本語</a> | 한국어 | <a href="README-ru.md">Русский</a> | <a href="README-vi.md">Tiếng Việt</a> | <a href="README-zh_cn.md">简体中文</a> | <a href="README-zh_tw.md">繁體中文</a></p>

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

**HachiDroid**는 모바일 게임 클라이언트에 모드 런타임을 주입하고 패치하기 위한 오픈 소스 Android 애플리케이션입니다. **UM:PD**를 위한 **Hachimi** 런타임을 다루는 **Tracen**과 **PC:RD (JP)**를 위한 **DaringTact** 런타임을 다루는 **Astraea**라는 두 개의 게임 패밀리를 지원하며, 대시보드에서 탭 한 번으로 전환할 수 있습니다. Jetpack Compose 및 Material 3 Expressive 디자인으로 처음부터 새롭게 구축된 [UmaPatcher-Edge](https://github.com/Tenshou170/UmaPatcher-Edge)의 현대적인 후속 도구입니다.

---

## 주요 기능

- **다중 클라이언트 지원**: 탭 한 번으로 대상 버전을 간편하게 전환:
  - 일본판 (JP)
  - 번체 중국어판 (TW / HK)
  - 한국판 (KR)
  - 글로벌판 (EN)
- **유연한 패치 대상**:
  - **설치된 게임**: 기기에 이미 설치된 클라이언트를 직접 감지하여 패치.
  - **APK 파일**: 단일 APK, 분할 APK 번들 또는 XAPK 파일을 직접 선택하여 패치.
- **다양한 설치 및 내보내기 모드**:
  - **직접 설치 (Root)**: 재설치 없이 패치된 라이브러리를 실행 중인 앱에 직접 마운트.
  - **Shizuku (권한 상승 ADB)**: 루트 권한 없이 분할 APK 및 XAPK를 직접 설치.
  - **패키지 설치 관리자**: 표준 시스템 세션 설치.
  - **파일 내보내기**: 패치된 독립형 APK 또는 XAPK를 저장소에 직접 저장.
  - **기존 설치 관리자**: 구형 환경을 위한 대체 시스템 패키지 설치 관리자.
- **Hachimi 모드 관리**:
  - GitHub 릴리스에서 최신 Hachimi-Edge 런타임을 자동으로 확인하고 업데이트.
  - 사용자 지정 로컬 `.so` 바이너리를 탭 한 번으로 사이드로드.
- **플러그인 시스템**: 개별 활성화/비활성화 토글이 지원되는 추가 네이티브 `.so` 플러그인 관리.
- **내장 앱 업데이터**: SHA-256 무결성 검증 및 변경 로그 뷰어가 포함된 인앱 업데이트 확인 기능.
- **최신 저장소 및 DocumentsProvider**: 파일 관리자에서 `/data/data`에 접근할 수 있도록 하는 DocumentsProvider 내보내기(Android 16 QPR2+ 및 Android 17+ 필수).
- **UmaPatcher-Edge 마이그레이션**: 기존 UmaPatcher-Edge 설치로부터 서명 키스토어, 플러그인, 설정을 원클릭으로 자동 이전.
- **게임 모드**: 모드별 팔레트와 대시보드를 갖춘 Tracen(UM:PD · Hachimi)과 Astraea(PC:RD JP · DaringTact).
- **기기 내 라이브러리 풀기 (Astraea)**: 내장 [Senbei](https://github.com/Tenshou170/Senbei) 엔진으로 CrackProof로 보호된 `libil2cpp.so`와 `libnative.so`를 게임의 arm64 스플릿에서 직접 풉니다. PC 도구도, 다시 업로드할 필요도 없습니다. arm64 기기 전용.
- **되돌릴 수 없는 패치 경고**: Astraea 모드는 패치된 PC:RD를 순정 상태로 되돌릴 수 없음을 명시합니다. 순정으로 플레이하려면 Play 스토어에서 다시 설치해야 합니다.

---

## 게임 모드

히어로 카드의 드롭다운에서 게임 계열을 고르면 선택이 앱 전체(색상 팔레트, 대시보드 타일, 패치 흐름)에 반영됩니다.

| 모드 | 게임 | 런타임 | 상태 |
| :--- | :--- | :--- | :--- |
| **Tracen** | UM:PD (JP / TW-HK / KR / EN) | Hachimi-Edge | 전체 패치 흐름 |
| **Astraea** | PC:RD (JP) | DaringTact | 분할 APK 패치, 라이브러리 풀기, 설치 또는 내보내기 |

Astraea는 설치된 앱이 아니라 게임의 분할 APK를 패치합니다. base 스플릿에는 깨끗한 Application 클래스와 CrackProof 셸을 우회하는 매니페스트 리다이렉트를 적용하고, arm64 config 스플릿에는 `lib__*__.so` 스텁을 제거한 뒤 `libil2cpp.so`, `libnative.so`, `libdaringtact.so`를 기록합니다. 결과는 다시 서명되며 Shizuku, 시스템 패키지 설치 프로그램, 또는 분할 APK/XAPK 내보내기로 설치할 수 있습니다.

앱에서도 알리지만, 패치된 빌드는 사용자의 키로 서명되므로 스토어 버전을 업데이트할 수 없고(먼저 게임을 삭제해야 합니다), 순정 상태로 되돌리는 복원 기능도 없습니다. 패치가 제거하는 셸은 되돌릴 수 없으며, 유일한 방법은 Play 스토어에서 다시 설치하는 것입니다.

---

## 요구 사항

| 항목 | 최소 요구 사양 | 비고 |
| :--- | :--- | :--- |
| **Android OS** | Android 7.0 (API Level 24) 이상 | Android 17 (API Level 37) 기준 빌드 및 테스트 완료 |
| **대상 앱** | 지원되는 UM:PD 클라이언트 | 일본판, 번체 중국어판, 한국판 또는 글로벌판 |
| **Astraea 대상** | PC:RD (JP) | arm64-v8a 스플릿. 기기 내 복원은 arm64 기기 필요 |
| **Root (선택)** | Magisk / KernelSU / APatch | 직접 설치(Direct Install) 모드에서만 필요 |
| **Shizuku (선택)** | Shizuku v13 이상 | 무루팅 분할 APK / XAPK 설치 시 필요 |

---

## 빠른 시작

1. **[Releases](https://github.com/HachiFridge/HachiDroid/releases)** 페이지에서 HachiDroid를 설치합니다.
2. 앱을 실행하고 **패치 스튜디오**에서 대상 클라이언트를 선택합니다.
3. 입력 소스(**설치된 게임** 또는 **APK 파일**)를 선택합니다.
4. 원하는 **설치 방식**(Direct Root, Shizuku, 패키지 설치 관리자, 파일 내보내기)을 선택합니다.
5. **패치 시작**을 누르고 화면의 안내를 따릅니다.

상세 가이드: **[Android에서 Hachimi 설치하기](https://hachimi.noccu.art/docs/hachimi/installing-android)**

---

## 소스에서 빌드

컴파일 사전 요구사항, 환경 설정 및 Gradle 빌드 명령은 **[BUILDING-ko.md](BUILDING-ko.md)**에 문서화되어 있습니다.

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
./gradlew assembleRelease
```

---

## 커뮤니티 및 지원

- **Discord**: [Discord 서버](https://discord.gg/YjBgmuqqYr)에 참여하여 소통과 지원을 받으세요.
- **Issues**: 버그 보고 및 기능 요청은 GitHub Issues를 이용해 주세요.

---

## 라이선스

이 프로젝트는 GNU General Public License v3.0 (GPL-3.0) 하에 배포됩니다. 자세한 내용은 [LICENSE](../LICENSE) 및 [NOTICE](../NOTICE) 파일을 참조하세요.

### Senbei 브리지

선택적인 기기 내 복원은 CrackProof 언패커 [Senbei](https://github.com/Tenshou170/Senbei)(**AGPL-3.0-only**)를 링크합니다. 따라서 `libsenbei_jni.so`를 포함하는 빌드는 위의 GPL-3.0에 더해 AGPL-3.0 의무를 집니다. [NOTICE](../NOTICE)를 참고하세요. Rust 툴체인이 없으면 브리지는 포함되지 않습니다.
