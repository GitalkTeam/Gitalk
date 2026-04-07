@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set SRC_DIR=src\main\java
set RESOURCES_DIR=src\main\resources
set OUT_DIR=out
set LIB_DIR=lib

if not exist "%LIB_DIR%" (
    echo lib 폴더 없음
    exit /b 1
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

set SRC_FILES=
for /R "%SRC_DIR%" %%f in (*.java) do (
    set SRC_FILES=!SRC_FILES! "%%f"
)

javac --release 21 -cp "%LIB_DIR%\*" -d "%OUT_DIR%" !SRC_FILES!

if errorlevel 1 (
    echo 컴파일 실패
    exit /b 1
)

xcopy "%RESOURCES_DIR%\*" "%OUT_DIR%\" /s /e /y >nul

echo 컴파일 성공