package com.gitalk.chatbot;

import com.gitalk.api.GithubTrendingClient;
import com.gitalk.api.GithubWebhookServer;
import com.gitalk.api.HackerNewsClient;
import com.gitalk.api.OpenAIClient;
import com.gitalk.model.NewsItem;
import com.gitalk.model.TrendingRepo;
import com.gitalk.model.WebhookEvent;

import java.util.List;

public class ChatBot {

    private final GithubTrendingClient trendingClient = new GithubTrendingClient();
    private final HackerNewsClient hackerNewsClient = new HackerNewsClient();
    private final OpenAIClient openAIClient = new OpenAIClient();
    private final GithubWebhookServer webhookServer = new GithubWebhookServer();

    public void handleCommand(String input) {
        String[] parts = input.trim().split("\\s+", 2);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "trend" -> handleTrend(parts.length > 1 ? parts[1].trim() : null);
            case "news" -> handleNews();
            case "webhook" -> {
                String sub = parts.length > 1 ? parts[1].toLowerCase() : "";
                handleWebhook(sub);
            }
            case "help" -> printHelp();
            default -> System.out.println("알 수 없는 명령어입니다. 'help' 를 입력하세요.");
        }
    }

    // ── 기능 1: GitHub Trending + OpenAI 분석 ──────────────────────────────

    private void handleTrend(String filter) {
        String filterDesc = filter == null ? "전체" :
                filter.startsWith("topic:") ? "주제: " + filter.substring(6) : "언어: " + filter;
        System.out.println("\n GitHub 트렌딩 레포지토리 분석 중... [" + filterDesc + "]\n");
        try {
            List<TrendingRepo> repos = trendingClient.fetchTrending(5, filter);
            if (repos.isEmpty()) {
                System.out.println("트렌딩 데이터를 가져오지 못했습니다.");
                return;
            }

            StringBuilder prompt = new StringBuilder();
            prompt.append("다음은 최근 7일간 GitHub에서 가장 많은 스타를 받은 레포지토리 목록입니다 [").append(filterDesc).append("].\n");
            prompt.append("각 레포지토리가 왜 인기 있는지 한국어로 간략하게 분석해주세요.\n\n");

            for (int i = 0; i < repos.size(); i++) {
                TrendingRepo repo = repos.get(i);
                prompt.append(i + 1).append(". ").append(repo.fullName()).append("\n");
                prompt.append("   스타: ").append(repo.stars()).append("\n");
                prompt.append("   언어: ").append(repo.language()).append("\n");
                prompt.append("   설명: ").append(repo.description()).append("\n\n");
            }

            System.out.println("=== GitHub 트렌딩 TOP 5 ===\n");
            for (int i = 0; i < repos.size(); i++) {
                System.out.println((i + 1) + ". " + repos.get(i));
                System.out.println();
            }

            System.out.println("OpenAI 분석 중...\n");
            String analysis = openAIClient.analyze(prompt.toString());
            System.out.println("=== AI 분석 결과 ===\n");
            System.out.println(analysis);
            System.out.println();

        } catch (Exception e) {
            System.err.println("트렌딩 조회 실패: " + e.getMessage());
        }
    }

    // ── 기능 2: HackerNews 뉴스 ────────────────────────────────────────────

    private void handleNews() {
        System.out.println("\n HackerNews 개발자 뉴스 가져오는 중...\n");
        try {
            List<NewsItem> items = hackerNewsClient.fetchTopStories(10);
            if (items.isEmpty()) {
                System.out.println("뉴스를 가져오지 못했습니다.");
                return;
            }

            System.out.println("=== HackerNews Top 10 ===\n");
            for (int i = 0; i < items.size(); i++) {
                System.out.println((i + 1) + ". " + items.get(i));
                System.out.println();
            }
        } catch (Exception e) {
            System.err.println("뉴스 조회 실패: " + e.getMessage());
        }
    }

    // ── 기능 3: GitHub Webhook ─────────────────────────────────────────────

    private void handleWebhook(String sub) {
        switch (sub) {
            case "start" -> {
                if (webhookServer.isRunning()) {
                    System.out.println("웹훅 서버가 이미 실행 중입니다.");
                    return;
                }
                try {
                    webhookServer.setEventListener(this::printWebhookEvent);
                    webhookServer.start();
                } catch (Exception e) {
                    System.err.println("웹훅 서버 시작 실패: " + e.getMessage());
                }
            }
            case "stop" -> {
                webhookServer.stop();
            }
            case "list" -> {
                List<WebhookEvent> events = webhookServer.getReceivedEvents();
                if (events.isEmpty()) {
                    System.out.println("수신된 웹훅 이벤트가 없습니다.");
                } else {
                    System.out.println("=== 수신된 이벤트 (" + events.size() + "건) ===\n");
                    events.forEach(e -> {
                        System.out.println(e);
                        System.out.println();
                    });
                }
            }
            default -> System.out.println("사용법: webhook start | webhook stop | webhook list");
        }
    }

    private void printWebhookEvent(WebhookEvent event) {
        System.out.println("\n[Webhook 수신] " + event);
        System.out.print("> ");
    }

    // ── 도움말 ─────────────────────────────────────────────────────────────

    public void printHelp() {
        System.out.println("""

                ╔══════════════════════════════════════════╗
                ║         Gitalk 명령어 목록               ║
                ╠══════════════════════════════════════════╣
                ║  trend [필터]   GitHub 트렌딩 + AI 분석  ║
                ║    예) trend java / trend topic:ai      ║
                ║  news           HackerNews 개발자 뉴스   ║
                ║  webhook start  웹훅 서버 시작            ║
                ║  webhook stop   웹훅 서버 종료            ║
                ║  webhook list   수신된 이벤트 목록        ║
                ║  help           도움말                   ║
                ║  exit           종료                     ║
                ╚══════════════════════════════════════════╝
                """);
    }
}
