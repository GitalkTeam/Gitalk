package com.gitalk.chat.repository;

import com.gitalk.chat.domain.Message;

import java.util.List;

public interface ChatRepository {
    void saveMessage(Message message);
    List<Message> findByRoomId(String roomId);
}
