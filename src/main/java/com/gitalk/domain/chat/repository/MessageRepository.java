package com.gitalk.domain.chat.repository;

import com.gitalk.domain.chat.domain.Message;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository {
    void save(Message message);
    List<Message> findByRoomId(Long roomId);
    List<Message> findByRoomId(Long roomId, int limit);

    /**
     * 특정 방의 메시지 중 since 시각보다 나중에 생성된 메시지를 시간 오름차순으로 반환.
     * 미독 메시지 조회에 사용. since 가 null 이면 빈 리스트.
     */
    List<Message> findByRoomIdSince(Long roomId, LocalDateTime since, int limit);
}
