package com.gitalk.chat.socket;

import com.gitalk.chat.repository.InMemoryChatRepository;
import com.gitalk.chat.service.ChatService;

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
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("=================================");
            System.out.println("채팅 서버 시작 (포트: " + port + ")");
            System.out.println("=================================");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[연결] 클라이언트: " + socket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(socket, chatService);
                chatService.addClient(handler);
                handler.start();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        ChatService chatService = new ChatService(new InMemoryChatRepository());
        new ChatServer(PORT, chatService).start();
    }
}
