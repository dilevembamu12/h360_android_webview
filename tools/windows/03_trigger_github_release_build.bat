@echo off
setlocal
title H360 Android - Trigger GitHub Release Build

echo ============================================
echo  H360 Android - Trigger Release Workflow
echo ============================================
echo.

where gh >nul 2>&1
if errorlevel 1 (
  echo [ERROR] GitHub CLI (gh) not found.
  exit /b 1
)

set /p REPO=GitHub repo (owner/name) [example: dilevembamu12/H360-Android-WebView]: 
if "%REPO%"=="" (
  echo [ERROR] Repo is required.
  exit /b 1
)

set "WORKFLOW=android-release-signed.yml"
set "REF=main"

echo.
echo [INFO] Triggering workflow %WORKFLOW% on %REF%...
gh workflow run %WORKFLOW% -R %REPO% --ref %REF%
if errorlevel 1 (
  echo [ERROR] Failed to trigger workflow.
  exit /b 1
)

echo.
echo [OK] Workflow started.
echo Open GitHub Actions to monitor logs and download artifacts:
echo https://github.com/%REPO%/actions
echo.
exit /b 0

