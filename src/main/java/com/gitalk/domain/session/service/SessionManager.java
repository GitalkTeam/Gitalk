package com.gitalk.domain.session.service;

import com.gitalk.domain.session.model.Session;
import com.gitalk.domain.user.model.Users;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private final Map<String, Session> sessionsById = new ConcurrentHashMap<>();
    private final Map<Long, String> sessionIdByUserId = new ConcurrentHashMap<>();
    private final long timeoutMinutes;

    public SessionManager(long timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public Session createSession(Users user) {
        Long userId = user.getUserid();

        String oldSessionId = sessionIdByUserId.get(userId);
        if (oldSessionId != null) {
            invalidate(oldSessionId);
        }

        String sessionId = UUID.randomUUID().toString();

        Session session = new Session(
                sessionId,
                user.getUserid(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileUrl(),
                timeoutMinutes
        );

        sessionsById.put(sessionId, session);
        sessionIdByUserId.put(userId, sessionId);

        return session;
    }

    public Session getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }

        Session session = sessionsById.get(sessionId);
        if (session == null) {
            return null;
        }

        if (session.isExpired()) {
            invalidate(sessionId);
            return null;
        }

        session.touch();
        return session;
    }

    public Session getSessionByUserId(Long userId) {
        String sessionId = sessionIdByUserId.get(userId);
        if (sessionId == null) {
            return null;
        }
        return getSession(sessionId);
    }

    public void invalidate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Session removed = sessionsById.remove(sessionId);
        if (removed != null) {
            sessionIdByUserId.remove(removed.getUserid());
        }
    }
}