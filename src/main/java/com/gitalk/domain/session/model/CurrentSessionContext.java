package com.gitalk.domain.session.model;

/**
 * CurrentSessionContext Description :
 * NOTE :
 *
 * @author jki
 * @since 04-08 (수) 오전 11:35
 */
import com.gitalk.domain.session.service.SessionManager;

public class CurrentSessionContext {
    private final SessionManager sessionManager;
    private String currentSessionId;

    public CurrentSessionContext(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void set(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session은 null일 수 없습니다.");
        }
        this.currentSessionId = session.getSessionId();
    }

    public Session get() {
        return sessionManager.getSession(currentSessionId);
    }

    public Session require() {
        Session session = get();
        if (session == null) {
            throw new RuntimeException("로그인이 필요합니다.");
        }
        return session;
    }

    public boolean isLoggedIn() {
        return get() != null;
    }

    public void logout() {
        if (currentSessionId != null) {
            sessionManager.invalidate(currentSessionId);
            currentSessionId = null;
        }
    }
}