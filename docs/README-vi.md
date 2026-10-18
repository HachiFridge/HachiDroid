<div align="center">
  <img src="../images/AppIcon.png" width="128" height="128" alt="HachiDroid icon">
  <h1>HachiDroid</h1>
  <p><b>Công cụ vá và quản lý MOD Android thế hệ mới cho UM:PD và PC:RD</b></p>

  <p><a href="../README.md">English</a> | <a href="README-fil.md">Filipino</a> | <a href="README-ja.md">日本語</a> | <a href="README-ko.md">한국어</a> | <a href="README-ru.md">Русский</a> | Tiếng Việt | <a href="README-zh_cn.md">简体中文</a> | <a href="README-zh_tw.md">繁體中文</a></p>

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

HachiDroid là ứng dụng Android mã nguồn mở giúp chèn và vá runtime mod vào client game di động. Ứng dụng có hai dòng game: **Tracen** cho **UM:PD** với runtime **Hachimi**, và **Astraea** cho **PC:RD (JP)** với runtime **DaringTact**; chuyển đổi bằng một thao tác trên màn hình chính. Được xây dựng từ đầu với Jetpack Compose và Material 3 Expressive, HachiDroid là phiên bản kế nhiệm hiện đại của [UmaPatcher-Edge](https://github.com/Tenshou170/UmaPatcher-Edge).

---

## Tính năng chính nổi bật

- **Hỗ trợ đa client**: Dễ dàng nhận diện và chuyển đổi giữa các phiên bản client chỉ với một chạm:
  - Bản Nhật (JP)
  - Bản Phồn thể (TW / HK)
  - Bản Hàn (KR)
  - Bản Quốc tế (EN)
- **Mục tiêu vá linh hoạt**:
  - **Game đã cài đặt**: Trực tiếp nhận diện và vá client đã có trên thiết bị.
  - **Tệp APK**: Hỗ trợ chọn và vá tệp APK đơn, bộ split APK hoặc tệp XAPK.
- **Nhiều chế độ cài đặt và xuất tệp**:
  - **Cài đặt trực tiếp (Root)**: Gắn trực tiếp các thư viện đã vá vào ứng dụng đang chạy mà không cần cài đặt lại.
  - **Shizuku (ADB nâng cao)**: Cài đặt split APK và XAPK mà không yêu cầu quyền root.
  - **Trình cài đặt hệ thống**: Cài đặt thông qua phiên cài đặt chuẩn của hệ thống.
  - **Xuất tệp**: Lưu trực tiếp tệp APK độc lập hoặc XAPK đã vá vào bộ nhớ thiết bị.
  - **Trình cài đặt cũ**: Trình cài đặt dự phòng cho các môi trường Android cũ hơn.
- **Quản lý Mod Hachimi**:
  - Tự động kiểm tra và tải về phiên bản Hachimi-Edge mới nhất từ GitHub releases.
  - Hỗ trợ nạp tệp nhị phân `.so` tùy chỉnh từ bộ nhớ cục bộ chỉ với một chạm.
- **Hệ thống Plugin**: Nạp và quản lý các plugin native `.so` bổ sung với nút bật/tắt riêng biệt.
- **Trình cập nhật tích hợp**: Kiểm tra cập nhật ngay trong ứng dụng với xác thực mã kiểm tra SHA-256 và xem danh sách thay đổi.
- **Lưu trữ hiện đại & DocumentsProvider**: Tùy chọn xuất DocumentsProvider cho phép các ứng dụng quản lý tệp truy cập vào thư mục `/data/data` (cần thiết cho Android 16 QPR2+ và Android 17+).
- **Chuyển đổi từ UmaPatcher-Edge**: Tự động chuyển đổi kho khóa ký (keystore), plugin và cài đặt từ phiên bản UmaPatcher-Edge cũ.
- **Chế độ game**: Tracen (UM:PD · Hachimi) và Astraea (PC:RD JP · DaringTact) với bảng màu và dashboard riêng.
- **Giải nén thư viện trên thiết bị (Astraea)**: công cụ [Senbei](https://github.com/Tenshou170/Senbei) tích hợp giải nén `libil2cpp.so` và `libnative.so` được CrackProof bảo vệ ngay từ APK config arm64 của game — không cần công cụ trên PC và không phải tải lại tệp của ai. Chỉ hỗ trợ thiết bị arm64.
- **Cảnh báo vá một chiều**: chế độ Astraea nêu rõ không thể đưa PC:RD đã vá về bản gốc; muốn chơi bản gốc phải cài lại từ Play Store.

---

## Chế độ game

Chọn dòng game trong menu thả xuống trên thẻ hero; lựa chọn áp dụng cho toàn bộ ứng dụng: bảng màu, các ô dashboard và luồng vá.

| Chế độ | Game | Runtime | Trạng thái |
| :--- | :--- | :--- | :--- |
| **Tracen** | UM:PD (JP / TW-HK / KR / EN) | Hachimi-Edge | Đầy đủ luồng vá |
| **Astraea** | PC:RD (JP) | DaringTact | Vá APK chia nhỏ, giải nén thư viện, cài đặt hoặc xuất tệp |

Astraea vá bộ APK chia nhỏ của game chứ không phải ứng dụng đã cài: base nhận một lớp Application sạch và chỉnh manifest để vượt qua lớp vỏ CrackProof, còn config arm64 thì xóa các stub `lib__*__.so` và ghi vào `libil2cpp.so`, `libnative.so`, `libdaringtact.so`. Kết quả được ký lại và có thể cài qua Shizuku, trình cài đặt hệ thống, hoặc xuất thành APK chia nhỏ / XAPK.

Hai hệ quả cũng được nêu ngay trong ứng dụng: bản đã vá được ký bằng khóa của bạn nên không thể cập nhật bản từ cửa hàng (hãy gỡ game trước), và không có khôi phục bản gốc — lớp vỏ mà bản vá loại bỏ là mất hẳn, cách duy nhất để quay lại là cài lại từ Play Store.

---

## Yêu cầu hệ thống

| Thành phần | Yêu cầu tối thiểu | Ghi chú |
| :--- | :--- | :--- |
| **Hệ điều hành Android** | Android 7.0 (API Level 24)+ | Xây dựng và thử nghiệm trên Android 17 (API Level 37) |
| **Ứng dụng mục tiêu** | Client UM:PD được hỗ trợ | Phiên bản JP, TW/HK, KR hoặc Global |
| **Mục tiêu Astraea** | PC:RD (JP) | APK config arm64-v8a; khôi phục trên thiết bị cần máy arm64 |
| **Root (Tùy chọn)** | Magisk / KernelSU / APatch | Chỉ cần thiết cho chế độ Cài đặt trực tiếp |
| **Shizuku (Tùy chọn)** | Shizuku v13+ | Để cài đặt split APK / XAPK không cần root |

---

## Bắt đầu nhanh

1. **Cài đặt HachiDroid** từ trang **[Releases](https://github.com/HachiFridge/HachiDroid/releases)**.
2. Mở ứng dụng và chọn client mục tiêu tại **Xưởng vá**.
3. Chọn nguồn đầu vào (**Game đã cài đặt** hoặc **Tệp APK**).
4. Chọn **Phương thức cài đặt** mong muốn (Direct Root, Shizuku, Trình cài đặt hệ thống hoặc Xuất tệp).
5. Nhấn **Bắt đầu vá** và làm theo hướng dẫn trên màn hình.

Hướng dẫn chi tiết: **[Cài đặt Hachimi trên Android](https://hachimi.noccu.art/docs/hachimi/installing-android)**

---

## Xây dựng từ mã nguồn

Yêu cầu môi trường, chuẩn bị và các lệnh xây dựng Gradle được ghi chép chi tiết tại **[BUILDING-vi.md](BUILDING-vi.md)**.

```bash
git clone https://github.com/HachiFridge/HachiDroid.git
cd HachiDroid
./gradlew assembleRelease
```

---

## Cộng đồng & Hỗ trợ

- **Discord**: Tham gia máy chủ [Discord](https://discord.gg/YjBgmuqqYr) để trao đổi và nhận trợ giúp.
- **Issues**: Báo cáo lỗi hoặc đóng góp ý kiến qua GitHub Issues.

---

## Giấy phép

Dự án này được cấp phép theo Giấy phép GNU General Public License v3.0 (GPL-3.0) — xem các tệp [LICENSE](../LICENSE) và [NOTICE](../NOTICE) để biết chi tiết.

### Cầu nối Senbei

Tính năng khôi phục trên thiết bị (tùy chọn) liên kết [Senbei](https://github.com/Tenshou170/Senbei), công cụ giải nén CrackProof theo giấy phép **AGPL-3.0-only**. Vì vậy bản dựng kèm `libsenbei_jni.so` phải tuân thủ AGPL-3.0 bên cạnh GPL-3.0 ở trên; xem [NOTICE](../NOTICE). Nếu không có bộ công cụ Rust, cầu nối này không được đóng gói.
