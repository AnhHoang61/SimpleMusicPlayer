# Nhạc Của Tôi (Simple Music Player) 🎵

Ứng dụng nghe nhạc ngoại tuyến (Offline Music Player) dành cho Android, được thiết kế với phong cách Dark Mode hiện đại, mượt mà và tối ưu hiệu năng.

![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)
![API](https://img.shields.io/badge/API-24%2B%20(Android%207.0%2B)-brightgreen)
![License](https://img.shields.io/badge/License-MIT-blue)

---

## 🌟 Tính Năng Nổi Bật

- **Tự động quét nhạc trên thiết bị:** Quét và lọc thông minh toàn bộ file âm thanh (`.mp3`, `.m4a`, `.wav`...) trong bộ nhớ máy và thẻ nhớ, tự động loại bỏ các đoạn âm thanh chuông báo ngắn dưới 5 giây.
- **Tìm kiếm tức thì (Instant Search):** Lọc bài hát theo tên hoặc ca sĩ ngay khi nhập liệu.
- **Thanh Mini Player:** Thu gọn ở cạnh đáy màn hình để thao tác nhanh Play/Pause và chuyển bài khi đang duyệt danh sách.
- **Màn hình Full Player (Samurai Katana Edition):**
  - **Sóng nhạc đĩa than 60 FPS (Circular Audio Wave Visualizer):** 64 cột sóng âm thanh tỏa tia, vòng sóng âm lan tỏa (acoustic shockwave ripples), và dải sóng lượn neon uốn lượn liên tục quanh vành đĩa Tsuba. Chạm vào đĩa than để kích hoạt xung kích sóng âm và đổi kiểu sóng (Đầy đủ, Vòng sóng, Sóng Neon).
  - **Hào quang Kiếm Tsuba (Spiritual Aura Breathing Pulse):** Vầng hào quang ánh ngọc lam co giãn nhịp nhàng khi phát nhạc và tắt nhẹ khi tạm dừng.
  - Hiệu ứng đĩa Tsuba xoay mượt mà khi phát nhạc và tự động dừng khi tạm dừng.
  - Thanh tua nhạc lưỡi kiếm Katana (SeekBar) với chuôi kiếm (Tsuka) và mũi kiếm (Kissaki).
  - Phản hồi xúc giác vi mô (Micro-interactions): Nảy đàn hồi Play/Pause/Shuffle/Repeat, nhích lướt Next/Previous, trái tim Yêu thích nảy pop.
  - Chế độ **Phát ngẫu nhiên (Shuffle)** và **Lặp lại (Repeat One / Repeat All)**.
  - **Thanh công cụ nhanh (Quick Actions Toolbar):** Lấp đầy khoảng trống cạnh dưới với các phím bấm tiện ích: Yêu thích (❤️), Thêm vào Album chủ đề (➕), Đổi tên (✏️), Xóa nhạc (🗑️), và xem hàng đợi (📋).
  - Huy hiệu nguồn phát (Source Badge) hiển thị rõ đang phát từ Album chủ đề, Thư mục hay Tất cả.
- **Đồng bộ thời gian thực qua Wi-Fi (Real-time Wi-Fi Sync & Desktop Companion):**
  - Tự động phát hiện PC trong mạng Wi-Fi qua UDP Beacon (Port 8888).
  - Đồng bộ tức thì (sub-second sync) qua luồng sự kiện Server-Sent Events (SSE).
  - Kèm ứng dụng Desktop Companion & Windows Service trong thư mục `desktop-companion/`.
- **Quản lý Thư mục & Đổi tên, Xóa nhạc:**
  - **Duyệt theo Thư mục máy:** Nhóm các bài hát theo thư mục thực tế trên bộ nhớ máy (Music, Download, V-Music...), cho phép mở xem và phát toàn bộ thư mục với 1 chạm.
  - **Chuyển cảnh lướt ngang mượt mà (Horizontal Drill Navigation):** Mở xem chi tiết thư mục và album với hiệu ứng trượt mượt mà.
  - **Đổi tên khúc nhạc (Rename):** Đổi tên bài hát ngay trong ứng dụng, lưu trữ lâu dài và tự động cập nhật trên toàn bộ danh sách, thông báo và widget.
  - **Xóa bài hát an toàn (Delete):** Xóa khỏi Album chủ đề hoặc xóa vĩnh viễn khỏi thiết bị với hộp thoại xác nhận an toàn.
- **Tạo Album theo chủ đề (Themed Albums):**
  - Tự do tạo các Album đặt tên theo chủ đề, cảm xúc hoặc thời điểm nghe nhạc (ví dụ: *⚔️ Samurai Kiếm Đạo*, *🌙 Nhạc Đêm Khuya*, *🔥 Động Lực & Luyện Tập*, *☕ Acoustic & Thư Giãn*...).
  - Tùy chọn biểu tượng Emoji trực quan cho từng chủ đề.
  - Thêm, bớt bài hát dễ dàng và phát toàn bộ album chỉ với 1 chạm.
- **Phát nhạc chạy ngầm (Foreground Service):**
  - Tiếp tục phát nhạc mượt mà ngay cả khi tắt màn hình, khóa máy hoặc sử dụng ứng dụng khác.
  - Thanh điều khiển trên Notification bar và màn hình khóa (Android 13+ & 14+ tương thích).
- **Hỗ trợ quyền đầy đủ:** Xử lý cấp quyền động mượt mà cho cả Android 13+ (`READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`) và Android 12 trở xuống (`READ_EXTERNAL_STORAGE`).

---

## 📸 Cấu Trúc Dự Án (Clean Architecture)

```text
V-Music/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/simplemusicplayer/
│       │   ├── MainActivity.java               # Điều phối giao diện người dùng, Tabs, Animation & Controls
│       │   ├── adapter/
│       │   │   ├── FolderAdapter.java          # Adapter thư mục chứa nhạc
│       │   │   ├── MusicAdapter.java           # Adapter danh sách bài hát & bộ lọc tìm kiếm
│       │   │   └── ThemedAlbumAdapter.java     # Adapter album chủ đề
│       │   ├── manager/
│       │   │   └── ThemedAlbumManager.java     # Quản lý album chủ đề, danh sách yêu thích & tên bài hát
│       │   ├── model/
│       │   │   ├── Song.java                   # Entity mô hình dữ liệu bài hát
│       │   │   └── ThemedAlbum.java            # Entity album chủ đề
│       │   ├── player/
│       │   │   └── MusicPlayerManager.java     # Singleton điều khiển MediaPlayer & thứ tự phát
│       │   ├── service/
│       │   │   └── MusicService.java           # Foreground Service, Notification & Android Auto
│       │   ├── sync/
│       │   │   └── SyncManager.java            # Đồng bộ Wi-Fi 3 lớp (SSE, UDP Beacon, Polling)
│       │   ├── util/
│       │   │   ├── AudioDeviceHelper.java      # Quản lý phát hiện & nhãn cổng âm thanh (Speaker/BT/USB)
│       │   │   ├── MusicLoader.java            # Quét & nạp danh sách nhạc từ MediaStore bất đồng bộ
│       │   │   └── TimeUtils.java              # Tiện ích định dạng thời gian chuẩn mm:ss / hh:mm:ss
│       │   └── view/
│       │       └── VinylWaveVisualizerView.java# Sóng nhạc đĩa than 60 FPS (Radial Bars, Ripples, Ribbon)
│       └── res/
│           ├── layout/                         # Giao diện XML (activity_main, bottom sheets, dialogs)
│           ├── drawable/                       # Vector kiếm Katana, đĩa Tsuba, Icons & Shapes
│           └── values/                         # Colors (Samurai Palette), Strings, Themes
└── desktop-companion/                          # Ứng dụng Desktop Companion & Windows Service
    ├── server.js                               # Node.js Sync Engine (HTTP 8899, UDP 8888, SSE)
    ├── VMusicDesktop.cs / .exe                 # Desktop System Tray GUI Application
    ├── VMusicService.cs / .exe                 # Windows Background Service
    ├── install-windows-service.bat             # Cài đặt tự khởi động cùng Windows
    └── public/                                 # Giao diện quản lý Web Browser & Logo Samurai
```

---

## 🚀 Hướng Dẫn Cài Đặt & Biên Dịch

### Yêu cầu:
- **JDK:** Java 17 trở lên
- **Android SDK:** Compile SDK 34 (Android 14)
- **Gradle:** 8.9 (đã kèm sẵn Gradle Wrapper)

### 1. Build file APK:
Mở terminal tại thư mục dự án và chạy:
```bash
# Windows
.\gradlew assembleDebug

# Linux / macOS
./gradlew assembleDebug
```
File APK sau khi build sẽ nằm tại:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Cài đặt trực tiếp vào điện thoại qua ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License
Phát hành theo giấy phép [MIT License](LICENSE).
