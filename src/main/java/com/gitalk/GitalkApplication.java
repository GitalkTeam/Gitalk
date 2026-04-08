package com.gitalk;

import com.gitalk.common.util.AppConfig;
import com.gitalk.common.view.MainView;
import com.gitalk.domain.chatbot.model.NewsItem;
import com.gitalk.domain.chatbot.model.TrendingRepo;
import com.gitalk.domain.chatbot.service.NewsService;
import com.gitalk.domain.chatbot.service.TrendingService;
import com.gitalk.domain.chatbot.service.WebhookService;
import com.gitalk.domain.chatbot.view.ChatBotView;
import com.gitalk.domain.session.model.CurrentSessionContext;
import com.gitalk.domain.session.model.Session;
import com.gitalk.domain.session.service.SessionManager;
import com.gitalk.domain.user.repository.UserRepository;
import com.gitalk.domain.user.service.UserService;
import com.gitalk.domain.user.view.JoinAndLoginView;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.util.List;

public class GitalkApplication {

    private static final TrendingService trendingService = new TrendingService();
    private static final NewsService newsService = new NewsService();
    private static final WebhookService webhookService = new WebhookService();
    private static final ChatBotView chatView = new ChatBotView();
    private static final MainView mainView = new MainView();
    private static BufferedReader reader;
    private static CurrentSessionContext currentSessionContext;

    public static void main(String[] args) throws Exception {
        Console console = System.console();
        reader = new BufferedReader(new InputStreamReader(System.in));

        // 1. 배너 + 메인 메뉴
        mainView.printBanner();
        String menuInput = reader.readLine();
        if (menuInput == null) return;
        String menuChoice = menuInput.replaceAll("[\\p{Cntrl}\\uFEFF]", "").trim();

        if ("2".equals(menuChoice)) { mainView.printExit(); return; }
        if (!"1".equals(menuChoice)) return;

        // 2. 로그인 / 회원가입
        UserRepository userRepository = new UserRepository();
        SessionManager sessionManager = new SessionManager(60);
        UserService userService = new UserService(userRepository, sessionManager);
        JoinAndLoginView joinAndLoginView = new JoinAndLoginView(userService);

        currentSessionContext = new CurrentSessionContext(sessionManager);

        joinAndLoginView.start();


        // 전제:
        // JoinAndLoginView 에 로그인 성공한 Session 을 꺼낼 수 있는 getter 가 있어야 함.
        // 예) public Session getCurrentSession()
        Session loginSession = joinAndLoginView.getCurrentSession();

        if (loginSession == null) {
            mainView.printError("로그인 세션을 찾을 수 없습니다.");
            return;
        }

        currentSessionContext.set(loginSession);

        // 3. 챗봇 진입
        mainView.clearScreen();
        if (console == null) mainView.printTerminalWarning();
        chatView.printHelp();

        while (true) {
            try {
                String line;
                if (console != null) {
                    line = console.readLine("> ");
                } else {
                    System.out.print("> ");
                    System.out.flush();
                    line = reader.readLine();
                }
                if (line == null) break;

                String input = line.replaceAll("[\\p{Cntrl}\\uFEFF]", "")
                        .replaceAll("\\p{Z}", " ")
                        .trim();
                if (input.isEmpty()) continue;
                if ("exit".equalsIgnoreCase(input)) { handleLogout(); }

                handleCommand(input);

            } catch (RuntimeException e) {
                if (e.getClass().getSimpleName().equals("UserInterruptException")) {
                    handleLogout();
                }
                mainView.printError(e.getMessage());
            }
        }

        logoutIfNeeded();
    }

    // ── 라우팅 ─────────────────────────────────────────────────────────────

