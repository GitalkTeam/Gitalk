package com.gitalk.chat.repository;

import com.gitalk.chat.domain.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 인메모리 채팅 저장소 구현체 (로컬 테스트용)
 * 나중에 DB 연동 시 DatabaseChatRepository로 교체
 */
public class InMemoryChatRepository implements ChatRepository {

    private final Map<String, List<Message>> store = new HashMap<>();

    @Override
    public void saveMessage(Message message) {
        store.computeIfAbsent(message.getRoomId(), k -> new ArrayList<>())
             .add(message);
    }

    @Override
    public List<Message> findByRoomId(String roomId) {
        return Collections.unmodifiableList(
                store.getOrDefault(roomId, Collections.emptyList())
        );
    }
}
