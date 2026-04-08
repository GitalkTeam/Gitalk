package com.gitalk.common.api;

import com.gitalk.domain.chatbot.model.WebhookEvent;
import com.gitalk.common.util.AppConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class GithubWebhookServer {

    private final int port;
    private HttpServer server;
    private final List<WebhookEvent> receivedEvents = Collections.synchronizedList(new ArrayList<>());
    private Consumer<WebhookEvent> eventListener;

    public GithubWebhookServer() {
        this.port = Integer.parseInt(AppConfig.get("webhook.server.port"));
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/webhook", this::handleWebhook);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public void setEventListener(Consumer<WebhookEvent> listener) {
        this.eventListener = listener;
    }

    public List<WebhookEvent> getReceivedEvents() {
        return Collections.unmodifiableList(receivedEvents);
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String eventType = exchange.getRequestHeaders().getFirst("X-GitHub-Event");
        String body = readBody(exchange.getRequestBody());

        try {
            WebhookEvent event = parseEvent(eventType, body);
            if (event != null) {
                receivedEvents.add(event);
                if (eventListener != null) {
                    eventListener.accept(event);
                }
            }
        } catch (Exception e) {
            System.err.println("Webhook 파싱 오류: " + e.getMessage());
        }

        String response = "{\"status\":\"ok\"}";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().close();
    }

    private WebhookEvent parseEvent(String eventType, String body) {
        if (!"issues".equals(eventType) && !"pull_request".equals(eventType)) {
            return null;
        }

        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        String action = json.has("action") ? json.get("action").getAsString() : "unknown";

        // 신규 오픈된 이벤트만 처리
        if (!"opened".equals(action)) return null;

        String repo = json.getAsJsonObject("repository").get("full_name").getAsString();

        JsonObject target = "issues".equals(eventType)
                ? json.getAsJsonObject("issue")
                : json.getAsJsonObject("pull_request");

        String title = target.get("title").getAsString();
        String url = target.get("html_url").getAsString();

        JsonElement userEl = target.get("user");
        String author = (userEl != null && !userEl.isJsonNull())
                ? userEl.getAsJsonObject().get("login").getAsString()
                : "unknown";

        String type = "issues".equals(eventType) ? "issue" : "pull_request";
        return new WebhookEvent(type, action, repo, title, url, author);
    }

    private String readBody(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
