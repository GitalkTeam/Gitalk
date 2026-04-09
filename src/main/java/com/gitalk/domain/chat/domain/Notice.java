package com.gitalk.domain.chat.domain;

import java.time.LocalDateTime;

public class Notice {

    private Long noticeId;
    private Long userId;
    private Long roomId;
    private String title;
    private String content;
    private String authorNickname;  // 조회 시 JOIN으로 채움
    private LocalDateTime createdAt;

    // 신규 작성용
    public Notice(Long userId, Long roomId, String title, String content) {
        this.userId = userId;
        this.roomId = roomId;
        this.title = title;
        this.content = content;
    }

    // DB 조회용
    public Notice(Long noticeId, Long userId, Long roomId, String title,
                  String content, String authorNickname, LocalDateTime createdAt) {
        this.noticeId = noticeId;
        this.userId = userId;
        this.roomId = roomId;
        this.title = title;
        this.content = content;
        this.authorNickname = authorNickname;
        this.createdAt = createdAt;
    }

    public Long getNoticeId()          { return noticeId; }
    public Long getUserId()            { return userId; }
    public Long getRoomId()            { return roomId; }
    public String getTitle()           { return title; }
    public String getContent()         { return content; }
    public String getAuthorNickname()  { return authorNickname; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
}
