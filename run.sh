#!/bin/bash
# Gitalk 실행 스크립트

OUT_DIR="out"
LIB_DIR="lib"

java -cp "$OUT_DIR:$LIB_DIR/*" com.gitalk.GitalkApplication
