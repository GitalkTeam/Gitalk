package com.gitalk.domain.chatbot.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record WebhookEvent(
        String type,      // "issue" | "pull_request"
        String action,
        String repo,
        String title,
        String url,
        String author,
        LocalDateTime receivedAt
) {
    public WebhookEvent(String type, String action, String repo, String title, String url, String author) {
        this(type, action, repo, title, url, author, LocalDateTime.now());
    }

    @Override
    public String toString() {
        String icon = "pull_request".equals(type) ? "PR" : "Issue";
        return String.format("[%s] %s | %s\n  레포: %s | 작성자: %s\n  %s\n  받은 시각: %s",
                icon, action, title, repo, author, url,
                receivedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
