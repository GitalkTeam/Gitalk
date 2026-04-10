package com.gitalk.domain.chat.domain;

/**
 * ChatMessage Description : 저장된 채팅 메시지와 검색용 메타데이터를 함께 표현하는 domain 모델입니다.(Mongo DB 저장용)
 * NOTE : domain 계층 객체이며, 키워드 검색과 문맥 조회를 위해 정규화된 본문 값을 함께 보관합니다.
 * @author jki
 * @since  04-09 (목) 오후 4:03
 */
import java.time.LocalDateTime;

public class ChatMessage {

    private final Long messageId;
    private final Long roomId;
    private final Long senderId;
    private final String senderNickname;
    private final String content;
    private final String normalizedContent;
    private final LocalDateTime createdAt;

    public ChatMessage(Long messageId,
                       Long roomId,
                       Long senderId,
                       String senderNickname,
                       String content,
                       String normalizedContent,
                       LocalDateTime createdAt) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.senderId = senderId;
        this.senderNickname = senderNickname;
        this.content = content;
        this.normalizedContent = normalizedContent;
        this.createdAt = createdAt;
    }

    public Long getMessageId() {
        return messageId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getSenderNickname() {
        return senderNickname;
    }

    public String getContent() {
        return content;
    }

    public String getNormalizedContent() {
        return normalizedContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
