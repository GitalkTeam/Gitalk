package com.gitalk.domain.chat.repository;

import com.gitalk.domain.chat.domain.ChatRoom;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository {
    ChatRoom save(ChatRoom room);
    Optional<ChatRoom> findById(Long roomId);
    List<ChatRoom> findAll();
    List<ChatRoom> findByType(String type);
    boolean existsByTypeAndName(String type, String name);
    void deleteById(Long roomId);
}
