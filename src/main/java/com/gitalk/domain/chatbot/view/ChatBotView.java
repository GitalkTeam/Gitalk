package com.gitalk.domain.chatbot.view;

import com.gitalk.domain.chatbot.model.NewsItem;
import com.gitalk.domain.chatbot.model.TrendingRepo;
import com.gitalk.domain.chatbot.model.WebhookEvent;

import java.util.List;

public class ChatBotView {

    private static final int WIDTH = 68;
    private static final String DIV = "─".repeat(WIDTH);

    // ── 레이아웃 유틸 ──────────────────────────────────────────────────────

    public String center(String text) {
        int padding = Math.max(0, (WIDTH - displayWidth(text)) / 2);
        return " ".repeat(padding) + text;
    }

    public int displayWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            w += (c >= '\uAC00' && c <= '\uD7A3') || (c >= '\u1100' && c <= '\uFFA0') ? 2 : 1;
        }
        return w;
    }

    public String wrapText(String text, int maxDisplayWidth) {
        StringBuilder result = new StringBuilder();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder(" ");
        int lineWidth = 1;
        for (String word : words) {
            int wordWidth = displayWidth(word);
            if (lineWidth + wordWidth + 1 > maxDisplayWidth && lineWidth > 1) {
                result.append(line).append("\n");
                line = new StringBuilder(" ").append(word);
                lineWidth = 1 + wordWidth;
            } else {
                if (lineWidth > 1) { line.append(" "); lineWidth++; }
                line.append(word);
                lineWidth += wordWidth;
            }
        }
        if (line.length() > 1) result.append(line);
        return result.toString();
    }

    // ── 공통 ───────────────────────────────────────────────────────────────

    public void printCommandHint() {
        System.out.println(" 명령어: trend [필터] | news | webhook start/stop/list | help | exit");
        System.out.println(DIV + "\n");
    }

    public void printError(String message) {
        System.err.println(message);
    }

    // ── 트렌딩 ─────────────────────────────────────────────────────────────

    public void printTrendingList(List<TrendingRepo> repos, String[] descriptions, String filterDesc) {
        System.out.println("\n" + DIV);
        System.out.println(center("GitHub 트렌딩 [" + filterDesc + "]"));
        System.out.println(DIV);
        for (int i = 0; i < repos.size(); i++) {
            TrendingRepo repo = repos.get(i);
            String lang = repo.language() != null ? repo.language() : "N/A";
            System.out.printf(" %d. %s%n", i + 1, repo.fullName());
            System.out.printf("    %s · ⭐ %,d%n", lang, repo.stars());
            if (descriptions[i] != null && !descriptions[i].isBlank()) {
                System.out.printf("    %s%n", descriptions[i]);
            }
            System.out.println();
        }
        System.out.println(DIV);
    }

    public void printTrendingAnalysis(TrendingRepo repo, String analysis) {
        System.out.println("\n" + DIV);
        System.out.println(center("AI 분석: " + repo.fullName()));
        System.out.println(DIV);
        System.out.println(wrapText(analysis, WIDTH - 1));
        System.out.println();
        System.out.println(" GitHub: " + repo.url());
        System.out.println(DIV);
    }

    // ── 뉴스 ───────────────────────────────────────────────────────────────

    /**
     * @param times   각 항목의 상대 시간 문자열 (NewsService.relativeTime 결과)
     * @param domains 각 항목의 도메인 문자열 (NewsService.domain 결과)
     */
    public void printNewsList(List<NewsItem> items, String[] titles, String[] times, String[] domains) {
        System.out.println("\n" + DIV);
        System.out.println(center("HackerNews Top 5"));
        System.out.println(DIV);
        for (int i = 0; i < items.size(); i++) {
            NewsItem item = items.get(i);
            System.out.printf(" %2d. %s%n", i + 1, titles[i]);
            System.out.printf("     ⭐ %d | %s | %s%n%n", item.score(), domains[i], times[i]);
        }
        System.out.println(DIV);
    }

    public void printNewsSummary(NewsItem item, String title, String summary) {
        String link = item.url() != null
                ? item.url()
                : "https://news.ycombinator.com/item?id=" + item.id();

        System.out.println("\n" + DIV);
        System.out.println(center(title));
        System.out.println(DIV);
        System.out.println(wrapText(summary, WIDTH - 1));
        System.out.println();
        System.out.println(" 원문: " + link);
        System.out.printf(" HN 토론: https://news.ycombinator.com/item?id=%d%n", item.id());
        System.out.println(DIV);
    }

    // ── 웹훅 ───────────────────────────────────────────────────────────────

    public void printWebhookStarted(int port) {
        System.out.println("\n" + DIV);
        System.out.println(center("Webhook 서버 시작 (포트: " + port + ")"));
        System.out.println(DIV);
        System.out.println(" GitHub 레포 → Settings → Webhooks → Add webhook");
        System.out.println();
        System.out.println(" Payload URL");
        System.out.println("   http://<ngrok-url>:" + port + "/webhook");
        System.out.println();
        System.out.println(" Content type:  application/json");
        System.out.println(" Events:        ☑ Issues  ☑ Pull requests");
    }

    public void printWebhookStopped() {
        System.out.println("\n" + DIV);
        System.out.println(center("Webhook 서버 종료"));
    }

    public void printWebhookEvents(List<WebhookEvent> events) {
        System.out.println("\n" + DIV);
        System.out.println(center("수신된 이벤트 (" + events.size() + "건)"));
        System.out.println(DIV);
        if (events.isEmpty()) {
            System.out.println(" 수신된 이벤트가 없습니다.");
        } else {
            for (WebhookEvent e : events) {
                String icon = "pull_request".equals(e.type()) ? "PR" : "Issue";
                System.out.printf(" [%s] %s | %s%n", icon, e.action(), e.repo());
                System.out.printf(" 제목: %s%n", e.title());
                System.out.printf(" 작성자: %s%n", e.author());
                System.out.printf(" %s%n%n", e.url());
            }
        }
    }

    public void printWebhookEventAlert(WebhookEvent event) {
        String icon = "pull_request".equals(event.type()) ? "PR" : "Issue";
        System.out.printf("%n[Webhook] [%s] %s | %s — %s%n> ",
                icon, event.action(), event.repo(), event.title());
        System.out.flush();
    }

    // ── 도움말 ─────────────────────────────────────────────────────────────

    public void printHelp() {
        System.out.println("\n" + DIV);
        System.out.println(center("Gitalk 명령어 목록"));
        System.out.println(DIV);
        System.out.println(" trend [필터]     GitHub 트렌딩 + AI 분석");
        System.out.println("   예) trend java / trend topic:ai");
        System.out.println(" news             HackerNews 개발자 뉴스");
        System.out.println(" webhook start    웹훅 서버 시작");
        System.out.println(" webhook stop     웹훅 서버 종료");
        System.out.println(" webhook list     수신된 이벤트 목록");
        System.out.println(" help             도움말");
        System.out.println(" exit             종료");
        System.out.println(DIV + "\n");
    }
}
