#!/bin/bash
# Gitalk 컴파일 스크립트

SRC_DIR="src/main/java"
RESOURCES_DIR="src/main/resources"
OUT_DIR="out"
LIB_DIR="lib"

# lib 폴더에 JAR이 있는지 확인
if [ -z "$(ls -A $LIB_DIR 2>/dev/null)" ]; then
    echo "오류: $LIB_DIR/ 폴더에 JAR 파일이 없습니다."
    echo "아래 파일을 다운로드해서 lib/ 에 넣어주세요:"
    echo "  - mysql-connector-j-8.x.x.jar (https://dev.mysql.com/downloads/connector/j/)"
    echo "  - gson-2.x.x.jar              (https://github.com/google/gson/releases)"
    exit 1
fi

mkdir -p "$OUT_DIR"

SRC_FILES=$(find "$SRC_DIR" -name "*.java")

javac --release 21 -cp "$LIB_DIR/*" -d "$OUT_DIR" $SRC_FILES

if [ $? -eq 0 ]; then
    cp -r "$RESOURCES_DIR/." "$OUT_DIR/"
    echo "컴파일 성공"
else
    echo "컴파일 실패"
    exit 1
fi
