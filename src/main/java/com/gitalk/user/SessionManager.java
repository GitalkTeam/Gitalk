package com.gitalk.user;

/**
 * SessionManager Description :
 * NOTE :
 *
 * @author jki
 * @since 04-07 (화) 오후 3:45
 */
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final Map<String, Users> sessionMap = new ConcurrentHashMap<>();

    private SessionManager() {}

    // 로그인 시 세션 생성
    public static String createSession(Users user) {
        String sessionId = UUID.randomUUID().toString();
        sessionMap.put(sessionId, user);
        return sessionId;
    }

    // 사용자 조회
    public static Users getUser(String sessionId) {
        return sessionMap.get(sessionId);
    }

    // 로그아웃
    public static void removeSession(String sessionId) {
        sessionMap.remove(sessionId);
    }

    // 로그인 여부 확인
    public static boolean isLogin(String sessionId) {
        return sessionMap.containsKey(sessionId);
    }
}