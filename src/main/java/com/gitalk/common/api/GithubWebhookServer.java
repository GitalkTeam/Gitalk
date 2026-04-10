package com.gitalk.common.api;

import com.gitalk.domain.chat.domain.ChatRoom;
import com.gitalk.domain.chat.repository.ChatRoomRepository;
import com.gitalk.domain.chat.service.ChatService;
import com.gitalk.domain.chatbot.model.WebhookEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;

/**
 * GitHub Webhook 수신 서버.
 *
 * - ChatServer 와 함께 boot 되는 상시 가동 컴포넌트
 * - 엔드포인트: POST /webhook/{roomid}
 * - HMAC-SHA256 서명 검증 (X-Hub-Signature-256 헤더, 방 단위 secret)
 * - 받은 이벤트를 ChatService.broadcastSystemMessage 로 방에 전달
 * - 최근 이벤트는 방별 in-memory 큐에 N건 보관 → /repo events 로 조회 가능
 */
public class GithubWebhookServer {

    public static final int HTTP_PORT = 6002;

    /** 방별 최근 이벤트 캐시. /repo events 응답용. */
    private static final int EVENTS_PER_ROOM = 50;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatService chatService;
    private HttpServer server;

    /** roomId → 최근 이벤트 N개 (newest first) */
    private final ConcurrentHashMap<Long, ConcurrentLinkedDeque<WebhookEvent>> recentByRoom =
            new ConcurrentHashMap<>();

