package com.gitalk.model;

public record TrendingRepo(
        String fullName,
        String description,
        int stars,
        String language,
        String url
) {
    @Override
    public String toString() {
        return String.format("[%s] ⭐ %,d | %s\n  %s\n  %s",
                language != null ? language : "N/A",
                stars,
                fullName,
                description != null ? description : "(설명 없음)",
                url);
    }
}
