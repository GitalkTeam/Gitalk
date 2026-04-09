package com.gitalk.domain.chat.repository;

import com.gitalk.domain.chat.domain.Notice;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository {
    Notice save(Notice notice);
    Optional<Notice> findById(Long noticeId);
    List<Notice> findByRoomId(Long roomId);
    List<Notice> findRecentByUserId(Long userId, int limit);
}
