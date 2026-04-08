##!/bin/bash
## Gitalk 실행 스크립트
#
#OUT_DIR="out"
#LIB_DIR="lib"
#
#java -cp "$OUT_DIR:$LIB_DIR/*" com.gitalk.GitalkApplication
#!/bin/bash
# Gitalk 실행 스크립트
# 사용법:
#   bash run.sh          → GitalkApplication (챗봇)
#   bash run.sh server   → 채팅 서버
#   bash run.sh client   → 채팅 클라이언트
chcp.com 65001 #UTF-8 설정
OUT_DIR="out"
LIB_DIR="lib"

case "$1" in
    server)
        java -cp "$OUT_DIR;$LIB_DIR/*" com.gitalk.chat.socket.ChatServer
        ;;
    client)
        java -cp "$OUT_DIR;$LIB_DIR/*" com.gitalk.chat.client.ChatClient
        ;;
    *)
        java -cp "$OUT_DIR;$LIB_DIR/*" com.gitalk.GitalkApplication
        ;;
esac
