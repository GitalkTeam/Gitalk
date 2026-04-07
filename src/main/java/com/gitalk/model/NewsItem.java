package com.gitalk.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record NewsItem(
        int id,
        String title,
        String url,
        int score,
        String by,
        long time
) {
    @Override
    public String toString() {
        String link = url != null ? url : "https://news.ycombinator.com/item?id=" + id;
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.of("Asia/Seoul"));
        String formatted = dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        return String.format("▶ %s\n  점수: %d | 작성자: %s | %s\n  %s",
                title, score, by, formatted, link);
    }
}
