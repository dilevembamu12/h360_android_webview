@echo off
setlocal ENABLEDELAYEDEXPANSION
title H360 Android - Prepare Keystore Base64

echo ============================================
echo  H360 Android - Keystore to Base64
echo ============================================
echo.

if "%~1"=="" (
  set /p KEYSTORE_PATH=Enter keystore path [jks/keystore]: 
) else (
  set "KEYSTORE_PATH=%~1"
)

if not exist "%KEYSTORE_PATH%" (
  echo [ERROR] Keystore not found: %KEYSTORE_PATH%
  exit /b 1
)

set "OUT_FILE=%~dp0android_keystore.base64.txt"
powershell -NoProfile -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('%KEYSTORE_PATH%')) | Out-File '%OUT_FILE%' -Encoding ascii"

if errorlevel 1 (
  echo [ERROR] Failed to encode keystore.
  exit /b 1
)

echo.
echo [OK] Base64 file generated:
echo      %OUT_FILE%
echo.
echo Next step:
echo   Run 02_configure_github_secrets.bat
echo.
exit /b 0
