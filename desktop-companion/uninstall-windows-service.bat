@echo off
chcp 65001 >nul
title Gỡ bỏ V-Music Windows Service

:: Check for Administrative privileges
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo [INFO] Yêu cầu quyền Quản trị viên (Administrator) để gỡ bỏ Windows Service...
    powershell -Command "Start-Process cmd -ArgumentList '/c \"\"%~dp0uninstall-windows-service.bat\"\"' -Verb runAs"
    exit /b
)

cd /d "%~dp0"
set SERVICE_NAME=VMusicSyncService

echo =======================================================
echo          GỠ BỎ V-MUSIC WINDOWS SERVICE
echo =======================================================
echo.

echo Đang dừng dịch vụ %SERVICE_NAME%...
sc.exe stop "%SERVICE_NAME%" >nul 2>&1
timeout /t 2 /nobreak >nul

echo Đang xóa dịch vụ khỏi hệ thống...
sc.exe delete "%SERVICE_NAME%"

echo.
echo =======================================================
echo   ✅ ĐÃ GỠ BỎ WINDOWS SERVICE THÀNH CÔNG!
echo =======================================================
echo.
pause
