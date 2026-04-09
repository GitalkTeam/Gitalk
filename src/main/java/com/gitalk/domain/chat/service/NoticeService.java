package com.gitalk.domain.chat.service;

import com.gitalk.domain.chat.domain.Notice;
import com.gitalk.domain.chat.repository.NoticeRepository;

import java.util.List;

public class NoticeService {

    private final NoticeRepository noticeRepository;

    public NoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    public Notice post(Long userId, Long roomId, String title, String content) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("제목을 입력해주세요.");
        return noticeRepository.save(new Notice(userId, roomId, title, content == null ? "" : content));
    }

    public List<Notice> getNotices(Long roomId) {
        return noticeRepository.findByRoomId(roomId);
    }

    public Notice getNotice(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new RuntimeException("공지를 찾을 수 없습니다."));
    }

    public List<Notice> getRecentForUser(Long userId) {
        return noticeRepository.findRecentByUserId(userId, 5);
    }
}
