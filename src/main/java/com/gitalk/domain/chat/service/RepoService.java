package com.gitalk.domain.chat.service;

import com.gitalk.common.util.AppConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub repo 관련 API 호출 (사용자 access_token 사용).
 *
 * - 사용자의 repo 목록 조회 (방 만들 때 선택지 표시)
 * - repo 검증 (직접 URL 입력 폴백용)
 * - Webhook 자동 등록 / 해제
 * - 방 단위 webhook secret 발급
 *
 * 모든 API 호출은 사용자의 OAuth access_token 으로 본인 권한 안에서 동작.
 */
public class RepoService {

    private static final String API_BASE   = "https://api.github.com";
    private static final String USER_AGENT = "Gitalk-Chat";

    /** GitHub URL 에서 owner/repo 추출. https://github.com/owner/repo[.git][/]? 형태 모두 허용. */
    private static final Pattern REPO_URL_PATTERN =
            Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+?)(?:\\.git)?/?$");

    private final HttpClient httpClient;

    public RepoService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── DTO ────────────────────────────────────────────────────────────────

    /** GitHub repo 메타 정보 (목록·연결 시 사용). */
    public record RepoInfo(
            long id,
            String fullName,         // owner/name
            String name,
            String owner,
            String description,
            String language,
            int stars,
            String defaultBranch,
            boolean isPrivate,
            String htmlUrl
    ) {}

    /** Webhook 등록 결과. */
    public record WebhookRegistration(long hookId, String secret) {}

    // ── repo 목록 / 검증 ───────────────────────────────────────────────────

    /**
     * 사용자가 접근 가능한 repo 목록을 최근 push 순으로 조회.
     * GitHub default per_page=30. limit 은 client 표시 한도.
     */
    public List<RepoInfo> listMyRepos(String accessToken, int limit) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("GitHub access token이 없습니다.");
        }
        int perPage = Math.min(Math.max(limit, 1), 100);
        String url = API_BASE + "/user/repos?per_page=" + perPage
                + "&sort=pushed&direction=desc&affiliation=owner,collaborator,organization_member";

        JsonArray arr = getJsonArray(url, accessToken);
        List<RepoInfo> result = new ArrayList<>();
        for (int i = 0; i < arr.size() && i < limit; i++) {
            result.add(parseRepoInfo(arr.get(i).getAsJsonObject()));
        }
        return result;
    }

    /** repo URL 검증 + 메타 조회. URL 형식 잘못되면 IllegalArgumentException. */
    public RepoInfo fetchRepoByUrl(String repoUrl, String accessToken) {
        Matcher m = REPO_URL_PATTERN.matcher(repoUrl == null ? "" : repoUrl.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "올바른 GitHub repo URL이 아닙니다. (예: https://github.com/owner/repo)");
        }
        String owner = m.group(1);
        String name  = m.group(2);
        String url   = API_BASE + "/repos/" + owner + "/" + name;
        JsonObject json = getJsonObject(url, accessToken);
        return parseRepoInfo(json);
    }

    /** 텍스트 owner/name 에서 RepoInfo 조회 (이미 fullName 알 때). */
    public RepoInfo fetchRepo(String fullName, String accessToken) {
        if (fullName == null || !fullName.contains("/")) {
            throw new IllegalArgumentException("올바른 repo 형식이 아닙니다. (owner/name)");
        }
        return getRepoByApi(API_BASE + "/repos/" + fullName, accessToken);
    }

    private RepoInfo getRepoByApi(String url, String accessToken) {
        return parseRepoInfo(getJsonObject(url, accessToken));
    }

    // ── Webhook 자동 등록 / 해제 ─────────────────────────────────────────────

    /**
     * 지정된 repo 에 우리 서버용 webhook 을 등록한다.
     *
     * @param repo         등록 대상 repo
     * @param accessToken  사용자 OAuth token (admin:repo_hook scope 필요)
     * @param payloadUrl   GitHub 이 POST 보낼 우리 서버 엔드포인트 (예: https://gitalk.example.com/webhook/42)
     * @return 발급된 hook id + secret
     */
    public WebhookRegistration registerWebhook(RepoInfo repo, String accessToken, String payloadUrl) {
        String secret = generateSecret();

        JsonObject config = new JsonObject();
        config.addProperty("url", payloadUrl);
        config.addProperty("content_type", "json");
        config.addProperty("secret", secret);
        config.addProperty("insecure_ssl", "0");

        JsonArray events = new JsonArray();
        events.add("issues");
        events.add("pull_request");
        events.add("push");
        events.add("issue_comment");
        events.add("pull_request_review");

        JsonObject body = new JsonObject();
        body.addProperty("name", "web");
        body.addProperty("active", true);
        body.add("events", events);
        body.add("config", config);

        String url = API_BASE + "/repos/" + repo.fullName() + "/hooks";
        JsonObject response = postJson(url, accessToken, body.toString());
        long hookId = response.get("id").getAsLong();
        return new WebhookRegistration(hookId, secret);
    }

    /** 등록된 webhook 삭제. 실패해도 호출자가 결정하도록 RuntimeException 그대로 던짐. */
    public void deleteWebhook(String repoFullName, long hookId, String accessToken) {
        String url = API_BASE + "/repos/" + repoFullName + "/hooks/" + hookId;
        HttpRequest request = baseBuilder(url, accessToken)
                .DELETE()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code != 204 && code != 404) {  // 404 = 이미 삭제됨, OK 처리
                throw new RuntimeException("Webhook 삭제 실패 (HTTP " + code + "): " + response.body());
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Webhook 삭제 호출 실패: " + e.getMessage(), e);
        }
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────────────

    /** 64자 hex (256bit) random secret 생성. HMAC-SHA256 검증용으로 충분. */
    public static String generateSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private HttpRequest.Builder baseBuilder(String url, String accessToken) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(15));
    }

    private JsonObject getJsonObject(String url, String accessToken) {
        try {
            HttpResponse<String> response = httpClient.send(
                    baseBuilder(url, accessToken).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("GitHub API 오류 (" + response.statusCode() + "): " + response.body());
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("GitHub API 호출 실패: " + e.getMessage(), e);
        }
    }

    private JsonArray getJsonArray(String url, String accessToken) {
        try {
            HttpResponse<String> response = httpClient.send(
                    baseBuilder(url, accessToken).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("GitHub API 오류 (" + response.statusCode() + "): " + response.body());
            }
            return JsonParser.parseString(response.body()).getAsJsonArray();
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("GitHub API 호출 실패: " + e.getMessage(), e);
        }
    }

    private JsonObject postJson(String url, String accessToken, String jsonBody) {
        try {
            HttpResponse<String> response = httpClient.send(
                    baseBuilder(url, accessToken)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code != 200 && code != 201) {
                throw new RuntimeException("GitHub API 오류 (" + code + "): " + response.body());
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("GitHub API 호출 실패: " + e.getMessage(), e);
        }
    }

    private RepoInfo parseRepoInfo(JsonObject json) {
        long id            = json.get("id").getAsLong();
        String fullName    = json.get("full_name").getAsString();
        String name        = json.get("name").getAsString();
        String owner       = json.getAsJsonObject("owner").get("login").getAsString();
        String description = json.has("description") && !json.get("description").isJsonNull()
                ? json.get("description").getAsString() : null;
        String language    = json.has("language") && !json.get("language").isJsonNull()
                ? json.get("language").getAsString() : null;
        int stars          = json.has("stargazers_count") ? json.get("stargazers_count").getAsInt() : 0;
        String defaultBr   = json.has("default_branch") ? json.get("default_branch").getAsString() : "main";
        boolean isPrivate  = json.has("private") && json.get("private").getAsBoolean();
        String htmlUrl     = json.get("html_url").getAsString();
        return new RepoInfo(id, fullName, name, owner, description, language, stars, defaultBr, isPrivate, htmlUrl);
    }

    /**
     * 우리 webhook 수신 엔드포인트의 full URL prefix.
     * config 키 public.webhook.url 에 사용자가 직접 설정.
     *
     *   직접 포트 접근:    http://1.2.3.4:6002/webhook
     *   reverse proxy:    https://gitalk.example.com/webhook
     *   custom path:      https://gitalk.example.com/api/gitalk/webhook
     *
     * 미설정 시 로컬 폴백 (GitHub 실제 접근 불가).
     */
    public static String resolveWebhookBaseUrl() {
        String url = AppConfig.get("public.webhook.url");
        if (url == null || url.isBlank()) {
            url = "http://localhost:6002/webhook";  // 로컬 폴백
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * GitHub URL 또는 owner/name 문자열에서 fullName(owner/name) 추출.
     * https / http / .git suffix / trailing slash 모두 허용.
     * 매칭 안 되면 null.
     */
    public static String parseRepoFullName(String urlOrFullName) {
        if (urlOrFullName == null || urlOrFullName.isBlank()) return null;
        String s = urlOrFullName.trim();
        // 이미 owner/name 형태?
        if (!s.contains("://") && s.matches("^[^/]+/[^/]+$")) return s;
        Matcher m = REPO_URL_PATTERN.matcher(s);
        if (m.matches()) return m.group(1) + "/" + m.group(2);
        return null;
    }
}
