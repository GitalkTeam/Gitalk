# Gitalk Chat Server image
# - 빌드 단계에서 src/ 컴파일
# - 런타임에 ChatServer main 실행
# - config.properties 는 volume 으로 주입 (이미지에 secret 안 박음)

FROM eclipse-temurin:21-jdk

WORKDIR /app

# 1. 의존성 jar (mysql/jline/gson/mongodb/bson)
COPY lib/ ./lib/

# 2. 소스
COPY src/ ./src/

# 3. 빌드 스크립트와 SQL 초기화 (init.sql 은 mysql 컨테이너 측에서 mount)
COPY compile.sh init.sql ./

# 4. 컴파일 (javac → out/)
RUN bash compile.sh

# 5. 외부 노출 포트
#    6000 - 채팅 socket
#    6001 - 이미지 업로드 HTTP
#    6002 - GitHub Webhook HTTP
EXPOSE 6000 6001 6002

# 6. 기본 실행: ChatServer.main
#    config.properties 는 /app/out/config.properties 위치에 volume mount 필요
ENTRYPOINT ["java", "-cp", "out:lib/*", "com.gitalk.domain.chat.socket.ChatServer"]
