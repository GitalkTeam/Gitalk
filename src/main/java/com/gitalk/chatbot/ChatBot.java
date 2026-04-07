package com.gitalk.chatbot;

import com.gitalk.api.GithubTrendingClient;
import com.gitalk.api.GithubWebhookServer;
import com.gitalk.api.HackerNewsClient;
import com.gitalk.api.OpenAIClient;
import com.gitalk.model.NewsItem;
import com.gitalk.model.TrendingRepo;
import com.gitalk.model.WebhookEvent;

import com.gitalk.util.Spinner;

import java.io.BufferedReader;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public class ChatBot {

    private static final int WIDTH = 68;
    private static final String DIV = "─".repeat(WIDTH);

    private final GithubTrendingClient trendingClient = new GithubTrendingClient();
    private final HackerNewsClient hackerNewsClient = new HackerNewsClient();
    private final OpenAIClient openAIClient = new OpenAIClient();
    private final GithubWebhookServer webhookServer = new GithubWebhookServer();
    private final BufferedReader reader;

    public ChatBot(BufferedReader reader) {
        this.reader = reader;
    }

    public void handleCommand(String input) {
        String[] parts = input.trim().split("[\\s\\p{Z}]+", 2);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "trend"   -> { handleTrend(parts.length > 1 ? parts[1].trim() : null); printCommandHint(); }
            case "news"    -> { handleNews(); printCommandHint(); }
            case "webhook" -> { handleWebhook(parts.length > 1 ? parts[1].toLowerCase() : ""); printCommandHint(); }
            case "help"    -> printHelp();
            default        -> System.out.println("알 수 없는 명령어입니다. 'help' 를 입력하세요.");
        }
    }

    private void printCommandHint() {
        System.out.println(" 명령어: trend [필터] | news | webhook start/stop/list | help | exit");
        System.out.println(DIV + "\n");
    }

    private String center(String text) {
        int textWidth = displayWidth(text);
        int padding = Math.max(0, (WIDTH - textWidth) / 2);
        return " ".repeat(padding) + text;
    }

    // 한글(2칸) 등 멀티바이트 문자를 고려한 표시 너비 계산
    private int displayWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) {
            w += (c >= '\uAC00' && c <= '\uD7A3') || (c >= '\u1100' && c <= '\uFFA0') ? 2 : 1;
        }
        return w;
    }

    private String wrapText(String text, int maxDisplayWidth) {
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

    // ── 기능 1: GitHub Trending + OpenAI 분석 ──────────────────────────────

    private void handleTrend(String filter) {
        String filterDesc = filter == null ? "전체" :
                filter.startsWith("topic:") ? "topic:" + filter.substring(6) : filter;
        Spinner spinner = new Spinner();
        try {
            spinner.start("GitHub 트렌딩 조회 중 [" + filterDesc + "]");
            List<TrendingRepo> repos = trendingClient.fetchTrending(5, filter);
            spinner.stop();

            if (repos.isEmpty()) {
                System.out.println("트렌딩 데이터를 가져오지 못했습니다.");
                return;
            }

            spinner.start("설명 번역 중");
            String[] descriptions = translateDescriptions(repos);
            spinner.stop();

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

            System.out.print(" 번호 선택 (0: 취소) > ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null) return;
            int choice = Integer.parseInt(line.replaceAll("[\\p{Cntrl}\\uFEFF]", "").trim());
            if (choice == 0 || choice > repos.size()) return;

            TrendingRepo selected = repos.get(choice - 1);
            spinner.start("AI 분석 중");

            String prompt = String.format(
                    "GitHub 레포지토리 '%s'가 왜 인기 있는지 한국어로 3~4문장으로 설명해줘.\n언어: %s\n설명: %s",
                    selected.fullName(), selected.language(), selected.description());
            String analysis = openAIClient.analyze(prompt);
            spinner.stop();

            System.out.println("\n" + DIV);
            System.out.println(center("AI 분석: " + selected.fullName()));
            System.out.println(DIV);
            System.out.println(wrapText(analysis, WIDTH - 1));
            System.out.println();
            System.out.println(" GitHub: " + selected.url());

        } catch (NumberFormatException e) {
            spinner.stop();
            System.out.println("올바른 번호를 입력해주세요.");
        } catch (Exception e) {
            spinner.stop();
            System.err.println("트렌딩 조회 실패: " + e.getMessage());
        }
    }

    private String[] translateDescriptions(List<TrendingRepo> repos) {
        String[] result = new String[repos.size()];
        StringBuilder prompt = new StringBuilder();
        prompt.append("아래 레포지토리 설명들을 한국어로 20자 이내로 요약해줘.\n");
        prompt.append("반드시 번호 형식으로만 답해줘. 다른 말은 하지 마.\n");
        prompt.append("1. 번역\n2. 번역\n...\n\n");
        for (int i = 0; i < repos.size(); i++) {
            String desc = repos.get(i).description();
            prompt.append(i + 1).append(". ").append(desc != null ? desc : "(설명 없음)").append("\n");
        }
        try {
            String raw = openAIClient.analyze(prompt.toString());
            String[] lines = raw.strip().split("\n");
            int idx = 0;
            for (String line : lines) {
                if (idx >= repos.size()) break;
                String trimmed = line.replaceFirst("^\\d+\\.\\s*", "").trim();
                if (!trimmed.isEmpty()) result[idx++] = trimmed;
            }
        } catch (Exception e) {
            for (int i = 0; i < repos.size(); i++) result[i] = repos.get(i).description();
        }
        return result;
    }

    // ── 기능 2: HackerNews 뉴스 ────────────────────────────────────────────

    private void handleNews() {
        Spinner spinner = new Spinner();
        try {
            spinner.start("HackerNews 조회 중");
            List<NewsItem> items = hackerNewsClient.fetchTopStories(5);
            spinner.stop();

            if (items.isEmpty()) {
                System.out.println("뉴스를 가져오지 못했습니다.");
                return;
            }

            spinner.start("제목 번역 중");
            String[] titles = translateTitles(items);
            spinner.stop();

            System.out.println("\n" + DIV);
            System.out.println(center("HackerNews Top 5"));
            System.out.println(DIV);
            for (int i = 0; i < items.size(); i++) {
                NewsItem item = items.get(i);
                System.out.printf(" %2d. %s%n", i + 1, titles[i]);
                System.out.printf("     ⭐ %d | %s | %s%n%n",
                        item.score(), domain(item.url()), relativeTime(item.time()));
            }
            System.out.println(DIV);

            System.out.print(" 번호 선택 (0: 취소) > ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null) return;
            int choice = Integer.parseInt(line.replaceAll("[\\p{Cntrl}\\uFEFF]", "").trim());
            if (choice == 0 || choice > items.size()) return;

            NewsItem selected = items.get(choice - 1);
            spinner.start("AI 설명 중");

            String prompt = String.format(
                    "다음 개발자 뉴스 기사를 한국어로 3~4문장으로 설명해줘. " +
                    "어떤 내용인지, 왜 개발자들에게 중요한지 위주로 설명해줘.\n제목: %s",
                    selected.title());
            String summary = openAIClient.analyze(prompt);
            spinner.stop();

            String link = selected.url() != null
                    ? selected.url()
                    : "https://news.ycombinator.com/item?id=" + selected.id();

            System.out.println("\n" + DIV);
            System.out.println(center(titles[choice - 1]));
            System.out.println(DIV);
            System.out.println(wrapText(summary, WIDTH - 1));
            System.out.println();
            System.out.println(" 원문: " + link);
            System.out.printf(" HN 토론: https://news.ycombinator.com/item?id=%d%n", selected.id());

        } catch (NumberFormatException e) {
            spinner.stop();
            System.out.println("올바른 번호를 입력해주세요.");
        } catch (Exception e) {
            spinner.stop();
            System.err.println("뉴스 조회 실패: " + e.getMessage());
        }
    }

    private String[] translateTitles(List<NewsItem> items) {
        String[] result = items.stream().map(NewsItem::title).toArray(String[]::new);
        StringBuilder prompt = new StringBuilder();
        prompt.append("아래 뉴스 제목들을 자연스러운 한국어로 번역해줘.\n");
        prompt.append("번역이 어색한 고유명사(기술명, 라이브러리명 등)는 영어 그대로 둬.\n");
        prompt.append("반드시 번호 형식으로만 답해줘. 다른 말은 하지 마.\n");
        prompt.append("1. 번역\n2. 번역\n...\n\n");
        for (int i = 0; i < items.size(); i++) {
            prompt.append(i + 1).append(". ").append(items.get(i).title()).append("\n");
        }
        try {
            String raw = openAIClient.analyze(prompt.toString());
            String[] lines = raw.strip().split("\n");
            int idx = 0;
            for (String line : lines) {
                if (idx >= items.size()) break;
                String trimmed = line.replaceFirst("^\\d+\\.\\s*", "").trim();
                if (!trimmed.isEmpty()) result[idx++] = trimmed;
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String relativeTime(long epochSeconds) {
        long diff = Instant.now().getEpochSecond() - epochSeconds;
        if (diff < 60)     return "방금 전";
        if (diff < 3600)   return (diff / 60) + "분 전";
        if (diff < 86400)  return (diff / 3600) + "시간 전";
        return (diff / 86400) + "일 전";
    }

    private String domain(String url) {
        if (url == null) return "news.ycombinator.com";
        try {
            String host = URI.create(url).getHost();
            return host != null ? host.replaceFirst("^www\\.", "") : url;
        } catch (Exception e) {
            return url;
        }
    }

    // ── 기능 3: GitHub Webhook ─────────────────────────────────────────────

    private void handleWebhook(String sub) {
        switch (sub) {
            case "start" -> {
                if (webhookServer.isRunning()) {
                    System.out.println("\n 웹훅 서버가 이미 실행 중입니다.\n");
                    return;
                }
                try {
                    webhookServer.setEventListener(this::printWebhookEvent);
                    webhookServer.start();
                    int port = Integer.parseInt(com.gitalk.util.AppConfig.get("webhook.server.port"));
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
                } catch (Exception e) {
                    System.err.println("웹훅 서버 시작 실패: " + e.getMessage());
                }
            }
            case "stop" -> {
                webhookServer.stop();
                System.out.println("\n" + DIV);
                System.out.println(center("Webhook 서버 종료"));
            }
            case "list" -> {
                List<WebhookEvent> events = webhookServer.getReceivedEvents();
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
            default -> System.out.println("사용법: webhook start | webhook stop | webhook list");
        }
    }

    private void printWebhookEvent(WebhookEvent event) {
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
