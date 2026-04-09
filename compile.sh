#!/bin/bash
# Gitalk 컴파일 스크립트

SRC_DIR="src/main/java"
RESOURCES_DIR="src/main/resources"
OUT_DIR="out"
LIB_DIR="lib"
BIN_DIR="src/main/java/com/gitalk/common/api/ImageToAscii/ascii-image-converter"

# lib 폴더에 JAR이 있는지 확인
if [ -z "$(ls -A $LIB_DIR 2>/dev/null)" ]; then
    echo "오류: $LIB_DIR/ 폴더에 JAR 파일이 없습니다."
    echo "아래 파일을 다운로드해서 lib/ 에 넣어주세요:"
    echo "  - mysql-connector-j-8.x.x.jar (https://dev.mysql.com/downloads/connector/j/)"
    echo "  - gson-2.x.x.jar              (https://github.com/google/gson/releases)"
    exit 1
fi

# ── macOS / Linux: 바이너리 실행 권한 설정 ───────────────────────────────
if [[ "$OSTYPE" != msys* && "$OSTYPE" != cygwin* && "$OSTYPE" != win* ]]; then
    chmod +x "$BIN_DIR/mac_ascii-image-converter"   2>/dev/null
    chmod +x "$BIN_DIR/linux_ascii-image-converter" 2>/dev/null
fi

# ── Java 컴파일 ───────────────────────────────────────────────────────────
mkdir -p "$OUT_DIR"

SRC_FILES=$(find "$SRC_DIR" -name "*.java" ! -path "*/ascii-image-converter/*")

javac --release 21 -cp "$LIB_DIR/*" -d "$OUT_DIR" $SRC_FILES

if [ $? -eq 0 ]; then
    cp -r "$RESOURCES_DIR/." "$OUT_DIR/"
    echo "컴파일 성공"
else
    echo "컴파일 실패"
    exit 1
fi
