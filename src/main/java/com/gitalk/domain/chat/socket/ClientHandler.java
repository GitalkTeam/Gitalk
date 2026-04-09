package com.gitalk.domain.chat.socket;

import com.gitalk.domain.chat.domain.Message;
import com.gitalk.domain.chat.service.ChatService;
import com.gitalk.domain.chat.service.MessageSender;
import com.gitalk.domain.chat.service.Protocol;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
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
    private Long userId;  // 로그인 연동 후 설정
    private Long roomId;  // JOIN 패킷에서 설정

    public ClientHandler(Socket socket, ChatService chatService) {
        this.socket = socket;
        this.chatService = chatService;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            if (!handleJoin()) { disconnect(); return; }

            chatService.broadcastSystemMessage(roomId,
                    nickname + "님이 입장했습니다. (현재 " + chatService.getOnlineCount(roomId) + "명)");
            handleChat();

        } catch (IOException e) {
            System.out.println("[오류] 클라이언트 오류: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    /** JOIN:userId:roomId:nickname 파싱 */
    private boolean handleJoin() throws IOException {
        String packet = in.readLine();
        if (packet == null) return false;

        String[] parts = Protocol.parseJoin(packet);
        if (parts.length < 4 || !Protocol.JOIN.equals(parts[0])) {
            sendRaw(Protocol.buildJoinFailed("잘못된 패킷 형식입니다."));
            return false;
        }

        try {
            this.userId = Long.parseLong(parts[1].trim());
            this.roomId = Long.parseLong(parts[2].trim());
        } catch (NumberFormatException e) {
            sendRaw(Protocol.buildJoinFailed("잘못된 패킷 형식입니다."));
            return false;
        }

        String nick = parts[3].trim();
        if (nick.isBlank()) {
            sendRaw(Protocol.buildJoinFailed("닉네임을 입력해주세요."));
            return false;
        }

        this.nickname = nick;
        chatService.addClient(this.roomId, this);
        sendRaw(Protocol.buildJoinSuccess(nick));
        System.out.println("[입장 roomId=" + this.roomId + "] " + nick);
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
                    Message msg = new Message(userId, nickname, parts[1], roomId);
                    chatService.broadcast(msg, this);
                    System.out.println("[채팅 roomId=" + roomId + "] " + nickname + ": " + parts[1]);
                }
            } else if (Protocol.BOT.equals(type)) {
                String[] parts = Protocol.parse(packet);
                if (parts.length >= 2) {
                    chatService.broadcastBot(roomId, Protocol.buildBotPacket(parts[1]), this);
                }
            }
        }
    }

    private void disconnect() {
        if (roomId != null) {
            chatService.removeClient(roomId, this);
            if (nickname != null) {
                chatService.broadcastSystemMessage(roomId, nickname + "님이 퇴장했습니다.");
            }
        }
        if (nickname != null) System.out.println("[퇴장] " + nickname);
        try { socket.close(); } catch (IOException ignored) {}
    }

    public void setUserId(Long userId) { this.userId = userId; }

    @Override public void sendRaw(String rawMessage) { if (out != null) out.println(rawMessage); }
    @Override public String getNickname() { return nickname != null ? nickname : "(알 수 없음)"; }
    @Override public boolean isAuthenticated() { return nickname != null; }
}