    private static void handleCommand(String input) {
        String[] parts = input.trim().split("[\\s\\p{Z}]+", 2);
        String command = parts[0].toLowerCase();
        switch (command) {
            case "trend"   -> { handleTrend(parts.length > 1 ? parts[1].trim() : null); chatView.printCommandHint(); }
            case "news"    -> { handleNews(); chatView.printCommandHint(); }
            case "webhook" -> { handleWebhook(parts.length > 1 ? parts[1].toLowerCase() : ""); chatView.printCommandHint(); }
            case "help"    -> chatView.printHelp();
            default        -> System.out.println("알 수 없는 명령어입니다. 'help' 를 입력하세요.");
        }
    }

    // ── 기능 1: GitHub Trending ────────────────────────────────────────────

    private static void handleTrend(String filter) {
        try {
            List<TrendingRepo> repos = trendingService.fetchTrending(filter);
            if (repos.isEmpty()) { System.out.println("트렌딩 데이터를 가져오지 못했습니다."); return; }

            String[] descriptions = trendingService.translateDescriptions(repos);
            chatView.printTrendingList(repos, descriptions, TrendingService.filterDesc(filter));

            int choice = readChoice(repos.size());
            if (choice == 0) return;

            String analysis = trendingService.analyzeRepo(repos.get(choice - 1));
            chatView.printTrendingAnalysis(repos.get(choice - 1), analysis);

        } catch (Exception e) {
            chatView.printError("트렌딩 조회 실패: " + e.getMessage());
        }
    }

    // ── 기능 2: HackerNews ─────────────────────────────────────────────────

    private static void handleNews() {
        try {
            List<NewsItem> items = newsService.fetchTopStories();
            if (items.isEmpty()) { System.out.println("뉴스를 가져오지 못했습니다."); return; }

            String[] titles  = newsService.translateTitles(items);
            String[] times   = items.stream().map(i -> newsService.relativeTime(i.time())).toArray(String[]::new);
            String[] domains = items.stream().map(i -> newsService.domain(i.url())).toArray(String[]::new);
            chatView.printNewsList(items, titles, times, domains);

            int choice = readChoice(items.size());
            if (choice == 0) return;

            String summary = newsService.summarizeArticle(items.get(choice - 1));
            chatView.printNewsSummary(items.get(choice - 1), titles[choice - 1], summary);

        } catch (Exception e) {
            chatView.printError("뉴스 조회 실패: " + e.getMessage());
        }
    }

    // ── 기능 3: Webhook ────────────────────────────────────────────────────

    private static void handleWebhook(String sub) {
        switch (sub) {
            case "start" -> {
                if (webhookService.isRunning()) { System.out.println("\n 웹훅 서버가 이미 실행 중입니다.\n"); return; }
                try {
                    webhookService.start(chatView::printWebhookEventAlert);
                    int port = Integer.parseInt(AppConfig.get("webhook.server.port"));
                    chatView.printWebhookStarted(port);
                } catch (Exception e) {
                    chatView.printError("웹훅 서버 시작 실패: " + e.getMessage());
                }
            }
            case "stop"  -> { webhookService.stop(); chatView.printWebhookStopped(); }
            case "list"  -> chatView.printWebhookEvents(webhookService.getEvents());
            default      -> System.out.println("사용법: webhook start | webhook stop | webhook list");
        }
    }

    // ── 로그아웃 ───────────────────────────────────────────────────────────

    private static void handleLogout() {
        logoutIfNeeded();
        mainView.printExit();
        System.exit(0);
    }

    private static void logoutIfNeeded() {
        if (currentSessionContext != null && currentSessionContext.isLoggedIn()) {
            currentSessionContext.logout();
        }
    }

    // ── 사용자 입력 ────────────────────────────────────────────────────────

    private static int readChoice(int max) {
        try {
            System.out.print(" 번호 선택 (0: 취소) > ");
            System.out.flush();
            String line = reader.readLine();
            if (line == null) return 0;
            int choice = Integer.parseInt(line.replaceAll("[\\p{Cntrl}\\uFEFF]", "").trim());
            return (choice < 0 || choice > max) ? 0 : choice;
        } catch (NumberFormatException e) {
            System.out.println("올바른 번호를 입력해주세요.");
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
}