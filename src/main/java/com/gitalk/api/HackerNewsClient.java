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

public class HackerNewsClient {

    private static final String BASE_URL = "https://hacker-news.firebaseio.com/v0";
    private final HttpClient httpClient;

    public HackerNewsClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * HackerNews Top Stories 중 상위 count개를 가져옵니다.
     */
    public List<NewsItem> fetchTopStories(int count) throws Exception {
        String idsJson = get(BASE_URL + "/topstories.json");
        JsonArray ids = JsonParser.parseString(idsJson).getAsJsonArray();

        List<NewsItem> items = new ArrayList<>();
        int limit = Math.min(count, ids.size());

        for (int i = 0; i < limit; i++) {
            int id = ids.get(i).getAsInt();
            try {
                NewsItem item = fetchItem(id);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                // 개별 아이템 실패 시 건너뜀
            }
        }
        return items;
    }

    private NewsItem fetchItem(int id) throws Exception {
        String json = get(BASE_URL + "/item/" + id + ".json");
        if (json == null || json.equals("null")) return null;

        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        String type = obj.has("type") ? obj.get("type").getAsString() : "";
        if (!"story".equals(type)) return null;

        String title = obj.has("title") ? obj.get("title").getAsString() : "(제목 없음)";
        String url = obj.has("url") ? obj.get("url").getAsString() : null;
        int score = obj.has("score") ? obj.get("score").getAsInt() : 0;
        String by = obj.has("by") ? obj.get("by").getAsString() : "unknown";
        long time = obj.has("time") ? obj.get("time").getAsLong() : 0L;

        return new NewsItem(id, title, url, score, by, time);
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
