package com.gitalk.domain.chat.socket;

import com.gitalk.common.api.ImageAsciiHttpServer;
import com.gitalk.domain.chat.repository.InMemoryChatRepository;
import com.gitalk.domain.chat.repository.MessageRepositoryImpl;
import com.gitalk.domain.chat.service.ChatService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {

    private static final int PORT = 6000;

    private final int port;
    private final ChatService chatService;

    public ChatServer(int port, ChatService chatService) {
        this.port = port;
        this.chatService = chatService;
    }

    public void start() throws IOException {
        // 이미지 업로드 → ASCII 아트 변환용 임베디드 HTTP 서버
        ImageAsciiHttpServer httpServer = new ImageAsciiHttpServer(chatService);
        httpServer.start();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("=================================");
            System.out.println("채팅 서버 시작 (포트: " + port + ")");
            System.out.println("이미지 변환 서버 (포트: " + ImageAsciiHttpServer.HTTP_PORT + ")");
            System.out.println("=================================");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[연결] 클라이언트: " + socket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(socket, chatService);
                handler.start();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        // DB 연동: MessageRepositoryImpl / 로컬 테스트: InMemoryChatRepository
        ChatService chatService = new ChatService(new MessageRepositoryImpl());
        new ChatServer(PORT, chatService).start();
    }
}
