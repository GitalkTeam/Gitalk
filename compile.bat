@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM Gitalk 컴파일 스크립트

set "SRC_DIR=src\main\java"
set "RESOURCES_DIR=src\main\resources"
set "OUT_DIR=out"
set "LIB_DIR=lib"
set "BIN_DIR=src\main\java\com\gitalk\common\api\ImageToAscii\ascii-image-converter"

REM lib 폴더에 JAR이 있는지 확인
if not exist "%LIB_DIR%\*.jar" (
    echo 오류: %LIB_DIR%\ 폴더에 JAR 파일이 없습니다.
    echo 아래 파일을 다운로드해서 lib\ 에 넣어주세요:
    echo   - mysql-connector-j-8.x.x.jar ^(https://dev.mysql.com/downloads/connector/j/^)
    echo   - gson-2.x.x.jar              ^(https://github.com/google/gson/releases^)
    exit /b 1
)

REM Java 컴파일
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

set "SRC_LIST=%TEMP%\gitalk_sources.txt"
if exist "%SRC_LIST%" del "%SRC_LIST%"

for /r "%SRC_DIR%" %%f in (*.java) do (
    echo %%f | findstr /i /c:"ascii-image-converter" >nul
    if errorlevel 1 (
        echo %%f>>"%SRC_LIST%"
    )
)

javac -encoding UTF-8 --release 21 -cp "%LIB_DIR%\*" -d "%OUT_DIR%" @"%SRC_LIST%"

if %errorlevel% equ 0 (
    xcopy "%RESOURCES_DIR%\*" "%OUT_DIR%\" /e /i /y >nul
    echo 컴파일 성공
) else (
    echo 컴파일 실패
    if exist "%SRC_LIST%" del "%SRC_LIST%"
    exit /b 1
)

if exist "%SRC_LIST%" del "%SRC_LIST%"
endlocal