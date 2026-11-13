@echo off
chcp 65001 >nul
title Cài đặt V-Music Windows Service (Auto-Start on Boot)

:: Check for Administrative privileges
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Yêu cầu quyền Quản trị viên (Administrator) để tạo Windows Service...
    echo [INFO] Đang mở hộp thoại cấp quyền UAC...
    powershell -Command "Start-Process cmd -ArgumentList '/c \"\"%~dp0install-windows-service.bat\"\"' -Verb runAs"
    exit /b
)

cd /d "%~dp0"

echo =======================================================
echo      🎵 CÀI ĐẶT V-MUSIC WI-FI SYNC WINDOWS SERVICE 🎵   
echo =======================================================
echo.

set SERVICE_NAME=VMusicSyncService
set DISPLAY_NAME=V-Music Wi-Fi Auto-Sync Service
set BIN_PATH=%~dp0VMusicService.exe

:: Stop and delete existing service if any
sc.exe stop "%SERVICE_NAME%" >nul 2>&1
sc.exe delete "%SERVICE_NAME%" >nul 2>&1
timeout /t 1 /nobreak >nul

echo [1/3] Đang đăng ký Windows Service khởi động cùng máy tính...
sc.exe create "%SERVICE_NAME%" binPath= "\"%BIN_PATH%\"" start= auto DisplayName= "%DISPLAY_NAME%"

if %errorlevel% neq 0 (
    echo [LỖI] Không thể tạo service. Vui lòng kiểm tra lại quyền Administrator!
    pause
    exit /b
)

echo [2/3] Thiết lập tự động khởi động lại khi có sự cố...
sc.exe description "%SERVICE_NAME%" "Dịch vụ tự động đồng bộ thư viện nhạc qua Wi-Fi thời gian thực cho ứng dụng V-Music trên Android." >nul
sc.exe failure "%SERVICE_NAME%" reset= 86400 actions= restart/5000/restart/5000/restart/5000 >nul

echo [3/3] Đang khởi động service ngay bây giờ...
sc.exe start "%SERVICE_NAME%"

echo.
echo =======================================================
echo   ✅ CÀI ĐẶT THÀNH CÔNG WINDOWS SERVICE!
echo   * Tên dịch vụ: %SERVICE_NAME%
echo   * Trạng thái : Tự động chạy ngầm mỗi khi bật máy tính
echo   * Quản lý    : Trong services.msc
echo =======================================================
echo.
pause
