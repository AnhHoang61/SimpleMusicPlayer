# V-Music Desktop Sync 🎵⚔️

Ứng dụng đồng bộ nhạc không dây thời gian thực giữa máy tính (PC) và điện thoại chạy **V-Music** qua mạng nội bộ Wi-Fi và USB ADB.

---

## 🌟 Tính Năng Nổi Bật

- **Phần mềm Desktop chuyên dụng (`V-Music-Desktop.exe`):**
  - Chạy giao diện Desktop độc lập (Dark Samurai Theme).
  - Tích hợp biểu tượng khay hệ thống (System Tray) cạnh đồng hồ Windows.
  - Thu nhỏ xuống khay khi đóng cửa sổ, tiếp tục chạy ngầm đồng bộ nhạc 24/7.
  - Phím tắt tiện lợi: Mở thư mục nhạc, Kích hoạt đồng bộ tức thì, Khởi động cùng Windows.
- **Windows Service chạy tự động khi bật máy (`VMusicService.exe`):**
  - Đăng ký trực tiếp vào hệ thống quản lý dịch vụ `services.msc`.
  - Tự động chạy ngầm ngay khi Windows khởi động (ngay cả trước khi người dùng đăng nhập).
  - Tự động phục hồi khi có sự cố.
- **Giám sát thư mục nhạc thời gian thực (Live File Watcher):**
  - Khi bạn thêm, xóa hoặc đổi tên bài hát trong thư mục `music/`, server lập tức phát hiện trong 0.4s và phát tín hiệu UDP Beacon để điện thoại kéo nhạc về ngay.
- **Kéo & Thả Nhạc (Drag & Drop):** Kéo thả trực tiếp file nhạc (`.mp3`, `.flac`, `.wav`, `.m4a`...) vào cửa sổ ứng dụng trên máy tính.
- **Tự Động Phát Hiện Không Cần Cài Đặt (Zero Config):** Điện thoại và máy tính tự tìm thấy nhau qua UDP Discovery Beacon (Port 8888) hoặc ADB Reverse (Port 8899).
- **Nhật Ký Thời Gian Thực:** Hiển thị trực tiếp lịch sử thiết bị điện thoại kết nối và đồng bộ.

---

## 🚀 Hướng Dẫn Sử Dụng

### Cách 1: Chạy Ứng Dụng Desktop (Khuyên Dùng)
- Mở biểu tượng **V-Music Desktop Sync** trên màn hình Desktop của bạn (hoặc chạy trực tiếp file `D:\V-Music-Desktop-Sync\V-Music-Desktop.exe`).
- Ứng dụng sẽ mở cửa sổ làm việc và thu nhỏ xuống khay hệ thống (System Tray) để luôn sẵn sàng phục vụ điện thoại.
- Bạn có thể kéo thả bất kỳ file nhạc nào vào cửa sổ ứng dụng.

### Cách 2: Cài Đặt Làm Windows Service (Tự Động Chạy Khi Bật Máy)
- Nhấp chuột phải vào file `D:\V-Music-Desktop-Sync\install-windows-service.bat` ➔ chọn **Run as administrator** (hoặc nhấp đúp để tự kích hoạt UAC).
- Dịch vụ `VMusicSyncService` sẽ được cài đặt và tự động kích hoạt mỗi khi bạn bật máy tính mà không cần thao tác thêm.
- Để gỡ bỏ dịch vụ khi không cần: chạy file `uninstall-windows-service.bat`.

---

## 📱 Đồng Bộ Với Điện Thoại V-Music

1. Đảm bảo điện thoại và máy tính kết nối chung mạng Wi-Fi (hoặc cắm cáp USB).
2. Khi bạn thêm bất kỳ bài hát mới nào vào máy tính, điện thoại sẽ tự động phát hiện và tải về thư viện trong vài giây!
3. Hoặc nhấn nút **"📱 Đồng bộ tới điện thoại ngay"** trên giao diện máy tính để kích hoạt đồng bộ lập tức.
