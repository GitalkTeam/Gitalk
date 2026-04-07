package com.gitalk.api;

import com.gitalk.model.TrendingRepo;
import com.gitalk.util.AppConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class GithubTrendingClient {

    private static final String SEARCH_API = "https://api.github.com/search/repositories";
    private final HttpClient httpClient;
    private final String token;

    public GithubTrendingClient() {
        this.token = AppConfig.get("github.token");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 최근 7일간 가장 많은 스타를 받은 레포지토리 목록을 가져옵니다.
     * @param filter null이면 전체, "java" 같은 언어명, "topic:ai" 같은 주제 지정 가능
     */
    public List<TrendingRepo> fetchTrending(int count, String filter) throws Exception {
        String since = LocalDate.now().minusDays(7).toString();
        StringBuilder q = new StringBuilder("created:>").append(since);

        if (filter != null && !filter.isBlank()) {
            if (filter.startsWith("topic:")) {
                q.append(" ").append(filter);
            } else {
                q.append(" language:").append(filter);
            }
        }

        String query = URLEncoder.encode(q.toString(), StandardCharsets.UTF_8);
        String url = SEARCH_API + "?q=" + query + "&sort=stars&order=desc&per_page=" + count;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(15))
                .GET();

        if (token != null && !token.isBlank() && !token.startsWith("YOUR_")) {
            builder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("GitHub API 오류: " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray items = json.getAsJsonArray("items");

        List<TrendingRepo> repos = new ArrayList<>();
        for (JsonElement element : items) {
            JsonObject repo = element.getAsJsonObject();
            String fullName = repo.get("full_name").getAsString();
            String description = repo.get("description").isJsonNull() ? null : repo.get("description").getAsString();
            int stars = repo.get("stargazers_count").getAsInt();
            String language = repo.get("language").isJsonNull() ? null : repo.get("language").getAsString();
            String htmlUrl = repo.get("html_url").getAsString();

            repos.add(new TrendingRepo(fullName, description, stars, language, htmlUrl));
        }
        return repos;
    }
}
