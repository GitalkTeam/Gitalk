package com.gitalk.domain.chat.repository;

import com.gitalk.domain.chat.domain.Message;

import java.util.List;

public interface MessageRepository {
    void save(Message message);
    List<Message> findByRoomId(Long roomId);
    List<Message> findByRoomId(Long roomId, int limit);
}
