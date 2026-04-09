#!/bin/bash
# Gitalk 실행 스크립트
# 사용법:
#   bash run.sh                    → GitalkApplication (챗봇 + 채팅방)
#   bash run.sh server             → 채팅 서버
#   bash run.sh client             → 채팅 클라이언트 (로컬)
#   bash run.sh client <host>      → 원격 서버에 접속

chcp.com 65001 2>/dev/null  # Windows: UTF-8 설정 (비-Windows에서는 무시됨)

OUT_DIR="out"
LIB_DIR="lib"

# OS별 클래스패스 구분자 결정
if [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* || "$OSTYPE" == win* ]]; then
    SEP=";"
else
    SEP=":"
fi

CP="$OUT_DIR$SEP$LIB_DIR/*"

# 표준 입출력·파일 모두 UTF-8 강제 (Java 17 이하/구형 로케일 안전망)
JAVA_OPTS="-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"

case "$1" in
    server)
        java $JAVA_OPTS -cp "$CP" com.gitalk.domain.chat.socket.ChatServer
        ;;
    client)
        HOST="${2:-127.0.0.1}"
        java $JAVA_OPTS -cp "$CP" com.gitalk.domain.chat.client.ChatClient "$HOST"
        ;;
    *)
        java $JAVA_OPTS -cp "$CP" com.gitalk.GitalkApplication
        ;;
esac
