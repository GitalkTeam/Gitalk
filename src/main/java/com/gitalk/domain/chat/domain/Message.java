package com.gitalk.domain.chat.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Message {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Long messageId;
    private Long userId;
    private Long roomId;
    private String content;
    private String senderNickname;  // 소켓 표시용
    private LocalDateTime createdAt;

    // 소켓 송신용 (새 메시지 생성)
    public Message(Long userId, String senderNickname, String content, Long roomId) {
        this.userId = userId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.roomId = roomId;
        this.createdAt = LocalDateTime.now();
    }

    // DB 조회용 (전체 필드)
    public Message(Long messageId, Long userId, Long roomId, String content, String senderNickname, LocalDateTime createdAt) {
        this.messageId = messageId;
        this.userId = userId;
        this.roomId = roomId;
        this.content = content;
        this.senderNickname = senderNickname;
        this.createdAt = createdAt;
    }

    public Long getMessageId()          { return messageId; }
    public Long getUserId()             { return userId; }
    public Long getRoomId()             { return roomId; }
    public String getContent()          { return content; }
    public String getSenderNickname()   { return senderNickname; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getFormattedTime()    { return createdAt.format(FORMATTER); }
}
