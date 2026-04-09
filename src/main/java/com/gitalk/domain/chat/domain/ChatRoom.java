package com.gitalk.domain.chat.domain;

import java.time.LocalDateTime;

public class ChatRoom {

    private Long roomId;
    private String name;
    private String type;       // TEAM / OPEN
    private String teamUrl;
    private Long creatorId;
    private String creatorNickname;  // 조회 시 JOIN으로 채움
    private LocalDateTime createdAt;

    // DB 조회용 (전체 필드)
    public ChatRoom(Long roomId, String name, String type, String teamUrl,
                    Long creatorId, String creatorNickname, LocalDateTime createdAt) {
        this.roomId = roomId;
        this.name = name;
        this.type = type;
        this.teamUrl = teamUrl;
        this.creatorId = creatorId;
        this.creatorNickname = creatorNickname;
        this.createdAt = createdAt;
    }

    // 신규 생성용 (roomId, createdAt 은 DB 자동 생성)
    public ChatRoom(String name, String type, String teamUrl, Long creatorId) {
        this.name = name;
        this.type = type;
        this.teamUrl = teamUrl;
        this.creatorId = creatorId;
    }

    public Long getRoomId()             { return roomId; }
    public String getName()             { return name; }
    public String getType()             { return type; }
    public String getTeamUrl()          { return teamUrl; }
    public Long getCreatorId()          { return creatorId; }
    public String getCreatorNickname()  { return creatorNickname; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
