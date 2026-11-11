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
- **Màn hình Full Player (Bottom Sheet):**
  - Hiệu ứng đĩa than (Vinyl Disc) xoay mượt mà khi phát nhạc và tự động dừng khi tạm dừng.
  - Thanh tua nhạc (SeekBar) kéo đến bất kỳ mốc thời gian nào của bài hát.
  - Chế độ **Phát ngẫu nhiên (Shuffle)** và **Lặp lại (Repeat One / Repeat All)**.
- **Phát nhạc chạy ngầm (Foreground Service):**
  - Tiếp tục phát nhạc mượt mà ngay cả khi tắt màn hình, khóa máy hoặc sử dụng ứng dụng khác.
  - Thanh điều khiển trên Notification bar và màn hình khóa (Android 13+ & 14+ tương thích).
- **Hỗ trợ quyền đầy đủ:** Xử lý cấp quyền động mượt mà cho cả Android 13+ (`READ_MEDIA_AUDIO`, `POST_NOTIFICATIONS`) và Android 12 trở xuống (`READ_EXTERNAL_STORAGE`).

---

## 📸 Cấu Trúc Dự Án

```text
SimpleMusicPlayer/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/simplemusicplayer/
│       │   ├── MainActivity.java           # Quản lý giao diện, cấp quyền, tìm kiếm
│       │   ├── MusicPlayerManager.java     # Singleton điều khiển MediaPlayer & playlist
│       │   ├── MusicService.java           # Foreground Service phát nhạc ngầm & Notification
│       │   ├── MusicAdapter.java           # Danh sách bài hát & bộ lọc Filterable
│       │   └── Song.java                   # Model dữ liệu bài hát
│       └── res/
│           ├── layout/                     # Giao diện XML (activity_main, item_song, bottom_sheet_player)
│           ├── drawable/                   # Vector icons & shapes
│           └── values/                     # Colors, Strings, Themes
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
