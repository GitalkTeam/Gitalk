package com.gitalk.domain.session.model;

/**
 * Session Description :
 * NOTE :
 *
 * @author jki
 * @since 04-08 (수) 오전 10:16
 */
import java.time.LocalDateTime;

public class Session {
    private final String sessionId;
    private final Long userid;
    private final String email;
    private final String nickname;
    private final String profileUrl;
    private final LocalDateTime createdAt;
    private LocalDateTime lastAccessAt;
    private final long timeoutMinutes;

    public Session(String sessionId, Long userid, String email, String nickname, String profileUrl, long timeoutMinutes) {
        this.sessionId = sessionId;
        this.userid = userid;
        this.email = email;
        this.nickname = nickname;
        this.profileUrl = profileUrl;
        this.timeoutMinutes = timeoutMinutes;
        this.createdAt = LocalDateTime.now();
        this.lastAccessAt = LocalDateTime.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Long getUserid() {
        return userid;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public void touch() {
        this.lastAccessAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return lastAccessAt.plusMinutes(timeoutMinutes).isBefore(LocalDateTime.now());
    }
}