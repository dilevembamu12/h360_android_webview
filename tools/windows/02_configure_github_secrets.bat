@echo off
setlocal ENABLEDELAYEDEXPANSION
title H360 Android - Configure GitHub Secrets

echo ============================================
echo  H360 Android - GitHub Secrets Setup
echo ============================================
echo.

where gh >nul 2>&1
if errorlevel 1 (
  echo [ERROR] GitHub CLI (gh) not found.
  echo Install: https://cli.github.com/
  exit /b 1
)

gh auth status >nul 2>&1
if errorlevel 1 (
  echo [INFO] You are not logged in. Running: gh auth login
  gh auth login
  if errorlevel 1 (
    echo [ERROR] gh auth login failed.
    exit /b 1
  )
)

set "BASE64_FILE=%~dp0android_keystore.base64.txt"
if not exist "%BASE64_FILE%" (
  echo [ERROR] Missing %BASE64_FILE%
  echo Run 01_prepare_keystore_base64.bat first.
  exit /b 1
)

set /p REPO=GitHub repo (owner/name) [example: dilevembamu12/H360-Android-WebView]: 
if "%REPO%"=="" (
  echo [ERROR] Repo is required.
  exit /b 1
)

echo.
set /p STORE_PASS=ANDROID_SIGNING_STORE_PASSWORD: 
set /p KEY_ALIAS=ANDROID_SIGNING_KEY_ALIAS: 
set /p KEY_PASS=ANDROID_SIGNING_KEY_PASSWORD: 

echo.
echo [INFO] Setting secrets in %REPO% ...
gh secret set ANDROID_KEYSTORE_BASE64 -R %REPO% < "%BASE64_FILE%"
if errorlevel 1 goto :secret_failed

echo %STORE_PASS%| gh secret set ANDROID_SIGNING_STORE_PASSWORD -R %REPO%
if errorlevel 1 goto :secret_failed

echo %KEY_ALIAS%| gh secret set ANDROID_SIGNING_KEY_ALIAS -R %REPO%
if errorlevel 1 goto :secret_failed

echo %KEY_PASS%| gh secret set ANDROID_SIGNING_KEY_PASSWORD -R %REPO%
if errorlevel 1 goto :secret_failed

echo.
echo [OK] GitHub secrets configured successfully.
echo Next step:
echo   Run 03_trigger_github_release_build.bat
exit /b 0

:secret_failed
echo.
echo [ERROR] Failed to set one or more secrets.
exit /b 1

