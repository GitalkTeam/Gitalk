package com.gitalk.domain.chatbot.view;

import com.gitalk.common.util.Layout;
import com.gitalk.domain.chatbot.model.NewsItem;
import com.gitalk.domain.chatbot.model.TrendingRepo;
import com.gitalk.domain.chatbot.model.WebhookEvent;

import java.util.List;

public class ChatBotView {

    private static final int WIDTH = 68;
    private static final String DIV = "─".repeat(WIDTH);

    // ── 컬럼 폭 ──────────────────────────────────────────────────────────────
    private static final int W_TREND_NAME    = 40;
    private static final int W_TREND_LANG    = 14;
    private static final int W_TREND_DESC    = 64;  // 들여쓰기 4칸 제외
    private static final int W_NEWS_TITLE    = 60;
    private static final int W_WEBHOOK_TITLE = 60;
    private static final int W_WEBHOOK_REPO  = 30;

    // ── 레이아웃 유틸 (Layout 위임) ────────────────────────────────────────

    private String center(String text) {
        return Layout.center(text, WIDTH);
    }

    /** 단어 단위 줄바꿈 — 들여쓰기 한 칸 + 표시 너비 기준 */
    private String wrapText(String text, int maxDisplayWidth) {
        StringBuilder result = new StringBuilder();
        for (String line : Layout.wrapWords(text, maxDisplayWidth - 1)) {
            if (result.length() > 0) result.append('\n');
            result.append(' ').append(line);
        }
        return result.toString();
    }

    // ── 공통 ───────────────────────────────────────────────────────────────

    public void printCommandHint() {
        System.out.println(center("명령어: trend [필터] | news | webhook start/stop/list | help | exit"));
        System.out.println(DIV + "\n");
    }

    public void printError(String message) {
        System.err.println(message);
    }

    // ── 트렌딩 ─────────────────────────────────────────────────────────────

    public void printTrendingList(List<TrendingRepo> repos, String[] descriptions, String filterDesc) {
        System.out.println("\n" + DIV);
        System.out.println(center("GitHub 트렌딩 [" + Layout.truncate(filterDesc, 30) + "]"));
        System.out.println(DIV);
        for (int i = 0; i < repos.size(); i++) {
            TrendingRepo repo = repos.get(i);
            String lang = Layout.fit(repo.language() != null ? repo.language() : "N/A", W_TREND_LANG);
            String name = Layout.truncate(repo.fullName(), W_TREND_NAME);
            System.out.printf(" %d. %s%n", i + 1, name);
            System.out.printf("    %s · ⭐ %,d%n", lang, repo.stars());
            if (descriptions[i] != null && !descriptions[i].isBlank()) {
                System.out.printf("    %s%n", Layout.truncate(descriptions[i], W_TREND_DESC));
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
            System.out.printf(" %2d. %s%n", i + 1, Layout.truncate(titles[i], W_NEWS_TITLE));
            System.out.printf("     ⭐ %d | %s | %s%n%n",
                    item.score(),
                    Layout.truncate(domains[i], 24),
                    Layout.truncate(times[i], 16));
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
        List<String> instructions = List.of(
                "GitHub 레포 → Settings → Webhooks → Add webhook",
                "",
                "Payload URL",
                "  http://<ngrok-url>:" + port + "/webhook",
                "",
                "Content type:  application/json",
                "Events:        ☑ Issues  ☑ Pull requests"
        );
        for (String line : Layout.centerBlock(instructions, WIDTH)) {
            System.out.println(line);
        }
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
                String repo = Layout.truncate(e.repo(), W_WEBHOOK_REPO);
                System.out.printf(" [%s] %s | %s%n", icon, Layout.truncate(e.action(), 16), repo);
                System.out.printf(" 제목: %s%n", Layout.truncate(e.title(), W_WEBHOOK_TITLE));
                System.out.printf(" 작성자: %s%n", Layout.truncate(e.author(), 24));
                System.out.printf(" %s%n%n", Layout.truncate(e.url(), 64));
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
        List<String> commands = List.of(
                "trend [필터]     GitHub 트렌딩 + AI 분석",
                "  예) trend java / trend topic:ai",
                "news             HackerNews 개발자 뉴스",
                "webhook start    웹훅 서버 시작",
                "webhook stop     웹훅 서버 종료",
                "webhook list     수신된 이벤트 목록",
                "help             도움말",
                "exit             종료"
        );
        for (String line : Layout.centerBlock(commands, WIDTH)) {
            System.out.println(line);
        }
        System.out.println(DIV + "\n");
    }
}
