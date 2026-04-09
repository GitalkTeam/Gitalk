package com.gitalk.domain.chat.repository;

import com.gitalk.domain.chat.domain.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 인메모리 구현체 - 로컬 테스트용, DB 미연결 환경에서 사용 */
public class InMemoryChatRepository implements MessageRepository {

    private final Map<Long, List<Message>> store = new ConcurrentHashMap<>();

    @Override
    public void save(Message message) {
        store.computeIfAbsent(message.getRoomId(), k -> new ArrayList<>()).add(message);
    }

    @Override
    public List<Message> findByRoomId(Long roomId) {
        return Collections.unmodifiableList(store.getOrDefault(roomId, Collections.emptyList()));
    }

    @Override
    public List<Message> findByRoomId(Long roomId, int limit) {
        List<Message> all = store.getOrDefault(roomId, Collections.emptyList());
        int from = Math.max(0, all.size() - limit);
        return Collections.unmodifiableList(all.subList(from, all.size()));
    }
}
