package com.gitalk.domain.chat.domain;

import java.time.LocalDateTime;

public class ChatRoom {

    private Long roomId;
    private String name;
    private String type;       // TEAM / OPEN
    private String teamUrl;            // TEAM 방의 GitHub repo URL (선택)
    private String webhookSecret;      // 방 단위 HMAC secret (자동 발급, 외부 노출 금지)
    private Long   webhookId;          // GitHub 측 webhook id (자동 해제용)
    private String description;        // OPEN 방 토픽 설명 (TEAM은 보통 null)
    private Long creatorId;
    private String creatorNickname;  // 조회 시 JOIN으로 채움
    private LocalDateTime createdAt;

    // DB 조회용 (전체 필드)
    public ChatRoom(Long roomId, String name, String type, String teamUrl,
                    String webhookSecret, Long webhookId, String description,
                    Long creatorId, String creatorNickname, LocalDateTime createdAt) {
        this.roomId = roomId;
        this.name = name;
        this.type = type;
        this.teamUrl = teamUrl;
        this.webhookSecret = webhookSecret;
        this.webhookId = webhookId;
        this.description = description;
        this.creatorId = creatorId;
        this.creatorNickname = creatorNickname;
        this.createdAt = createdAt;
    }

    // 신규 생성용 (roomId, createdAt, webhook 필드는 추후 채움)
    public ChatRoom(String name, String type, String teamUrl, String description, Long creatorId) {
        this.name = name;
        this.type = type;
        this.teamUrl = teamUrl;
        this.description = description;
        this.creatorId = creatorId;
    }

    public Long getRoomId()             { return roomId; }
    public String getName()             { return name; }
    public String getType()             { return type; }
    public String getTeamUrl()          { return teamUrl; }
    public String getWebhookSecret()    { return webhookSecret; }
    public Long getWebhookId()          { return webhookId; }
    public String getDescription()      { return description; }
    public Long getCreatorId()          { return creatorId; }
    public String getCreatorNickname()  { return creatorNickname; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isOpen() {
        return "OPEN".equals(type);
    }

    public boolean hasRepo() {
        return teamUrl != null && !teamUrl.isBlank();
    }
}
