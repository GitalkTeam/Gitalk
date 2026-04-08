package com.gitalk.domain.oauth.github.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gitalk.common.util.AppConfig;
import com.gitalk.domain.oauth.github.exception.GithubAuthorizationPendingException;
import com.gitalk.domain.oauth.github.model.GithubDeviceCode;
import com.gitalk.domain.oauth.github.model.GithubUserInfo;

/**
 * GithubOauthClient Description :
 * NOTE :
 *
 * @author jki
 * @since 04-08 (수) 오후 4:58
 */
public class GithubOauthClient {

    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String ACCEPT_JSON = "application/json";
    private static final String ACCEPT_GITHUB_JSON = "application/vnd.github+json";

    private final HttpClient httpClient;

    public GithubOauthClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 1) Device Code 발급
     */
    public GithubDeviceCode requestDeviceCode() {
        String deviceCodeUrl = getRequiredProperty("github.device.code.url");
        String clientId = getRequiredProperty("github.client.id");
        String scope = getPropertyOrDefault("github.oauth.scope", "read:user user:email");

        String requestBody = formData(
                "client_id", clientId,
                "scope", scope
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(deviceCodeUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", ACCEPT_JSON)
                .header("Content-Type", FORM_URLENCODED)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        String responseBody = send(request, "Device Code 발급");

        // GitHub가 200으로 에러를 내려주는 경우도 있으니 body 내부 에러 체크
        validateOAuthErrorResponse(responseBody, "Device Code 발급 실패");

        String deviceCode = extractString(responseBody, "device_code");
        String userCode = extractString(responseBody, "user_code");
        String verificationUri = extractString(responseBody, "verification_uri");
        int expiresIn = extractInt(responseBody, "expires_in", 900);
        int interval = extractInt(responseBody, "interval", 5);

        if (isBlank(deviceCode)) {
            throw new RuntimeException("GitHub 응답에 device_code가 없습니다. response=" + responseBody);
        }
        if (isBlank(userCode)) {
            throw new RuntimeException("GitHub 응답에 user_code가 없습니다. response=" + responseBody);
        }
        if (isBlank(verificationUri)) {
            throw new RuntimeException("GitHub 응답에 verification_uri가 없습니다. response=" + responseBody);
        }

        return new GithubDeviceCode(
                deviceCode,
                userCode,
                verificationUri,
                interval,
                expiresIn
        );
    }

    /**
     * 2) 승인 완료까지 polling 해서 access token 발급
     *
     * Device Flow는 client_id, device_code, grant_type 중심으로 polling 한다.
     */
    public String pollAccessToken(GithubDeviceCode deviceCode) {
        if (deviceCode == null) {
            throw new IllegalArgumentException("deviceCode는 필수입니다.");
        }

        String accessTokenUrl = getRequiredProperty("github.access.token.url");
        String clientId = getRequiredProperty("github.client.id");
        int pollTimeoutSeconds = getIntProperty("github.oauth.poll.timeout.seconds", 300);

        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + pollTimeoutSeconds * 1000L;
        int intervalSeconds = Math.max(deviceCode.getInterval(), 1);

        while (System.currentTimeMillis() < deadline) {
            String requestBody = formData(
                    "client_id", clientId,
                    "device_code", deviceCode.getDeviceCode(),
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code"
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(accessTokenUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", ACCEPT_JSON)
                    .header("Content-Type", FORM_URLENCODED)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            String responseBody = send(request, "Access Token polling");

            String accessToken = extractString(responseBody, "access_token");
            if (!isBlank(accessToken)) {
                return accessToken;
            }

            String error = extractString(responseBody, "error");
            String errorDescription = extractString(responseBody, "error_description");

            if (isBlank(error)) {
                throw new RuntimeException("GitHub access token 응답 형식이 올바르지 않습니다. response=" + responseBody);
            }

            switch (error) {
                case "authorization_pending":
                    sleepSeconds(intervalSeconds);
                    break;

                case "slow_down":
                    intervalSeconds += 5;
                    sleepSeconds(intervalSeconds);
                    break;

                case "expired_token":
                    throw new RuntimeException("GitHub 인증 코드가 만료되었습니다. 다시 로그인해주세요.");

                case "access_denied":
                    throw new RuntimeException("사용자가 GitHub 로그인을 승인하지 않았습니다.");

                case "unsupported_grant_type":
                    throw new RuntimeException("grant_type 설정이 올바르지 않습니다.");

                case "incorrect_client_credentials":
                    throw new RuntimeException("github.client.id 설정이 올바르지 않습니다.");

                case "bad_verification_code":
                    throw new RuntimeException("device_code가 유효하지 않습니다. 다시 로그인해주세요.");

                case "device_flow_disabled":
                    throw new RuntimeException("GitHub OAuth App에서 Device Flow가 비활성화되어 있습니다. GitHub 설정에서 Enable Device Flow를 켜야 합니다.");

                default:
                    throw new RuntimeException("GitHub 토큰 발급 실패: " +
                            (!isBlank(errorDescription) ? errorDescription : error));
            }
        }

        throw new RuntimeException("GitHub 로그인 승인 대기 시간이 초과되었습니다.");
    }

    /**
     * 3) GitHub 사용자 정보 조회
     * email이 /user 응답에 없으면 /user/emails 추가 조회
     */
    public GithubUserInfo fetchGithubUser(String accessToken) {
        if (isBlank(accessToken)) {
            throw new IllegalArgumentException("accessToken은 필수입니다.");
        }

        String userApiUrl = getRequiredProperty("github.api.user.url");
        String userJson = requestGithubApi(userApiUrl, accessToken);

        Long githubId = extractLong(userJson, "id");
        String login = extractString(userJson, "login");
        String email = extractString(userJson, "email");
        String avatarUrl = extractString(userJson, "avatar_url");

        if (githubId == null) {
            throw new RuntimeException("GitHub 사용자 ID를 가져오지 못했습니다. response=" + userJson);
        }

        if (isBlank(email)) {
            String emailsApiUrl = getRequiredProperty("github.api.emails.url");
            String emailsJson = requestGithubApi(emailsApiUrl, accessToken);
            email = extractPrimaryVerifiedEmail(emailsJson);
        }

        if (isBlank(email)) {
            throw new RuntimeException("GitHub 계정에서 사용 가능한 이메일을 찾지 못했습니다. user:email scope와 이메일 공개/검증 상태를 확인하세요.");
        }

        if (isBlank(login)) {
            login = email;
        }

        return new GithubUserInfo(githubId, login, email, avatarUrl);
    }

    private String requestGithubApi(String url, String accessToken) {
        String apiVersion = getPropertyOrDefault("github.api.version", "2022-11-28");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", ACCEPT_GITHUB_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", apiVersion)
                .GET()
                .build();

        return send(request, "GitHub API 호출");
    }

    private String send(HttpRequest request, String actionName) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                return responseBody;
            }

            String error = extractString(responseBody, "error");
            String errorDescription = extractString(responseBody, "error_description");
            String message = extractString(responseBody, "message");

            String detail;
            if (!isBlank(errorDescription)) {
                detail = errorDescription;
            } else if (!isBlank(message)) {
                detail = message;
            } else if (!isBlank(error)) {
                detail = error;
            } else {
                detail = responseBody;
            }

            throw new RuntimeException(actionName + " 실패. status=" + statusCode + ", detail=" + detail);

        } catch (IOException e) {
            throw new RuntimeException(actionName + " 중 네트워크 오류가 발생했습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(actionName + " 중 인터럽트가 발생했습니다.", e);
        }
    }

    private void validateOAuthErrorResponse(String responseBody, String defaultMessage) {
        String error = extractString(responseBody, "error");
        if (isBlank(error)) {
            return;
        }

        String errorDescription = extractString(responseBody, "error_description");

        switch (error) {
            case "device_flow_disabled":
                throw new RuntimeException("GitHub OAuth App에서 Device Flow가 비활성화되어 있습니다. GitHub 설정에서 Enable Device Flow를 켜야 합니다.");

            case "incorrect_client_credentials":
                throw new RuntimeException("github.client.id 설정이 올바르지 않습니다.");

            default:
                throw new RuntimeException(defaultMessage + ": " +
                        (!isBlank(errorDescription) ? errorDescription : error));
        }
    }

    public String requestAccessTokenOnce(GithubDeviceCode deviceCode) {
        if (deviceCode == null) {
            throw new IllegalArgumentException("deviceCode는 필수입니다.");
        }

        String accessTokenUrl = getRequiredProperty("github.access.token.url");
        String clientId = getRequiredProperty("github.client.id");

        String requestBody = formData(
                "client_id", clientId,
                "device_code", deviceCode.getDeviceCode(),
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(accessTokenUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", ACCEPT_JSON)
                .header("Content-Type", FORM_URLENCODED)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        String responseBody = send(request, "Access Token 확인");

        String accessToken = extractString(responseBody, "access_token");
        if (!isBlank(accessToken)) {
            return accessToken;
        }

        String error = extractString(responseBody, "error");
        String errorDescription = extractString(responseBody, "error_description");

        if (isBlank(error)) {
            throw new RuntimeException("GitHub access token 응답 형식이 올바르지 않습니다. response=" + responseBody);
        }

        switch (error) {
            case "authorization_pending":
                throw new GithubAuthorizationPendingException("아직 GitHub 승인이 완료되지 않았습니다.");

            case "slow_down":
                throw new GithubAuthorizationPendingException("아직 GitHub 승인이 완료되지 않았습니다.");

            case "expired_token":
                throw new RuntimeException("GitHub 인증 코드가 만료되었습니다. 다시 로그인해주세요.");

            case "access_denied":
                throw new RuntimeException("GitHub 로그인이 거부되었습니다.");

            case "unsupported_grant_type":
                throw new RuntimeException("grant_type 설정이 올바르지 않습니다.");

            case "incorrect_client_credentials":
                throw new RuntimeException("github.client.id 설정이 올바르지 않습니다.");

            case "bad_verification_code":
                throw new RuntimeException("device_code가 유효하지 않습니다. 다시 로그인해주세요.");

            case "device_flow_disabled":
                throw new RuntimeException("GitHub OAuth App에서 Device Flow가 비활성화되어 있습니다. GitHub 설정에서 Enable Device Flow를 켜야 합니다.");

            default:
                throw new RuntimeException("GitHub 토큰 발급 실패: " +
                        (!isBlank(errorDescription) ? errorDescription : error));
        }
    }

    private int getIntProperty(String key, int defaultValue) {
        String value = getPropertyOrDefault(key, String.valueOf(defaultValue));

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException("설정값이 숫자가 아닙니다. key=" + key + ", value=" + value, e);
        }
    }

    private String getRequiredProperty(String key) {
        String value = AppConfig.get(key);

        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("필수 설정값이 없습니다. key=" + key);
        }

        return value.trim();
    }

    private String getPropertyOrDefault(String key, String defaultValue) {
        String value = AppConfig.get(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private String formData(String... pairs) {
        if (pairs == null || pairs.length == 0 || pairs.length % 2 != 0) {
            throw new IllegalArgumentException("formData는 key/value 쌍으로 전달해야 합니다.");
        }

        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < pairs.length; i += 2) {
            if (builder.length() > 0) {
                builder.append("&");
            }

            builder.append(encode(pairs[i]))
                    .append("=")
                    .append(encode(pairs[i + 1]));
        }

        return builder.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("GitHub 승인 대기 중 인터럽트가 발생했습니다.", e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String extractString(String json, String key) {
        if (json == null || key == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return unescapeJson(matcher.group(1));
        }
        return null;
    }

    private Integer extractInt(String json, String key, int defaultValue) {
        if (json == null || key == null) {
            return defaultValue;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return defaultValue;
    }

    private Long extractLong(String json, String key) {
        if (json == null || key == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }
        return null;
    }

    private String extractBooleanText(String json, String key) {
        if (json == null || key == null) {
            return null;
        }

        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String extractPrimaryVerifiedEmail(String json) {
        if (isBlank(json)) {
            return null;
        }

        Pattern objectPattern = Pattern.compile("\\{(.*?)\\}", Pattern.DOTALL);
        Matcher matcher = objectPattern.matcher(json);

        String verifiedFallbackEmail = null;

        while (matcher.find()) {
            String objectJson = matcher.group();

            String email = extractString(objectJson, "email");
            String primaryText = extractBooleanText(objectJson, "primary");
            String verifiedText = extractBooleanText(objectJson, "verified");

            boolean primary = "true".equals(primaryText);
            boolean verified = "true".equals(verifiedText);

            if (isBlank(email)) {
                continue;
            }

            if (primary && verified) {
                return email;
            }

            if (verified && verifiedFallbackEmail == null) {
                verifiedFallbackEmail = email;
            }
        }

        return verifiedFallbackEmail;
    }

    private String unescapeJson(String value) {
        if (value == null) {
            return null;
        }

        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\/", "/")
                .replace("\\b", "\b")
                .replace("\\f", "\f")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}