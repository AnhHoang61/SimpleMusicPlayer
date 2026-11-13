@echo off
title V-Music Desktop Sync Server
cd /d "%~dp0"

echo ========================================================
echo   Dang khoi dong V-Music Desktop Sync Server...
echo ========================================================

:: Khoi dong server Node.js trong background
start "VMusicServer" /min node server.js

:: Cho 1 giay de server khoi tao xong
timeout /t 1 /nobreak >nul

:: Mo giao dien ung dung bang che do App Window (khong co thanh dia chi, nhu app desktop)
set EDGE_PATH="C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
if exist %EDGE_PATH% (
    start "" %EDGE_PATH% --app="http://localhost:8899" --window-size=980,720
) else (
    start http://localhost:8899
)

echo Ung dung da mo! Hay keo tha file nhac vao cua so ung dung.
exit
