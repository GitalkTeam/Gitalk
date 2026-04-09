package com.gitalk.chat.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 클라이언트-서버 간 텍스트 프로토콜 정의
 *
 * 클라이언트 → 서버:
 *   JOIN:nickname
 *   MSG:내용
 *   QUIT
 *
 * 서버 → 클라이언트:
 *   JOIN_SUCCESS:nickname
 *   JOIN_FAILED:사유
 *   MSG:발신자:내용
 *   SERVER:시스템메시지
 *
 * 나중에 타입을 추가해 확장 가능 (예: IMAGE:base64data, FILE:filename:size)
 * DB 연동 후 LOGIN:username:password 방식으로 복구 예정
 */
public class Protocol {

    public static final String JOIN         = "JOIN";
    public static final String JOIN_SUCCESS = "JOIN_SUCCESS";
    public static final String JOIN_FAILED  = "JOIN_FAILED";
    public static final String MSG          = "MSG";
    public static final String QUIT         = "QUIT";
    public static final String SERVER       = "SERVER";
    public static final String ASCII_ART    = "ASCII_ART";

    static final String SEP = ":";

    // ── 클라이언트 → 서버 빌더 ──────────────────────────────────────────────

    public static String buildJoinPacket(String nickname) {
        return JOIN + SEP + nickname;
    }

    public static String buildMsgPacket(String content) {
        return MSG + SEP + content;
    }

    // ── 서버 → 클라이언트 빌더 ─────────────────────────────────────────────

    public static String buildJoinSuccess(String nickname) {
        return JOIN_SUCCESS + SEP + nickname;
    }

    public static String buildJoinFailed(String reason) {
        return JOIN_FAILED + SEP + reason;
    }

    /** @deprecated DB 연동 후 복구 예정 */
    public static String buildLoginSuccess(String nickname) {
        return JOIN_SUCCESS + SEP + nickname;
    }

    /** @deprecated DB 연동 후 복구 예정 */
    public static String buildLoginFailed(String reason) {
        return JOIN_FAILED + SEP + reason;
    }

    /** 서버가 클라이언트에게 보내는 채팅 메시지 패킷 */
    public static String buildMsgPacket(String sender, String content) {
        return MSG + SEP + sender + SEP + content;
    }

    public static String buildServerPacket(String text) {
        return SERVER + SEP + text;
    }

    /** ASCII 아트 패킷: ASCII_ART:sender:filename:base64(art)
     *  Base64 인코딩으로 ESC 등 모든 제어 문자를 안전하게 전송한다. */
    public static String buildAsciiArtPacket(String sender, String filename, String asciiArt) {
        String encoded = Base64.getEncoder()
                .encodeToString(asciiArt.getBytes(StandardCharsets.UTF_8));
        return ASCII_ART + SEP + sender + SEP + filename + SEP + encoded;
    }

    /** ASCII 아트 수신 측 Base64 디코딩 */
    public static String decodeAsciiArt(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    // ── 파싱 ──────────────────────────────────────────────────────────────

    public static String[] parse(String raw) {
        return raw.split(SEP, 4);  // 최대 4분할 → [타입, 필드1, 필드2, 필드3]
    }

    public static String typeOf(String raw) {
        int idx = raw.indexOf(SEP);
        return idx == -1 ? raw : raw.substring(0, idx);
    }
}
