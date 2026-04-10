package com.gitalk.domain.chat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository {
    void addMember(Long roomId, Long userId);
    void removeMember(Long roomId, Long userId);
    boolean isMember(Long roomId, Long userId);
    List<Long> findUserIdsByRoomId(Long roomId);
    List<Long> findRoomIdsByUserId(Long userId);

    /** 미독 메시지 기준점: 마지막 socket 종료 시각. 첫 입장 등으로 비어있으면 Optional.empty(). */
    Optional<LocalDateTime> getLastSeen(Long userId, Long roomId);

    /** 사용자가 특정 방에서 socket을 닫을 때 호출. row 가 없으면 무시. */
    void updateLastSeen(Long userId, Long roomId, LocalDateTime ts);
}
