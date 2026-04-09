package com.gitalk.domain.chat.search.service;

import com.gitalk.domain.chat.search.domain.SearchSession;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SearchShareService {
    private final Map<String, SearchSession> sharedSessions = new ConcurrentHashMap<>();

    public SearchShareService() {
    }
    public String shareSession(SearchSession session) {
        String shareId = "SR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        sharedSessions.put(shareId, session);
        return shareId;
    }
    public SearchSession getSharedSession(String shareId) {
        return sharedSessions.get(shareId);
    }
}