    public GithubWebhookServer(ChatRoomRepository chatRoomRepository, ChatService chatService) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatService = chatService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 10);
        server.createContext("/webhook/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("GitHub Webhook 서버 시작 (포트: " + HTTP_PORT + ")");
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    /** /repo events 명령에서 사용. 방에 도착한 최근 이벤트를 newest-first 로 반환. */
    public java.util.List<WebhookEvent> getRecentEvents(Long roomId, int limit) {
        ConcurrentLinkedDeque<WebhookEvent> deque = recentByRoom.get(roomId);
        if (deque == null) return java.util.List.of();
        return deque.stream().limit(limit).toList();
    }

    // ── 요청 핸들러 ────────────────────────────────────────────────────────

    private void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        // 1. URL 에서 roomId 추출
        String path = exchange.getRequestURI().getPath();  // /webhook/42
        Long roomId = parseRoomId(path);
        if (roomId == null) {
            sendResponse(exchange, 404, "Invalid path (expected /webhook/{roomid})");
            return;
        }

        // 2. 방 조회 + secret 확인
        Optional<ChatRoom> roomOpt = chatRoomRepository.findById(roomId);
        if (roomOpt.isEmpty() || roomOpt.get().getWebhookSecret() == null) {
            sendResponse(exchange, 404, "Room not found or webhook not configured");
            return;
        }
        ChatRoom room = roomOpt.get();
        String secret = room.getWebhookSecret();

        // 3. body 읽기
        byte[] bodyBytes;
        try (InputStream body = exchange.getRequestBody()) {
            bodyBytes = body.readAllBytes();
        }

        // 4. HMAC 검증
        String signature = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
        if (signature == null || !verifySignature(secret, bodyBytes, signature)) {
            sendResponse(exchange, 401, "Invalid signature");
            return;
        }

        // 5. 이벤트 파싱 + 방에 broadcast
        String eventType = exchange.getRequestHeaders().getFirst("X-GitHub-Event");
        String bodyStr   = new String(bodyBytes, StandardCharsets.UTF_8);
        try {
            JsonObject json = JsonParser.parseString(bodyStr).getAsJsonObject();

            // ping 이벤트는 등록 직후 GitHub 가 보내는 헬스체크 — 200 만 응답
            if ("ping".equals(eventType)) {
                sendResponse(exchange, 200, "{\"status\":\"pong\"}");
                return;
            }

            String chatLine = formatEvent(eventType, json, room);
            if (chatLine != null) {
                chatService.broadcastSystemMessage(roomId, chatLine);
                cacheEvent(roomId, eventType, json);
            }
        } catch (Exception e) {
            System.err.println("[Webhook] 파싱 오류 roomId=" + roomId + ": " + e.getMessage());
        }

        sendResponse(exchange, 200, "{\"status\":\"ok\"}");
    }

    // ── HMAC 검증 ──────────────────────────────────────────────────────────

    private boolean verifySignature(String secret, byte[] body, String signatureHeader) {
        if (!signatureHeader.startsWith("sha256=")) return false;
        String expected = signatureHeader.substring("sha256=".length());
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            String calculated = HexFormat.of().formatHex(digest);
            return constantTimeEquals(calculated, expected);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    // ── 이벤트 포맷터 (옵션 D — [GitHub] [태그] 본문) ───────────────────────

    /** 알려진 이벤트만 포맷, 그 외는 null 반환 (broadcast 안 함). */
    private String formatEvent(String eventType, JsonObject payload, ChatRoom room) {
        if (eventType == null) return null;
        return switch (eventType) {
            case "issues"               -> formatIssues(payload);
            case "pull_request"         -> formatPullRequest(payload);
            case "issue_comment"        -> formatIssueComment(payload);
            case "pull_request_review"  -> formatPullRequestReview(payload);
            case "push"                 -> formatPush(payload, room);
            default                     -> null;
        };
    }

    private String formatIssues(JsonObject p) {
        String action = optString(p, "action");
        if (!"opened".equals(action) && !"closed".equals(action)) return null;
        JsonObject issue = p.getAsJsonObject("issue");
        int number    = issue.get("number").getAsInt();
        String title  = optString(issue, "title");
        String author = userLogin(issue.get("user"));
        String url    = optString(issue, "html_url");
        String tag    = "opened".equals(action) ? "[이슈]" : "[이슈 닫힘]";
        String verb   = "opened".equals(action) ? "열었어요" : "닫았어요";
        return "[GitHub] " + tag + " " + author + "가 #" + number + " 를 " + verb + "\n"
                + "         제목: " + title + "\n"
                + "         " + url;
    }

    private String formatPullRequest(JsonObject p) {
        String action = optString(p, "action");
        if (!"opened".equals(action) && !"closed".equals(action)) return null;
        JsonObject pr = p.getAsJsonObject("pull_request");
        int number    = pr.get("number").getAsInt();
        String title  = optString(pr, "title");
        String author = userLogin(pr.get("user"));
        String url    = optString(pr, "html_url");

        if ("opened".equals(action)) {
            return "[GitHub] [PR] " + author + "가 #" + number + " 를 열었어요\n"
                    + "         제목: " + title + "\n"
                    + "         " + url;
        }
        // closed: merged 여부 확인
        boolean merged = pr.has("merged") && pr.get("merged").getAsBoolean();
        if (merged) {
            JsonElement mergedBy = pr.get("merged_by");
            String merger = mergedBy != null && !mergedBy.isJsonNull()
                    ? mergedBy.getAsJsonObject().get("login").getAsString() : author;
            return "[GitHub] [병합] PR #" + number + " 가 병합되었어요 (by " + merger + ")\n"
                    + "         제목: " + title;
        }
        return "[GitHub] [PR 닫힘] PR #" + number + " 가 닫혔어요 (병합 없음)\n"
                + "         제목: " + title;
    }

    private String formatIssueComment(JsonObject p) {
        if (!"created".equals(optString(p, "action"))) return null;
        JsonObject issue   = p.getAsJsonObject("issue");
        JsonObject comment = p.getAsJsonObject("comment");
        int number    = issue.get("number").getAsInt();
        String author = userLogin(comment.get("user"));
        String body   = truncate(optString(comment, "body"), 100);
        return "[GitHub] [코멘트] " + author + "가 #" + number + " 에 댓글을 남겼어요\n"
                + "         \"" + body + "\"";
    }

    private String formatPullRequestReview(JsonObject p) {
        if (!"submitted".equals(optString(p, "action"))) return null;
        JsonObject review = p.getAsJsonObject("review");
        JsonObject pr     = p.getAsJsonObject("pull_request");
        int number    = pr.get("number").getAsInt();
        String author = userLogin(review.get("user"));
        String state  = optString(review, "state");
        String label  = switch (state == null ? "" : state.toLowerCase()) {
            case "approved"          -> "APPROVED";
            case "changes_requested" -> "CHANGES_REQUESTED";
            case "commented"         -> "COMMENTED";
            default                  -> state == null ? "?" : state.toUpperCase();
        };
        return "[GitHub] [리뷰] " + author + "가 #" + number + " 를 리뷰했어요 (" + label + ")";
    }

    private String formatPush(JsonObject p, ChatRoom room) {
        String ref = optString(p, "ref");  // refs/heads/main
        if (ref == null) return null;

        String branch = ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
        // default branch만 알림 — main/master 만 통과 (Phase 2 에서 repo.default_branch 와 비교 가능)
        if (!"main".equals(branch) && !"master".equals(branch)) return null;

        JsonObject pusher = p.has("pusher") && !p.get("pusher").isJsonNull()
                ? p.getAsJsonObject("pusher") : null;
        String author = pusher != null && pusher.has("name")
                ? pusher.get("name").getAsString() : "unknown";

        JsonArray commits = p.has("commits") && p.get("commits").isJsonArray()
                ? p.getAsJsonArray("commits") : new JsonArray();
        int total = commits.size();
        if (total == 0) return null;

        StringBuilder sb = new StringBuilder();
        sb.append("[GitHub] [PUSH] ").append(author).append("가 ").append(branch)
          .append(" 에 ").append(total).append("개 커밋 push\n");
        int show = Math.min(5, total);
        for (int i = 0; i < show; i++) {
            JsonObject c = commits.get(i).getAsJsonObject();
            String msg = optString(c, "message");
            if (msg != null) {
                int nl = msg.indexOf('\n');
                if (nl > 0) msg = msg.substring(0, nl);
            }
            sb.append("         - ").append(truncate(msg, 60)).append("\n");
        }
        if (total > show) {
            sb.append("         ... (+").append(total - show).append(" more)");
        } else if (sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    // ── 유틸 ───────────────────────────────────────────────────────────────

    private void cacheEvent(Long roomId, String eventType, JsonObject json) {
        String repo = json.has("repository") && !json.get("repository").isJsonNull()
                ? json.getAsJsonObject("repository").get("full_name").getAsString() : "?";
        String action = optString(json, "action");
        String title  = "?", url = "", author = "?";
        if (json.has("issue")) {
            JsonObject issue = json.getAsJsonObject("issue");
            title  = optString(issue, "title");
            url    = optString(issue, "html_url");
            author = userLogin(issue.get("user"));
        } else if (json.has("pull_request")) {
            JsonObject pr = json.getAsJsonObject("pull_request");
            title  = optString(pr, "title");
            url    = optString(pr, "html_url");
            author = userLogin(pr.get("user"));
        }
        WebhookEvent ev = new WebhookEvent(eventType, action, repo, title, url, author);

        recentByRoom.computeIfAbsent(roomId, k -> new ConcurrentLinkedDeque<>()).addFirst(ev);
        ConcurrentLinkedDeque<WebhookEvent> q = recentByRoom.get(roomId);
        while (q.size() > EVENTS_PER_ROOM) q.removeLast();
    }

    private static Long parseRoomId(String path) {
        // /webhook/{roomid}
        String[] parts = path.split("/");
        if (parts.length < 3) return null;
        try {
            return Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String optString(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return null;
        return json.get(key).getAsString();
    }

    private static String userLogin(JsonElement el) {
        if (el == null || el.isJsonNull()) return "unknown";
        JsonObject u = el.getAsJsonObject();
        return u.has("login") ? u.get("login").getAsString() : "unknown";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
