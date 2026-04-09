package com.gitalk.domain.chat.service;

import com.gitalk.domain.chat.domain.Message;
import com.gitalk.domain.chat.repository.MessageRepository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatService {

    /** 방별 접속 클라이언트. 같은 방에 속한 클라이언트끼리만 메시지를 주고받는다. */
    private final Map<Long, List<MessageSender>> roomClients = new ConcurrentHashMap<>();
    private final MessageRepository messageRepository;

    public ChatService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public void addClient(Long roomId, MessageSender sender) {
        roomClients.computeIfAbsent(roomId, k -> new CopyOnWriteArrayList<>()).add(sender);
    }

    public void removeClient(Long roomId, MessageSender sender) {
        List<MessageSender> list = roomClients.get(roomId);
        if (list != null) list.remove(sender);
    }

    /** 메시지를 저장하고 같은 방의 송신자 외 클라이언트에게 브로드캐스트 */
    public void broadcast(Message message, MessageSender sender) {
        messageRepository.save(message);
        String packet = Protocol.buildMsgPacket(message.getSenderNickname(), message.getContent());
        sendToRoom(message.getRoomId(), packet, sender);
    }

    /** 챗봇 메시지를 DB 저장 없이 같은 방의 송신자 외 클라이언트에게 브로드캐스트 */
    public void broadcastBot(Long roomId, String packet, MessageSender sender) {
        sendToRoom(roomId, packet, sender);
    }

    public void broadcastSystemMessage(Long roomId, String text) {
        sendToRoom(roomId, Protocol.buildServerPacket(text), null);
    }

    /** ASCII 아트를 같은 방의 모든 클라이언트에게 브로드캐스트 (DB 저장 없음) */
    public void broadcastAsciiArt(Long roomId, String sender, String filename, String asciiArt) {
        String packet = Protocol.buildAsciiArtPacket(sender, filename, asciiArt);
        sendToRoom(roomId, packet, null);
    }

    public int getOnlineCount(Long roomId) {
        List<MessageSender> list = roomClients.get(roomId);
        return list == null ? 0 : list.size();
    }

    public List<Message> getRecentMessages(Long roomId, int limit) {
        return messageRepository.findByRoomId(roomId, limit);
    }

    private void sendToRoom(Long roomId, String packet, MessageSender exclude) {
        List<MessageSender> list = roomClients.get(roomId);
        if (list == null) return;
        for (MessageSender client : list) {
            if (client != exclude) client.sendRaw(packet);
        }
    }
}
