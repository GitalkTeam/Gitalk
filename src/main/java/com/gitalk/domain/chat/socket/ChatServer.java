package com.gitalk.domain.chat.socket;

import com.gitalk.common.api.GithubWebhookServer;
import com.gitalk.common.api.ImageAsciiHttpServer;
import com.gitalk.domain.chat.config.MongoConnectionManager;
import com.gitalk.domain.chat.repository.*;
import com.gitalk.domain.chat.service.ChatService;
import com.gitalk.domain.chat.search.service.SearchShareService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {

    private static final int PORT = 6000;

    private final int port;
    private final ChatService chatService;
    private final SearchShareService searchShareService;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatRoomRepository chatRoomRepository;

    public ChatServer(int port,
                      ChatService chatService,
                      SearchShareService searchShareService,
                      ChatRoomMemberRepository memberRepository,
                      ChatRoomRepository chatRoomRepository) {
        this.port = port;
        this.chatService = chatService;
        this.searchShareService = searchShareService;
        this.memberRepository = memberRepository;
        this.chatRoomRepository = chatRoomRepository;
    }

    public void start() throws IOException {
        // 이미지 업로드 → ASCII 아트 변환용 임베디드 HTTP 서버
        ImageAsciiHttpServer httpServer = new ImageAsciiHttpServer(chatService);
        httpServer.start();

        // GitHub Webhook 수신 서버 (방 단위 라우팅 + HMAC 검증)
        GithubWebhookServer webhookServer = new GithubWebhookServer(chatRoomRepository, chatService);
        webhookServer.start();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("=================================");
            System.out.println("채팅 서버 시작 (포트: " + port + ")");
            System.out.println("이미지 변환 서버 (포트: " + ImageAsciiHttpServer.HTTP_PORT + ")");
            System.out.println("Webhook 서버 (포트: " + GithubWebhookServer.HTTP_PORT + ")");
            System.out.println("=================================");
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[연결] 클라이언트: " + socket.getInetAddress().getHostAddress());
                ClientHandler handler = new ClientHandler(socket, chatService, searchShareService, memberRepository);
                handler.start();
            }
        }
    }

    public static void main(String[] args) throws IOException {
        MongoConnectionManager connectionManager = MongoConnectionManager.getInstance();

        MongoChatMessageRepository messageRepository = new MongoChatMessageRepository(connectionManager);
        messageRepository.createIndexes();

        ChatService chatService = new ChatService(messageRepository);
        SearchShareService searchShareService = new SearchShareService();
        ChatRoomMemberRepository memberRepository = new ChatRoomMemberRepositoryImpl();
        ChatRoomRepository chatRoomRepository = new ChatRoomRepositoryImpl();
        Runtime.getRuntime().addShutdownHook(new Thread(connectionManager::close));
        new ChatServer(PORT, chatService, searchShareService, memberRepository, chatRoomRepository).start();
    }
}
