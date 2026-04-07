package com.gitalk.api;

import com.gitalk.model.NewsItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class HackerNewsClient {

    private static final String BASE_URL = "https://hacker-news.firebaseio.com/v0";
    private final HttpClient httpClient;

    public HackerNewsClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<NewsItem> fetchTopStories(int count) throws Exception {
        String idsJson = get(BASE_URL + "/topstories.json");
        JsonArray ids = JsonParser.parseString(idsJson).getAsJsonArray();
        int limit = Math.min(count, ids.size());

        // 병렬 호출
        List<CompletableFuture<NewsItem>> futures = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            int id = ids.get(i).getAsInt();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try { return fetchItem(id); } catch (Exception e) { return null; }
            }));
        }

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private NewsItem fetchItem(int id) throws Exception {
        String json = get(BASE_URL + "/item/" + id + ".json");
        if (json == null || json.equals("null")) return null;

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        if (!"story".equals(obj.has("type") ? obj.get("type").getAsString() : "")) return null;

        String title = obj.has("title") ? obj.get("title").getAsString() : "(제목 없음)";
        String url   = obj.has("url")   ? obj.get("url").getAsString()   : null;
        int score    = obj.has("score") ? obj.get("score").getAsInt()     : 0;
        String by    = obj.has("by")    ? obj.get("by").getAsString()     : "unknown";
        long time    = obj.has("time")  ? obj.get("time").getAsLong()     : 0L;

        return new NewsItem(id, title, url, score, by, time);
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
