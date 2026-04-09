package com.gitalk.domain.chat.repository;

import java.util.List;

public interface ChatRoomMemberRepository {
    void addMember(Long roomId, Long userId);
    void removeMember(Long roomId, Long userId);
    boolean isMember(Long roomId, Long userId);
    List<Long> findUserIdsByRoomId(Long roomId);
    List<Long> findRoomIdsByUserId(Long userId);
}
