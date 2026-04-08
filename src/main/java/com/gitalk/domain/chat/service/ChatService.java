package com.gitalk.chat.service;

import com.gitalk.chat.domain.Message;
import com.gitalk.chat.repository.ChatRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatService {

    private final List<MessageSender> clients = Collections.synchronizedList(new ArrayList<>());
    private final ChatRepository chatRepository;
    private static final String DEFAULT_ROOM = "general";

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public void addClient(MessageSender sender) {
        clients.add(sender);
    }

    public void removeClient(MessageSender sender) {
        clients.remove(sender);
    }

    /** 메시지를 저장하고 송신자를 제외한 모든 클라이언트에게 브로드캐스트 */
    public void broadcast(Message message, MessageSender sender) {
        chatRepository.saveMessage(message);
        String packet = Protocol.buildMsgPacket(message.getSender(), message.getContent());
        synchronized (clients) {
            for (MessageSender client : clients) {
                if (client != sender) {
                    client.sendRaw(packet);
                }
            }
        }
    }

    public void broadcastSystemMessage(String text) {
        String packet = Protocol.buildServerPacket(text);
        synchronized (clients) {
            for (MessageSender client : clients) {
                client.sendRaw(packet);
            }
        }
    }

    public int getOnlineCount() {
        return clients.size();
    }

    public String getDefaultRoom() {
        return DEFAULT_ROOM;
    }
}
