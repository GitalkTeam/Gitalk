package com.gitalk.chat.socket;

import com.gitalk.chat.domain.Message;
import com.gitalk.chat.service.ChatService;
import com.gitalk.chat.service.MessageSender;
import com.gitalk.chat.service.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 소켓 클라이언트 1명당 1개 생성되는 핸들러
 * DB 연동 후 handleJoin() 대신 handleLogin() 으로 교체 예정
 */
public class ClientHandler extends Thread implements MessageSender {

    private final Socket socket;
    private final ChatService chatService;
    private BufferedReader in;
    private PrintWriter out;
    private String nickname;

    public ClientHandler(Socket socket, ChatService chatService) {
        this.socket = socket;
        this.chatService = chatService;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true);

            if (!handleJoin()) {
                disconnect();
                return;
            }

            chatService.broadcastSystemMessage(nickname + "님이 입장했습니다. (현재 " + chatService.getOnlineCount() + "명)");
            handleChat();

        } catch (IOException e) {
            System.out.println("[오류] 클라이언트 오류: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    /** 닉네임만 받아서 입장 처리 (로그인 없음) */
    private boolean handleJoin() throws IOException {
        String packet = in.readLine();
        if (packet == null) return false;

        String[] parts = Protocol.parse(packet);
        if (!Protocol.JOIN.equals(parts[0])) {
            sendRaw(Protocol.buildJoinFailed("잘못된 패킷 형식입니다."));
            return false;
        }

        String nick = parts.length > 1 ? parts[1].trim() : "";
        if (nick.isBlank()) {
            sendRaw(Protocol.buildJoinFailed("닉네임을 입력해주세요."));
            return false;
        }

        this.nickname = nick;
        sendRaw(Protocol.buildJoinSuccess(nick));
        System.out.println("[입장] " + nick);
        return true;
    }

    private void handleChat() throws IOException {
        String packet;
        while ((packet = in.readLine()) != null) {
            String type = Protocol.typeOf(packet);

            if (Protocol.QUIT.equals(type)) break;

            if (Protocol.MSG.equals(type)) {
                String[] parts = Protocol.parse(packet);
                if (parts.length >= 2 && !parts[1].isBlank()) {
                    Message msg = new Message(nickname, parts[1], chatService.getDefaultRoom());
                    chatService.broadcast(msg, this);
                    System.out.println("[채팅] " + nickname + ": " + parts[1]);
                }
            }
        }
    }

    private void disconnect() {
        chatService.removeClient(this);
        if (nickname != null) {
            chatService.broadcastSystemMessage(nickname + "님이 퇴장했습니다.");
            System.out.println("[퇴장] " + nickname);
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    @Override
    public void sendRaw(String rawMessage) {
        if (out != null) out.println(rawMessage);
    }

    @Override
    public String getNickname() {
        return nickname != null ? nickname : "(알 수 없음)";
    }

    @Override
    public boolean isAuthenticated() {
        return nickname != null;
    }
}
