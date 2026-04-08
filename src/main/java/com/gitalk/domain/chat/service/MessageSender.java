package com.gitalk.chat.service;

/**
 * 메시지 송신 인터페이스
 * ChatService가 소켓 구현체(ClientHandler)에 직접 의존하지 않도록 분리
 * 나중에 WebSocket, SSE 등 다른 전송 방식으로 교체 가능
 */
public interface MessageSender {

    void sendRaw(String rawMessage);

    String getNickname();

    boolean isAuthenticated();
}
