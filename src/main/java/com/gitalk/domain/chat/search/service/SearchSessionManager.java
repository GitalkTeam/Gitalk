package com.gitalk.domain.chat.search.service;

/**
 * SearchSessionManager Description : 사용자 검색 세션과 공유용 세션 스냅샷을 관리하는 매니저입니다.
 * NOTE : search 지원 컴포넌트이며, 최근 검색 결과를 사용자 ID 또는 공유 ID에 매핑해 보관합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 5:40
 */
import com.gitalk.domain.chat.search.domain.SearchSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SearchSessionManager {

    private final Map<Long, SearchSession> userSessions = new ConcurrentHashMap<>();
    private final Map<String, SearchSession> sharedSessions = new ConcurrentHashMap<>();

    public void save(SearchSession session) {
        userSessions.put(session.getUserId(), session);
    }

    public SearchSession get(Long userId) {
        return userSessions.get(userId);
    }

    public void remove(Long userId) {
        userSessions.remove(userId);
    }

    public String share(SearchSession session) {
        String shareId = "SR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        sharedSessions.put(shareId, session);
        return shareId;
    }

    public SearchSession getShared(String shareId) {
        return sharedSessions.get(shareId);
    }

    public void saveShared(String shareId, SearchSession session) {
        if (shareId == null || shareId.isBlank() || session == null) {
            return;
        }
        sharedSessions.put(shareId, session);
    }

    public void clear() {
        userSessions.clear();
        sharedSessions.clear();
    }
}
