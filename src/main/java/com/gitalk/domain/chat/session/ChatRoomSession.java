package com.gitalk.domain.chat.session;

import com.gitalk.common.api.ImageAsciiHttpServer;
import com.gitalk.common.util.AppConfig;
import com.gitalk.common.util.Layout;
import com.gitalk.common.util.Screen;
import com.gitalk.common.util.Spinner;
import com.gitalk.domain.chat.search.domain.SearchExecutionContext;
import com.gitalk.domain.chat.search.domain.SearchSession;
import com.gitalk.domain.chat.search.util.SearchCommandParser;
import com.gitalk.domain.chat.search.util.SearchShareCodec;
import com.gitalk.domain.chat.search.service.ChatSearchService;
import com.gitalk.domain.chat.search.domain.SearchCommand;
import com.gitalk.domain.chat.search.service.SearchSessionManager;
import com.gitalk.domain.chat.domain.ChatRoom;
import com.gitalk.domain.chat.domain.Message;
import com.gitalk.domain.chat.service.ChatRoomService;
import com.gitalk.domain.chat.service.MissedMessageService;
import com.gitalk.domain.chat.service.NoticeService;
import com.gitalk.domain.chat.service.Protocol;
import com.gitalk.domain.chat.domain.Notice;
import com.gitalk.domain.chat.view.SearchView;
import com.gitalk.domain.chatbot.model.NewsItem;
import com.gitalk.domain.chatbot.model.TrendingRepo;
import com.gitalk.domain.chatbot.model.WebhookEvent;
import com.gitalk.common.api.GithubWebhookServer;
import com.gitalk.domain.chatbot.service.NewsService;
import com.gitalk.domain.chatbot.service.TrendingService;
import com.gitalk.domain.chat.service.RepoService;
import com.gitalk.domain.user.service.UserService;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.GraphicsEnvironment;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 채팅방 클라이언트 세션 (JLine 기반).
 *
 * 레이아웃: JLine LineReader 가 입력창을 화면 하단에 유지하고,
 * 메시지/봇 출력은 lineReader.printAbove() 로 입력창 위에 흘려 준다.
 * 한글 백스페이스, 화살표 편집, 입력 히스토리는 JLine 이 처리한다.
 */
public class ChatRoomSession {

    /** 채팅 서버 host. config 의 chat.server.host (기본 127.0.0.1) */
    private static final String HOST = resolveHost();
    private static final int    PORT = 6000;

    private static String resolveHost() {
        String host = AppConfig.get("chat.server.host");
        return (host == null || host.isBlank()) ? "127.0.0.1" : host.trim();
    }

    // ANSI 색상
    private static final String RESET  = "\033[0m";
    private static final String BOLD   = "\033[1m";
    private static final String DIM    = "\033[2m";
    private static final String CYAN   = "\033[36m";
    private static final String YELLOW = "\033[33m";
    private static final String GREEN  = "\033[32m";

    private static final String PROMPT = " 입력 > ";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final TrendingService  trendingService;
    private final NewsService      newsService;
    private final RepoService      repoService;
    private final GithubWebhookServer webhookServer;  // /repo events 조회용
    private final ChatRoomService  chatRoomService;
    private final UserService      userService;
    private final NoticeService    noticeService;

    private final ChatSearchService chatSearchService;
    private final SearchSessionManager searchSessionManager;
    private final SearchView searchView;
    private final MissedMessageService missedMessageService;
    // 세션 컨텍스트 (enter() 동안만 유효)
    private LineReader lineReader;
    private Terminal   terminal;
    private PrintWriter socketOut;
    private Long currentRoomId;
    private Long currentUserId;
    private String currentNickname;
    private String currentRoomName;
    private String currentRoomType;   // "TEAM" 또는 "OPEN" — 명령 권한 분기용
    private String currentAccessToken;  // GitHub access_token (있을 때만, repo API 호출용)

    // 이미지(ASCII 아트) 수신 저장소 — 세션 동안 유지
    private final Map<String, String> asciiArtStore = new ConcurrentHashMap<>();
    private final AtomicInteger imageCounter = new AtomicInteger(0);

    // ASCII 뷰어 활성 여부 + 뷰어 동안 들어온 메시지 버퍼 (printToScroll 모니터로 보호)
    private volatile boolean viewerActive = false;
    private final List<String> viewerBuffer = new ArrayList<>();

    /**
     * 직전에 화면에 그려진 채팅 메시지의 발신자.
     * 같은 발신자가 연속으로 발화하면 시간/닉네임 헤더를 생략하고 들여쓰기만 한다.
     * 시스템·봇·이미지 메시지는 그룹을 끊어주기 때문에 printToScroll 에서 null로 리셋.
     */
    private String lastDisplayedSender;

    /** " 12:34  " 시간 컬럼 폭 (= 1 leading space + 5 time + 2 separator) */
    private static final int CHAT_TIME_INDENT = 8;

    /**
     * lastDisplayedSender 에 봇 연속 출력을 표시하기 위한 sentinel.
     * 사용자 닉네임에 등장할 수 없는 제어문자를 포함해서 충돌 방지.
     */
    private static final String BOT_SENDER_TAG = "\u0001BOT\u0001";

    /** " 12:34  [봇]  " 봇 라인 prefix 의 표시 폭 = 1 + 5 + 2 + 4 + 2 = 14 */
    private static final int BOT_PREFIX_WIDTH = 14;

    public ChatRoomSession(TrendingService trendingService,
                           NewsService newsService,
                           RepoService repoService,
                           GithubWebhookServer webhookServer,
                           ChatRoomService chatRoomService,
                           UserService userService,
                           NoticeService noticeService,
                           ChatSearchService chatSearchService,
                           SearchSessionManager searchSessionManager,
                           SearchView searchView,
                           MissedMessageService missedMessageService) {
        this.trendingService = trendingService;
        this.newsService     = newsService;
        this.repoService     = repoService;
        this.webhookServer   = webhookServer;
        this.chatRoomService = chatRoomService;
        this.userService     = userService;
        this.noticeService   = noticeService;
        this.chatSearchService   = chatSearchService;
        this.searchSessionManager   = searchSessionManager;
        this.searchView   = searchView;
        this.missedMessageService = missedMessageService;
    }

    // ── 진입점 ───────────────────────────────────────────────────────────────

    public void enter(Long userId, String nickname, String accessToken, Long roomId, String roomName) {
        currentUserId = userId;
        currentRoomId = roomId;
        currentNickname = nickname;
        currentAccessToken = accessToken;
        currentRoomName = roomName;
        currentRoomType = chatRoomService.getRoom(roomId)
                .map(r -> r.getType())
                .orElse("TEAM");
        asciiArtStore.clear();
        imageCounter.set(0);

        Spinner.setSuppressed(true);
        try (Socket socket = new Socket(HOST, PORT);
             Terminal terminal = TerminalBuilder.builder()
                     .system(true)
                     .name("gitalk-chat")
                     .encoding(StandardCharsets.UTF_8)
                     .build()) {

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // 화면 정리는 LineReader 생성 전에 해서 JLine 상태 추적과 충돌 방지
            Screen.clear();

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .option(LineReader.Option.HISTORY_BEEP, false)
                    // Enter 후 입력 줄을 스크롤에 남기지 않고 지워서 채팅방 UI 유지
                    .option(LineReader.Option.ERASE_LINE_ON_FINISH, true)
                    // 이전 출력이 개행으로 끝나지 않았어도 프롬프트를 새 줄에서 시작
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .build();

            this.lineReader = reader;
            this.terminal   = terminal;
            this.socketOut  = out;

            // JOIN
            out.println(Protocol.buildJoinPacket(userId, roomId, nickname));
            String response = in.readLine();
            if (response == null) {
                System.out.println(" 입장 실패: 서버가 응답하지 않습니다 (연결이 끊김).");
                System.out.print(" 계속하려면 엔터를 누르세요...");
                System.out.flush();
                try { new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine(); } catch (IOException ignored) {}
                return;
            }
            if (!isJoinSuccess(response)) {
                System.out.print(" 계속하려면 엔터를 누르세요...");
                System.out.flush();
                try { new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine(); } catch (IOException ignored) {}
                return;
            }

            // 환영 배너 (LineReader 활성 전에 직접 터미널에 출력)
            printRoomBanner(roomId, roomName, terminal);

            // 미독 메시지 알림 (한 줄)
            notifyMissedOnEnter();

            // 수신 스레드
            Thread receiver = new Thread(() -> {
                try {
                    String packet;
                    while ((packet = in.readLine()) != null) {
                        handleIncomingPacket(packet);
                    }
                } catch (IOException ignored) {}
            });
            receiver.setDaemon(true);
            receiver.start();

            // 입력 루프
            while (true) {
                String raw;
                try {
                    raw = reader.readLine(PROMPT);
                } catch (UserInterruptException e) {  // Ctrl+C
                    continue;
                } catch (EndOfFileException e) {       // Ctrl+D
                    break;
                }
                if (raw == null) break;

                String input = sanitize(raw);
                if (input.isEmpty()) continue;
                if ("/quit".equalsIgnoreCase(input)) {
                    out.println(Protocol.QUIT);
                    break;
                }
                if (input.startsWith("/")) {
                    handleCommand(input.substring(1).trim(), nickname);
                } else {
                    out.println(Protocol.buildMsgPacket(input));
                    printChatMessage(nickname, input, true);
                }
            }

        } catch (IOException e) {
            System.out.println("\n 채팅 서버 연결 실패: " + e.getMessage());
            System.out.println(" 서버가 실행 중인지 확인하세요. (bash run.sh server)");
            System.out.print(" 계속하려면 엔터를 누르세요...");
            System.out.flush();
            try { new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine(); } catch (IOException ignored) {}
        } catch (RuntimeException e) {
            System.out.println("\n 입장 중 예외 발생: " + e.getClass().getSimpleName() + " — " + e.getMessage());
            e.printStackTrace(System.out);
            System.out.print(" 계속하려면 엔터를 누르세요...");
            System.out.flush();
            try { new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine(); } catch (IOException ignored) {}
        } finally {
            socketOut = null;
            lineReader = null;
            terminal = null;
            currentRoomId = null;
            currentUserId = null;
            currentNickname = null;
            currentRoomName = null;
            currentRoomType = null;
            currentAccessToken = null;
            Spinner.setSuppressed(false);
        }
    }

    // ── 외부 명령 (방 밖에서 /search 등) ─────────────────────────────────

    public void handleOutsideRoomCommand(Long userId, String nickname, String rawCommand) {
        String command = sanitize(rawCommand);
        if (command.isEmpty()) {
            return;
        }

        if (command.startsWith("/")) {
            command = command.substring(1).trim();
        }

        String[] parts = command.split("\\s+", 2);
        String main = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        if (!"search".equals(main)) {
            System.out.println(" 채팅방 밖에서는 /search 명령만 사용할 수 있습니다.");
            return;
        }

        runOutsideRoomCommandWithViewer(() ->
                executeSearchCommand(arg, nickname, userId, null, false));
    }

    private void runOutsideRoomCommandWithViewer(Runnable action) {
        if (terminal != null && lineReader != null) {
            action.run();
            return;
        }

        Terminal previousTerminal = terminal;
        LineReader previousLineReader = lineReader;

        try (Terminal tempTerminal = TerminalBuilder.builder()
                .system(true)
                .name("gitalk-search")
                .encoding(StandardCharsets.UTF_8)
                .build()) {

            LineReader tempReader = LineReaderBuilder.builder()
                    .terminal(tempTerminal)
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .option(LineReader.Option.HISTORY_BEEP, false)
                    .option(LineReader.Option.ERASE_LINE_ON_FINISH, true)
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .build();

            terminal = tempTerminal;
            lineReader = tempReader;
            action.run();
        } catch (IOException e) {
            System.out.println(" 검색 화면을 여는 중 오류가 발생했습니다: " + e.getMessage());
            action.run();
        } finally {
            lineReader = previousLineReader;
            terminal = previousTerminal;
        }
    }

    // ── 방 타입 헬퍼 (OPEN 채팅 권한 분기) ──────────────────────────────

    /** 현재 입장 중인 방이 OPEN 채팅인지 */
    private boolean isOpenChatRoom() {
        return "OPEN".equals(currentRoomType);
    }

    /** OPEN 방에서 호출 시 안내 메시지 출력 후 true 반환 */
    private boolean blockIfOpenRoom(String commandLabel) {
        if (isOpenChatRoom()) {
            printToScroll(DIM + " 오픈 채팅에서는 " + commandLabel + " 기능을 사용할 수 없습니다." + RESET);
            return true;
        }
        return false;
    }

    // ── 출력 (JLine printAbove 위임) ────────────────────────────────────────

    /** 저수준 출력: viewerBuffer / lineReader / System.out 으로 라우팅만 함. 그룹 추적 X. */
    private synchronized void rawPrint(String text) {
        if (viewerActive) {
            viewerBuffer.add(text);
            return;
        }
        if (lineReader != null) {
            lineReader.printAbove(text);
        } else {
            System.out.println(text);
        }
    }

    /** 시스템·알림 메시지 출력. 같은-사용자/봇 그룹화를 끊는다. */
    private synchronized void printToScroll(String text) {
        lastDisplayedSender = null;  // 시스템 메시지가 그룹 끊음
        rawPrint(text);
    }

    /**
     * 채팅 메시지 출력. 직전 발신자와 같으면 시간/닉네임 헤더를 생략하고 들여쓰기만 한다.
     * isOwn=true 면 [나] 색상으로 표시.
     */
    private synchronized void printChatMessage(String sender, String content, boolean isOwn) {
        boolean continuation = sender != null && sender.equals(lastDisplayedSender);
        String line;
        if (continuation) {
            line = " ".repeat(CHAT_TIME_INDENT) + (content == null ? "" : content);
        } else {
            String time = LocalTime.now().format(TIME_FMT);
            if (isOwn) {
                line = " " + DIM + time + RESET
                        + "  " + CYAN + BOLD + "[나] " + sender + RESET
                        + "  " + (content == null ? "" : content);
            } else {
                line = " " + DIM + time + RESET
                        + "  " + BOLD + "[" + sender + "]" + RESET
                        + "  " + (content == null ? "" : content);
            }
        }
        lastDisplayedSender = sender;
        rawPrint(line);
    }

    /**
     * 봇 출력. 길면 배너 width에 맞춰 wrap.
     * 첫 줄: " 12:34  [봇]  내용..." / 다음 줄(또는 직전이 봇이면 모든 줄): 같은 컬럼 위치에 들여쓰기.
     * 같은 봇 출력이 여러 차례 연속되면(트렌딩 결과 등) 두 번째 broadcast 부터는 헤더 없이 들여쓰기만.
     */
    private synchronized void printBotMessage(String content) {
        if (content == null) content = "";
        boolean continuation = BOT_SENDER_TAG.equals(lastDisplayedSender);

        int width = bannerWidth();
        int contentWidth = Math.max(20, width - BOT_PREFIX_WIDTH);
        java.util.List<String> wrapped = Layout.wrapWords(content, contentWidth);

        for (int i = 0; i < wrapped.size(); i++) {
            String line;
            if (i == 0 && !continuation) {
                String time = LocalTime.now().format(TIME_FMT);
                String prefix = " " + DIM + time + RESET + "  " + YELLOW + BOLD + "[봇]" + RESET + "  ";
                line = prefix + wrapped.get(i);
            } else {
                line = " ".repeat(BOT_PREFIX_WIDTH) + wrapped.get(i);
            }
            rawPrint(line);
        }
        lastDisplayedSender = BOT_SENDER_TAG;
    }

    /** 채팅방 내부 컴포넌트들이 공통으로 쓰는 표시 폭. */
    private int bannerWidth() {
        if (terminal == null) return 70;
        return Math.min(70, Math.max(40, terminal.getWidth() - 2));
    }

    /** 챗봇 결과: 로컬에 wrap+표시 + 다른 클라이언트에 BOT 패킷 릴레이 */
    private void broadcast(String line) {
        printBotMessage(line);
        if (socketOut != null) socketOut.println(Protocol.buildBotPacket(line));
    }

    // ── 명령어 처리 (/로 시작하는 입력) ────────────────────────────────────

    private void handleCommand(String cmd, String nickname) {
        String[] parts = cmd.split("\\s+", 2);
        String main = parts[0].toLowerCase();
        String arg  = parts.length > 1 ? parts[1].trim() : "";

        switch (main) {
            case "trend"   -> cmdTrend(arg);
            case "news"    -> cmdNews();
            case "repo"    -> cmdRepo(arg);
            case "invite"  -> cmdInvite(arg);
            case "notice"  -> cmdNotice(arg);
            case "image"   -> cmdImage(arg);
            case "search"  -> cmdSearch(arg, nickname);
            case "missed"  -> cmdMissed(arg);
            case "help"    -> cmdHelp();
            default        -> printToScroll(DIM + " 알 수 없는 명령어. /help 로 목록 확인" + RESET);
        }
    }

    // ── 채팅방 배너 ────────────────────────────────────────────────────────

    /**
     * 입장 직후 채팅방 헤더 출력.
     * 굵은 ruled 라인으로 감싼 박스 안에 방 이름·타입·인원·만든이를 가운데 정렬,
     * 그 아래 단축 명령 안내 + 얇은 ruled 라인으로 마감.
     */
    private void printRoomBanner(Long roomId, String roomName, Terminal terminal) {
        int width = Math.min(70, Math.max(40, terminal.getWidth() - 2));
        String thick = "━".repeat(width);
        String thin  = "─".repeat(width);

        ChatRoom room = chatRoomService.getRoom(roomId).orElse(null);
        String typeLabel  = "TEAM".equals(currentRoomType) ? "팀 채팅" : "오픈 채팅";
        int memberCount   = chatRoomService.getMemberCount(roomId);
        String creator    = (room != null && room.getCreatorNickname() != null)
                ? room.getCreatorNickname() : "?";
        String description = room != null ? room.getDescription() : null;

        String metaLine = typeLabel + "  ·  " + memberCount + "명  ·  만든이 " + creator;

        PrintWriter w = terminal.writer();
        w.println(thick);
        w.println(BOLD + Layout.center(roomName != null ? roomName : "(이름 없음)", width) + RESET);
        w.println(DIM + Layout.center(metaLine, width) + RESET);
        if (description != null && !description.isBlank()) {
            w.println(DIM + Layout.center(description, width) + RESET);
        }
        w.println(thick);
        w.println(DIM + "  /help  ·  /quit" + RESET);
        w.println(thin);
        w.flush();
    }

    // ── 미독 메시지 ────────────────────────────────────────────────────────

    /** 입장 직후 한 줄 알림. 미독 0건이거나 last_seen NULL 이면 출력 없음. */
    private void notifyMissedOnEnter() {
        if (missedMessageService == null || currentUserId == null || currentRoomId == null) return;
        try {
            java.util.Optional<java.time.LocalDateTime> lastSeen =
                    missedMessageService.getLastSeen(currentUserId, currentRoomId);
            if (lastSeen.isEmpty()) return;
            java.util.List<Message> missed =
                    missedMessageService.findMissedForView(currentUserId, currentRoomId);
            if (missed.isEmpty()) return;

            String time = lastSeen.get().format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"));
            int width = Math.min(70, Math.max(40, terminal.getWidth() - 2));
            String thin = "─".repeat(width);

            PrintWriter w = terminal.writer();
            w.println(YELLOW + BOLD + " 🆕 " + missed.size()
                    + "개의 새 메시지가 있습니다 (마지막 접속: " + time + ")" + RESET);
            w.println(DIM + "    /missed 로 자세히 보기  ·  /missed summary 로 AI 요약" + RESET);
            w.println(thin);
            w.flush();
        } catch (Exception e) {
            // 미독 알림 실패는 입장을 막지 않음 — 무시
        }
    }

    private void cmdMissed(String arg) {
        if (missedMessageService == null) {
            printToScroll(DIM + " 미독 메시지 서비스가 비활성화되어 있습니다." + RESET);
            return;
        }
        String trimmed = arg == null ? "" : arg.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            showMissedList();
            return;
        }
        String[] parts = trimmed.split("\\s+");
        switch (parts[0]) {
            case "summary" -> showMissedSummary();
            case "save"    -> saveMissed(parts.length > 1 ? parts[1] : "txt");
            default        -> printToScroll(DIM + " 사용법: /missed | /missed summary | /missed save [md]" + RESET);
        }
    }

    private void showMissedList() {
        java.util.List<Message> missed =
                missedMessageService.findMissedForView(currentUserId, currentRoomId);
        if (missed.isEmpty()) {
            printToScroll(DIM + " 놓친 메시지가 없습니다." + RESET);
            return;
        }
        showInAltScreen("놓친 메시지 (" + missed.size() + "건)", w -> {
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm");
            for (Message m : missed) {
                String time = m.getCreatedAt() != null ? m.getCreatedAt().format(fmt) : "(시간없음)";
                String nick = m.getSenderNickname() != null ? m.getSenderNickname() : "?";
                w.println(" " + DIM + time + RESET + "  " + BOLD + "[" + nick + "]" + RESET
                        + "  " + (m.getContent() != null ? m.getContent() : ""));
            }
            if (missed.size() >= MissedMessageService.VIEW_LIMIT_COUNT) {
                w.println();
                w.println(DIM + " ⚠ 한도 초과로 최근 "
                        + MissedMessageService.VIEW_LIMIT_COUNT + "건만 표시됨" + RESET);
            }
        });
    }

    private void showMissedSummary() {
        java.util.List<Message> missed =
                missedMessageService.findMissedForView(currentUserId, currentRoomId);
        if (missed.isEmpty()) {
            printToScroll(DIM + " 요약할 메시지가 없습니다." + RESET);
            return;
        }
        printToScroll(DIM + " 🤖 AI 요약 생성 중..." + RESET);
        String summary;
        try {
            summary = missedMessageService.summarizeWithAI(missed, currentRoomName);
        } catch (Exception e) {
            printToScroll(DIM + " 요약 실패: " + e.getMessage() + " — /missed 로 목록을 확인하세요." + RESET);
            return;
        }
        showInAltScreen("AI 요약 (" + missed.size() + "건)", w -> {
            for (String line : summary.split("\n")) {
                w.println(" " + line);
            }
        });
    }

    private void saveMissed(String format) {
        java.util.List<Message> missed =
                missedMessageService.findMissedForExport(currentUserId, currentRoomId);
        if (missed.isEmpty()) {
            printToScroll(DIM + " 저장할 메시지가 없습니다." + RESET);
            return;
        }
        try {
            java.nio.file.Path path = missedMessageService.saveToDownloads(missed, currentRoomName, format);
            printToScroll(GREEN + " ✓ 저장됨: " + path + RESET);
            printToScroll(DIM + "    " + missed.size() + "개 메시지" + RESET);
            if (missed.size() >= MissedMessageService.EXPORT_LIMIT_COUNT) {
                printToScroll(DIM + " ⚠ 한도 초과로 최근 "
                        + MissedMessageService.EXPORT_LIMIT_COUNT + "건만 저장됨" + RESET);
            }
        } catch (IllegalArgumentException e) {
            printToScroll(DIM + " " + e.getMessage() + RESET);
        } catch (Exception e) {
            printToScroll(DIM + " 저장 실패: " + e.getMessage() + RESET);
        }
    }

    /**
     * 공용 alt screen 뷰어. 헤더/divider 그린 후 body 콜백을 실행, q+Enter로 닫음.
     * 뷰어 동안 들어오는 메시지는 viewerBuffer에 보류됐다가 종료 시 드레인.
     */
    private void showInAltScreen(String title, java.util.function.Consumer<PrintWriter> body) {
        if (terminal == null || lineReader == null) {
            printToScroll(BOLD + " ─── " + title + " ───" + RESET);
            return;
        }
        viewerActive = true;
        try {
            terminal.puts(org.jline.utils.InfoCmp.Capability.enter_ca_mode);
            terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
            terminal.flush();

            PrintWriter w = terminal.writer();
            int width = Math.min(70, Math.max(30, terminal.getWidth() - 2));
            String div = "─".repeat(width);
            w.println();
            w.println(BOLD + Layout.center(title, width) + RESET);
            w.println(div);
            w.println();
            body.accept(w);
            w.println();
            w.println(div);
            w.flush();

            try {
                while (true) {
                    String line = lineReader.readLine(DIM + "  q+Enter (또는 Enter): 닫기 > " + RESET);
                    if (line == null) break;
                    String t = sanitize(line);
                    if (t.isEmpty() || "q".equalsIgnoreCase(t)) break;
                }
            } catch (UserInterruptException | EndOfFileException ignored) {}
        } finally {
            terminal.puts(org.jline.utils.InfoCmp.Capability.exit_ca_mode);
            terminal.flush();
            synchronized (this) {
                if (lineReader != null) {
                    for (String msg : viewerBuffer) {
                        lineReader.printAbove(msg);
                    }
                }
                viewerBuffer.clear();
                viewerActive = false;
            }
        }
    }

    // ── 이미지 → ASCII 아트 ────────────────────────────────────────────────

    private void cmdImage(String arg) {
        // /image <id>.view → 저장된 ASCII 아트 보기
        if (arg.endsWith(".view")) {
            String id = arg.substring(0, arg.length() - ".view".length()).trim();
            String art = asciiArtStore.get(id);
            if (art == null) {
                printToScroll(DIM + " 이미지 #" + id + " 을(를) 찾을 수 없습니다." + RESET);
                return;
            }
            showAscii(art, id);
            return;
        }

        // /image → 파일 선택 후 업로드
        File file = chooseImageFile();
        if (file == null) {
            printToScroll(DIM + " 이미지 선택이 취소되었습니다." + RESET);
            return;
        }
        if (!file.exists() || !file.isFile()) {
            printToScroll(DIM + " 파일을 찾을 수 없습니다: " + file.getAbsolutePath() + RESET);
            return;
        }

        int cols = terminal != null ? terminal.getWidth()  : 0;
        int rows = terminal != null ? terminal.getHeight() : 0;
        String filename = stripExtension(file.getName());

        printToScroll(DIM + " 이미지 업로드 중: " + file.getName() + RESET);

        // 별도 스레드 — 응답 대기 동안 채팅 입력/수신이 막히지 않도록
        File toUpload = file;
        Thread t = new Thread(() -> uploadImage(toUpload, filename, cols, rows), "image-upload");
        t.setDaemon(true);
        t.start();
    }

    /** GUI 사용 가능 시 JFileChooser, 헤드리스 환경이면 터미널 경로 직접 입력 */
    private File chooseImageFile() {
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            final File[] result = {null};
            try {
                SwingUtilities.invokeAndWait(() -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("이미지 파일 선택");
                    chooser.setFileFilter(new FileNameExtensionFilter(
                            "이미지 (jpg, png, gif, bmp, webp)",
                            "jpg", "jpeg", "png", "gif", "bmp", "webp"));
                    chooser.setAcceptAllFileFilterUsed(false);
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        result[0] = chooser.getSelectedFile();
                    }
                });
            } catch (Exception e) {
                printToScroll(DIM + " 파일 선택 창 오류: " + e.getMessage() + RESET);
            }
            if (result[0] != null) return result[0];
            // 다이얼로그 실패 시 아래 폴백으로
        }

        // 헤드리스 폴백
        if (lineReader == null) return null;
        try {
            String path = lineReader.readLine(" 이미지 경로 (취소: 빈값) > ");
            if (path == null || path.isBlank()) return null;
            return new File(sanitize(path));
        } catch (UserInterruptException | EndOfFileException e) {
            return null;
        }
    }

    private void uploadImage(File imageFile, String filename, int cols, int rows) {
        try {
            String contentType     = detectContentType(imageFile);
            String encodedSender   = URLEncoder.encode(currentNickname != null ? currentNickname : "익명",
                                                       StandardCharsets.UTF_8);
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            String urlStr = "http://" + HOST + ":" + ImageAsciiHttpServer.HTTP_PORT
                    + "/upload?sender=" + encodedSender
                    + "&filename=" + encodedFilename
                    + "&roomId=" + currentRoomId
                    + (cols > 0 ? "&cols=" + cols : "")
                    + (rows > 0 ? "&rows=" + rows : "");

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(60_000);  // 변환에 시간 걸릴 수 있음

            byte[] bytes = Files.readAllBytes(imageFile.toPath());
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                printToScroll(DIM + " 이미지 변환 실패 (HTTP " + code + ")" + RESET);
            }
        } catch (Exception e) {
            printToScroll(DIM + " 이미지 업로드 오류: " + e.getMessage() + RESET);
        }
    }

    /**
     * 별도의 alternate screen 버퍼에 ASCII 아트를 띄우고, q+Enter(또는 Enter)로 채팅방으로 복귀.
     * 뷰어가 떠 있는 동안 들어오는 채팅 메시지는 viewerBuffer 에 쌓아두었다가 종료 직후 흘려준다.
     */
    private void showAscii(String art, String id) {
        // 터미널이 없으면 alternate screen 못 쓰므로 인라인 폴백
        if (terminal == null || lineReader == null) {
            printToScroll(BOLD + " ─── 이미지 #" + id + " ───" + RESET);
            for (String line : art.split("\n")) {
                printToScroll(line);
            }
            printToScroll(BOLD + " ─────────────────────" + RESET);
            return;
        }

        viewerActive = true;
        try {
            // 1) 별도 화면 버퍼 진입 + 클리어
            terminal.puts(Capability.enter_ca_mode);
            terminal.puts(Capability.clear_screen);
            terminal.flush();

            // 2) 헤더 + ASCII 아트 + 푸터 그리기
            PrintWriter w = terminal.writer();
            int width = Math.min(64, Math.max(20, terminal.getWidth()));
            String div = "─".repeat(width);
            w.println();
            w.println(BOLD + " ─── 이미지 #" + id + " ───" + RESET);
            w.println(div);
            w.print(art);
            if (!art.endsWith("\n")) w.println();
            w.println(div);
            w.flush();

            // 3) 종료 키 입력 대기 (q 또는 Enter, Ctrl+C/Ctrl+D 도 종료)
            try {
                while (true) {
                    String line = lineReader.readLine(DIM + " q+Enter (또는 Enter) → 채팅방으로 돌아가기 > " + RESET);
                    if (line == null) break;
                    String t = sanitize(line);
                    if (t.isEmpty() || "q".equalsIgnoreCase(t)) break;
                }
            } catch (UserInterruptException | EndOfFileException ignored) {
                // Ctrl+C / Ctrl+D 도 정상 종료로 처리
            }

        } finally {
            // 4) alt screen 빠져나옴 → 원래 채팅방 화면 복구
            terminal.puts(Capability.exit_ca_mode);
            terminal.flush();

            // 5) 뷰어 동안 받은 메시지 드레인 + 플래그 해제 (printToScroll 락과 같은 모니터)
            synchronized (this) {
                if (lineReader != null) {
                    for (String msg : viewerBuffer) {
                        lineReader.printAbove(msg);
                    }
                }
                viewerBuffer.clear();
                viewerActive = false;
            }
        }
    }

    private void showSearchResult(String title, String content) {
        String safeTitle = (title == null || title.isBlank()) ? "결과" : title.trim();
        String body = content == null ? "" : content;

        if (terminal == null || lineReader == null) {
            printToScroll(BOLD + " ===== " + safeTitle + " =====" + RESET);
            for (String line : body.split("\n", -1)) {
                printToScroll(line);
            }
            printToScroll(BOLD + " =========================" + RESET);
            return;
        }

        viewerActive = true;
        try {
            terminal.puts(Capability.enter_ca_mode);
            terminal.puts(Capability.clear_screen);
            terminal.flush();

            PrintWriter w = terminal.writer();
            int width = Math.min(80, Math.max(20, terminal.getWidth()));
            String div = "=".repeat(width);
            w.println();
            w.println(BOLD + " " + safeTitle + RESET);
            w.println(div);
            w.print(body);
            if (!body.endsWith("\n")) {
                w.println();
            }
            w.println(div);
            w.flush();

            try {
                while (true) {
                    String line = lineReader.readLine(DIM + " q+Enter (또는 Enter) 로 채팅방으로 돌아가기 > " + RESET);
                    if (line == null) break;
                    String t = sanitize(line);
                    if (t.isEmpty() || "q".equalsIgnoreCase(t)) break;
                }
            } catch (UserInterruptException | EndOfFileException ignored) {
            }
        } finally {
            terminal.puts(Capability.exit_ca_mode);
            terminal.flush();

            synchronized (this) {
                if (lineReader != null) {
                    for (String msg : viewerBuffer) {
                        lineReader.printAbove(msg);
                    }
                }
                viewerBuffer.clear();
                viewerActive = false;
            }
        }
    }

    private static String detectContentType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".gif"))  return "image/gif";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".bmp"))  return "image/bmp";
        return "image/jpeg";
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private void cmdTrend(String filter) {
        String filterArg = filter.isEmpty() ? null : filter;
        List<TrendingRepo> repos;
        try {
            printToScroll(DIM + " GitHub 트렌딩 조회 중..." + RESET);
            repos = trendingService.fetchTrending(filterArg);
            String[] descs = trendingService.translateDescriptions(repos);

            broadcast(" ─── GitHub 트렌딩 [" + TrendingService.filterDesc(filterArg) + "] ───");
            for (int i = 0; i < repos.size(); i++) {
                TrendingRepo r = repos.get(i);
                String lang = r.language() != null ? "  " + r.language() : "";
                broadcast(String.format(" %d. %s  ★%,d%s", i + 1, r.fullName(), r.stars(), lang));
                if (descs[i] != null && !descs[i].isBlank())
                    broadcast("    " + descs[i]);
            }
        } catch (Exception e) {
            printToScroll(" 트렌딩 조회 실패: " + e.getMessage());
            return;
        }

        int choice = readChoice(repos.size());
        if (choice == 0) return;

        try {
            printToScroll(DIM + " AI 분석 중..." + RESET);
            String analysis = trendingService.analyzeRepo(repos.get(choice - 1));
            broadcast(" ─── " + repos.get(choice - 1).fullName() + " 분석 ───");
            for (String line : analysis.split("\n"))
                if (!line.isBlank()) broadcast(" " + line.trim());
        } catch (Exception e) {
            printToScroll(" 분석 실패: " + e.getMessage());
        }
    }

    private void cmdNews() {
        List<NewsItem> items;
        String[] titles;
        try {
            printToScroll(DIM + " Hacker News 조회 중..." + RESET);
            items  = newsService.fetchTopStories();
            titles = newsService.translateTitles(items);

            broadcast(" ─── Hacker News Top 5 ───");
            for (int i = 0; i < items.size(); i++) {
                NewsItem it = items.get(i);
                broadcast(String.format(" %d. %s", i + 1, titles[i]));
                broadcast(String.format("    %s · %s",
                        newsService.relativeTime(it.time()), newsService.domain(it.url())));
            }
        } catch (Exception e) {
            printToScroll(" 뉴스 조회 실패: " + e.getMessage());
            return;
        }

        int choice = readChoice(items.size());
        if (choice == 0) return;

        try {
            printToScroll(DIM + " 기사 요약 중..." + RESET);
            String summary = newsService.summarizeArticle(items.get(choice - 1));
            broadcast(" ─── " + titles[choice - 1] + " ───");
            for (String line : summary.split("\n"))
                if (!line.isBlank()) broadcast(" " + line.trim());
        } catch (Exception e) {
            printToScroll(" 요약 실패: " + e.getMessage());
        }
    }

    // ── /repo (팀 채팅 ↔ GitHub repo 연결) ───────────────────────────────

    private void cmdRepo(String sub) {
        if (blockIfOpenRoom("repo 연결")) return;
        String trimmed = sub == null ? "" : sub.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            showRepoInfo();
            return;
        }
        switch (trimmed) {
            case "connect"    -> connectRepo();
            case "disconnect" -> disconnectRepo();
            case "events"     -> showRepoEvents();
            default -> printToScroll(DIM + " 사용법: /repo | /repo connect | /repo disconnect | /repo events" + RESET);
        }
    }

    private void showRepoInfo() {
        chatRoomService.getRoom(currentRoomId).ifPresentOrElse(room -> {
            if (!room.hasRepo()) {
                printToScroll(DIM + " 연결된 GitHub repo 가 없습니다. /repo connect 로 연결하세요." + RESET);
                return;
            }
            try {
                String token = currentUserAccessToken();
                if (token == null) {
                    printToScroll(DIM + " " + room.getTeamUrl() + RESET);
                    return;
                }
                RepoService.RepoInfo info = repoService.fetchRepoByUrl(room.getTeamUrl(), token);
                printToScroll(BOLD + " [GitHub] 연결된 repo: " + info.fullName() + RESET);
                if (info.description() != null) {
                    printToScroll("    " + info.description());
                }
                printToScroll("    " + (info.language() != null ? info.language() + "  " : "")
                        + "★ " + info.stars() + "  default: " + info.defaultBranch()
                        + (info.isPrivate() ? "  (private)" : ""));
                printToScroll(DIM + "    " + info.htmlUrl() + RESET);
            } catch (Exception e) {
                printToScroll(DIM + " 연결: " + room.getTeamUrl() + RESET);
                printToScroll(DIM + " (GitHub 정보 조회 실패: " + e.getMessage() + ")" + RESET);
            }
        }, () -> printToScroll(DIM + " 방 정보 조회 실패" + RESET));
    }

    private void connectRepo() {
        // 1. 권한: 방장만
        if (!chatRoomService.isCreator(currentRoomId, currentUserId)) {
            printToScroll(DIM + " 방장만 repo 를 연결할 수 있습니다." + RESET);
            return;
        }

        // 2. GitHub 토큰 확인
        String token = currentUserAccessToken();
        if (token == null) {
            printToScroll(DIM + " GitHub 연동이 필요합니다. 메인 메뉴에서 GitHub 로그인을 먼저 해주세요." + RESET);
            return;
        }

        // 3. repo 목록 조회 + 선택 (alt screen 뷰어로 진행)
        java.util.List<RepoService.RepoInfo> repos;
        try {
            printToScroll(DIM + " GitHub 에서 레포 목록을 가져오는 중..." + RESET);
            repos = repoService.listMyRepos(token, 100);
        } catch (Exception e) {
            printToScroll(DIM + " 레포 목록 조회 실패: " + e.getMessage() + RESET);
            return;
        }
        if (repos.isEmpty()) {
            printToScroll(DIM + " 접근 가능한 레포가 없습니다." + RESET);
            return;
        }

        RepoService.RepoInfo picked = pickRepoFromList(repos);
        if (picked == null) {
            printToScroll(DIM + " 연결 취소됨." + RESET);
            return;
        }

        // 4. Webhook 자동 등록 + DB 갱신
        try {
            String payloadUrl = RepoService.resolveWebhookBaseUrl() + "/" + currentRoomId;
            RepoService.WebhookRegistration reg = repoService.registerWebhook(picked, token, payloadUrl);
            chatRoomService.linkRepo(currentRoomId, picked.htmlUrl(), reg.secret(), reg.hookId());
            printToScroll(GREEN + " ✓ " + picked.fullName() + " 연결됨" + RESET);
            printToScroll(DIM + "    Webhook 자동 등록 완료 (id=" + reg.hookId() + ")" + RESET);
            printToScroll(DIM + "    Payload URL: " + payloadUrl + RESET);
        } catch (Exception e) {
            printToScroll(DIM + " Webhook 등록 실패: " + e.getMessage() + RESET);
            printToScroll(DIM + " (admin:repo_hook scope + repo admin 권한이 필요합니다)" + RESET);
        }
    }

    private RepoService.RepoInfo pickRepoFromList(java.util.List<RepoService.RepoInfo> repos) {
        if (terminal == null || lineReader == null) return null;
        viewerActive = true;
        try {
            terminal.puts(org.jline.utils.InfoCmp.Capability.enter_ca_mode);
            terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
            terminal.flush();

            PrintWriter w = terminal.writer();
            int width = bannerWidth();
            String div = "─".repeat(width);
            w.println();
            w.println(BOLD + Layout.center("GitHub 레포 선택", width) + RESET);
            w.println(div);
            w.println();
            for (int i = 0; i < repos.size(); i++) {
                RepoService.RepoInfo r = repos.get(i);
                String idx = Layout.padLeft((i + 1) + ".", 3);
                String lang = r.language() != null ? r.language() : "-";
                String priv = r.isPrivate() ? " (private)" : "";
                w.println(" " + idx + " " + Layout.fit(r.fullName(), 32)
                        + "  ★ " + r.stars() + "  " + lang + priv);
            }
            w.println();
            w.println(div);
            w.println(DIM + "  번호 선택  ·  0/q: 취소" + RESET);
            w.flush();

            try {
                while (true) {
                    String input = lineReader.readLine(DIM + "  선택 > " + RESET);
                    if (input == null) return null;
                    String t = sanitize(input);
                    if (t.isEmpty() || "0".equals(t) || "q".equalsIgnoreCase(t)) return null;
                    try {
                        int n = Integer.parseInt(t);
                        if (n >= 1 && n <= repos.size()) return repos.get(n - 1);
                    } catch (NumberFormatException ignored) {}
                }
            } catch (UserInterruptException | EndOfFileException e) {
                return null;
            }
        } finally {
            terminal.puts(org.jline.utils.InfoCmp.Capability.exit_ca_mode);
            terminal.flush();
            synchronized (this) {
                if (lineReader != null) {
                    for (String msg : viewerBuffer) lineReader.printAbove(msg);
                }
                viewerBuffer.clear();
                viewerActive = false;
            }
        }
    }

    private void disconnectRepo() {
        if (!chatRoomService.isCreator(currentRoomId, currentUserId)) {
            printToScroll(DIM + " 방장만 repo 연결을 해제할 수 있습니다." + RESET);
            return;
        }
        chatRoomService.getRoom(currentRoomId).ifPresentOrElse(room -> {
            if (!room.hasRepo()) {
                printToScroll(DIM + " 연결된 repo 가 없습니다." + RESET);
                return;
            }
            String token = currentUserAccessToken();
            // 1) GitHub 측 webhook 제거 (실패해도 DB 정리는 진행)
            if (token != null && room.getWebhookId() != null) {
                try {
                    String fullName = RepoService.parseRepoFullName(room.getTeamUrl());
                    if (fullName == null) {
                        printToScroll(DIM + " repo URL 파싱 실패: " + room.getTeamUrl() + RESET);
                    } else {
                        repoService.deleteWebhook(fullName, room.getWebhookId(), token);
                    }
                } catch (Exception e) {
                    printToScroll(DIM + " GitHub webhook 삭제 실패: " + e.getMessage() + " (DB 는 정리합니다)" + RESET);
                }
            }
            // 2) DB unlink
            chatRoomService.unlinkRepo(currentRoomId);
            printToScroll(GREEN + " ✓ repo 연결 해제됨" + RESET);
        }, () -> printToScroll(DIM + " 방 정보 조회 실패" + RESET));
    }

    private void showRepoEvents() {
        if (webhookServer == null) {
            printToScroll(DIM + " webhook 서버가 비활성화되어 있습니다." + RESET);
            return;
        }
        java.util.List<com.gitalk.domain.chatbot.model.WebhookEvent> events =
                webhookServer.getRecentEvents(currentRoomId, 30);
        if (events.isEmpty()) {
            printToScroll(DIM + " 이 방에 수신된 GitHub 이벤트가 없습니다." + RESET);
            return;
        }
        showInAltScreen("최근 GitHub 이벤트 (" + events.size() + "건)", w -> {
            java.time.format.DateTimeFormatter fmt =
                    java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm");
            for (com.gitalk.domain.chatbot.model.WebhookEvent e : events) {
                String time = e.receivedAt() != null ? e.receivedAt().format(fmt) : "?";
                w.println(" " + DIM + time + RESET + "  [" + e.type() + "/" + e.action() + "]  "
                        + e.title() + "  by " + e.author());
                if (e.url() != null && !e.url().isBlank()) {
                    w.println("        " + DIM + e.url() + RESET);
                }
            }
        });
    }

    /** 현재 세션 사용자의 GitHub access_token. 로컬 가입자는 null. */
    private String currentUserAccessToken() {
        return currentAccessToken;
    }

    private void cmdInvite(String email) {
        // 오픈 채팅은 자유 입장이라 초대 개념이 없음
        if (blockIfOpenRoom("초대")) return;
        if (email.isBlank()) {
            printToScroll(DIM + " 사용법: /invite <이메일>" + RESET);
            return;
        }
        userService.findByEmail(email).ifPresentOrElse(
            target -> {
                try {
                    chatRoomService.inviteUser(currentRoomId, currentUserId, target.getUserId());
                    String name = target.getNickname() != null ? target.getNickname() : target.getEmail();
                    printToScroll(GREEN + " '" + name + "' 님을 초대했습니다." + RESET);
                } catch (Exception e) {
                    printToScroll(DIM + " 초대 실패: " + e.getMessage() + RESET);
                }
            },
            () -> printToScroll(DIM + " '" + email + "' 에 해당하는 사용자가 없습니다." + RESET)
        );
    }

    private void cmdNotice(String arg) {
        if (blockIfOpenRoom("공지")) return;
        if (arg.isBlank()) {
            // 공지 목록
            List<Notice> notices = noticeService.getNotices(currentRoomId);
            if (notices.isEmpty()) {
                printToScroll(DIM + " 등록된 공지가 없습니다." + RESET);
                return;
            }
            printToScroll(BOLD + " ─── 공지 목록 ───" + RESET);
            for (int i = 0; i < notices.size(); i++) {
                Notice n = notices.get(i);
                String time = n.getCreatedAt() != null
                        ? n.getCreatedAt().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")) : "";
                printToScroll(String.format(" %d. %s  %s%s%s  %s",
                        i + 1, n.getTitle(),
                        DIM, n.getAuthorNickname() != null ? n.getAuthorNickname() : "", RESET,
                        DIM + time + RESET));
            }
        } else {
            // 공지 등록
            try {
                Notice notice = noticeService.post(currentUserId, currentRoomId, arg, "");
                broadcast("📢 공지: " + notice.getTitle());
            } catch (Exception e) {
                printToScroll(DIM + " 공지 등록 실패: " + e.getMessage() + RESET);
            }
        }
    }

    private void cmdSearch(String arg, String nickname) {
        executeSearchCommand(arg, nickname, currentUserId, currentRoomId, currentRoomId != null);
    }

    private void executeSearchCommand(String arg,
                                      String nickname,
                                      Long userId,
                                      Long roomId,
                                      boolean joinedCurrentRoom) {
        try {

            SearchCommand command = SearchCommandParser.parse(arg);

            if (command.isHelp()) {
                showSearchResult("Search Help", searchView.helpText());
                return;
            }

            if (command.isShare()) {
                SearchSession session = searchSessionManager.get(userId);
                if (session == null) {
                    printToScroll(DIM + " 최근 검색 내역이 없습니다." + RESET);
                    return;
                }

                if (!joinedCurrentRoom || roomId == null) {
                    printToScroll(DIM + " 채팅방 안에서만 검색 결과를 공유할 수 있습니다." + RESET);
                    return;
                }

                String shareId = searchSessionManager.share(session);

                String shareMessage = "[검색공유] " + nickname
                        + "님이 검색 결과를 공유했습니다. /search --view " + shareId;
                String packetMessage = SearchShareCodec.appendPayload(shareMessage, shareId, session);

                // 서버로 일반 채팅 메시지처럼 전송
                socketOut.println(Protocol.buildMsgPacket(packetMessage));

                // 내 화면에도 바로 보이게 출력
                printChatMessage(nickname, shareMessage, true);
                return;
            }
            if (command.isView()) {
                SearchSession sharedSession = searchSessionManager.getShared(command.getShareId());
                if (sharedSession == null) {
                    printToScroll(DIM + " 공유된 검색 결과를 찾을 수 없습니다: " + command.getShareId() + RESET);
                    return;
                }

                String rendered = searchView.render(sharedSession);
                showSearchResult("Shared Search - " + command.getShareId(), rendered);
                printToScroll(GREEN + " 공유 검색 결과를 화면에 표시했습니다." + RESET);
                return;
            }
            SearchExecutionContext context = new SearchExecutionContext(
                    userId,
                    nickname,
                    roomId,
                    joinedCurrentRoom
            );

            SearchSession session = chatSearchService.search(command, context);
            searchSessionManager.save(session);

            String rendered = searchView.render(session);
            showSearchResult("Search Result - " + session.getKeyword(), rendered);

            printToScroll(GREEN + " 검색 결과를 화면에 표시했습니다." + RESET);

        } catch (Exception e) {
            printToScroll(DIM + " 검색 처리 중 오류: " + e.getMessage() + RESET);
        }
    }

    // ── 도움말 (카테고리 박스 → 번호 → 상세) ──────────────────────────────

    /** 도움말 카테고리 정의 (icon · name · teamOnly · {커맨드, 설명} 쌍 배열) */
    private record HelpCategory(String icon, String name, boolean teamOnly, String[][] commands) {}

    private static final List<HelpCategory> HELP_CATEGORIES = List.of(
            new HelpCategory("💬", "채팅", false, new String[][]{
                    {"/help", "이 도움말 표시"},
                    {"/quit", "채팅방 퇴장"},
            }),
            new HelpCategory("🤖", "챗봇", false, new String[][]{
                    {"/trend [언어]", "GitHub 트렌딩 조회 (예: /trend python)"},
                    {"/news", "Hacker News Top 5"},
            }),
            new HelpCategory("🖼", "이미지", false, new String[][]{
                    {"/image", "이미지 업로드 → ASCII 아트 변환 후 방에 공유"},
                    {"/image <N>.view", "수신한 N번 이미지 ASCII 아트 보기"},
            }),
            new HelpCategory("🔍", "검색", false, new String[][]{
                    {"/search <키워드>", "현재 방 메시지 검색"},
                    {"/search -r <방번호> <키워드>", "특정 방 메시지 검색"},
                    {"/search -s", "최근 검색 결과 공유"},
                    {"/search -v <shareId>", "공유된 검색 결과 보기"},
            }),
            new HelpCategory("📥", "미독 메시지", false, new String[][]{
                    {"/missed", "놓친 메시지를 별도 화면에서 보기"},
                    {"/missed summary", "놓친 메시지를 AI로 요약 (OpenAI)"},
                    {"/missed save", "놓친 메시지를 ~/Downloads 에 txt 로 저장"},
                    {"/missed save md", "놓친 메시지를 ~/Downloads 에 markdown 으로 저장"},
            }),
            new HelpCategory("👥", "멤버 초대", true, new String[][]{
                    {"/invite <이메일>", "멤버 초대"},
            }),
            new HelpCategory("📢", "공지", true, new String[][]{
                    {"/notice", "공지 목록 보기"},
                    {"/notice <제목>", "공지 등록"},
            }),
            new HelpCategory("🔗", "GitHub", true, new String[][]{
                    {"/repo", "현재 연결된 GitHub repo 정보 보기"},
                    {"/repo connect", "방장만. GitHub repo 선택해서 연결 + 자동 webhook 등록"},
                    {"/repo disconnect", "방장만. repo 연결 해제 + webhook 제거"},
                    {"/repo events", "이 방에 도착한 최근 GitHub 이벤트 보기"},
            })
    );

    private void cmdHelp() {
        // 헤드리스 환경(터미널/라인리더 미보유)이면 인라인 폴백
        if (terminal == null || lineReader == null) {
            cmdHelpInline();
            return;
        }

        // OPEN 방에서는 팀 전용 카테고리 제외
        List<HelpCategory> categories = new java.util.ArrayList<>();
        for (HelpCategory c : HELP_CATEGORIES) {
            if (!c.teamOnly() || !isOpenChatRoom()) {
                categories.add(c);
            }
        }

        viewerActive = true;
        try {
            terminal.puts(Capability.enter_ca_mode);
            Integer selected = null;  // null = 카테고리 목록 화면, 1..N = 상세 화면
            while (true) {
                terminal.puts(Capability.clear_screen);
                terminal.flush();

                if (selected == null) {
                    drawHelpCategoryList(categories);
                } else {
                    drawHelpCategoryDetail(categories.get(selected - 1));
                }

                String prompt = (selected == null)
                        ? DIM + "  번호 선택  ·  Enter/q: 닫기 > " + RESET
                        : DIM + "  Enter: 카테고리 목록  ·  q: 닫기 > " + RESET;

                String input;
                try {
                    input = lineReader.readLine(prompt);
                } catch (UserInterruptException | EndOfFileException e) {
                    break;
                }
                if (input == null) break;

                String t = sanitize(input);

                if ("q".equalsIgnoreCase(t)) {
                    break;
                }
                if (t.isEmpty()) {
                    if (selected == null) break;     // 목록에서 Enter → 닫기
                    selected = null;                  // 상세에서 Enter → 목록으로
                    continue;
                }
                if (selected == null) {
                    try {
                        int n = Integer.parseInt(t);
                        if (n >= 1 && n <= categories.size()) {
                            selected = n;
                        }
                    } catch (NumberFormatException ignored) {}
                }
                // 상세 화면에서의 숫자 입력은 무시
            }
        } finally {
            terminal.puts(Capability.exit_ca_mode);
            terminal.flush();
            synchronized (this) {
                if (lineReader != null) {
                    for (String msg : viewerBuffer) {
                        lineReader.printAbove(msg);
                    }
                }
                viewerBuffer.clear();
                viewerActive = false;
            }
        }
    }

    private void drawHelpCategoryList(List<HelpCategory> categories) {
        PrintWriter w = terminal.writer();
        int width = Math.min(64, Math.max(30, terminal.getWidth() - 2));
        String div = "─".repeat(width);

        w.println();
        w.println(BOLD + Layout.center("Gitalk 명령어 도움말", width) + RESET);
        w.println(div);
        w.println();

        for (int i = 0; i < categories.size(); i++) {
            HelpCategory c = categories.get(i);
            String idx = Layout.padLeft((i + 1) + ".", 3);
            String suffix = c.teamOnly() ? DIM + "  (팀 전용)" + RESET : "";
            w.println("  " + idx + "  " + c.icon() + "  " + c.name() + suffix);
        }

        w.println();
        w.println(div);
        w.flush();
    }

    private void drawHelpCategoryDetail(HelpCategory c) {
        PrintWriter w = terminal.writer();
        int width = Math.min(64, Math.max(30, terminal.getWidth() - 2));
        String div = "─".repeat(width);

        w.println();
        w.println(BOLD + Layout.center(c.icon() + "  " + c.name(), width) + RESET);
        w.println(div);
        w.println();

        // 커맨드 컬럼 폭 계산
        int maxCmdWidth = 0;
        for (String[] cmd : c.commands()) {
            maxCmdWidth = Math.max(maxCmdWidth, Layout.displayWidth(cmd[0]));
        }
        int cmdColumn = maxCmdWidth + 2;

        for (String[] cmd : c.commands()) {
            String cmdCell = Layout.fit(cmd[0], cmdColumn);
            w.println("  " + CYAN + BOLD + cmdCell + RESET + "  " + cmd[1]);
        }

        w.println();
        w.println(div);
        w.flush();
    }

    /** 헤드리스(터미널/라인리더 미보유) 환경용 단순 인라인 도움말 */
    private void cmdHelpInline() {
        boolean open = isOpenChatRoom();
        printToScroll(BOLD + " ─── 명령어 " + (open ? "(오픈 채팅)" : "(팀 채팅)") + " ───" + RESET);
        for (HelpCategory c : HELP_CATEGORIES) {
            if (c.teamOnly() && open) continue;
            printToScroll(BOLD + " " + c.icon() + " " + c.name() + RESET);
            for (String[] cmd : c.commands()) {
                printToScroll("   " + CYAN + cmd[0] + RESET + "  " + cmd[1]);
            }
        }
    }

    // ── 수신 패킷 디스패처 ──────────────────────────────────────────────────

    /**
     * 서버에서 받은 한 라인을 타입별로 분기해서 적절한 print 메서드로 보낸다.
     * MSG 만 같은-사용자 그룹화(printChatMessage)를 거치고, 나머지는 그룹을 끊는다.
     */
    private void handleIncomingPacket(String packet) {
        String[] parts = Protocol.parse(packet);
        if (parts.length == 0 || parts[0] == null) return;
        String type = parts[0];

        if (Protocol.MSG.equals(type)) {
            if (parts.length < 3) return;
            SearchShareCodec.ParsedSharedMessage parsed = SearchShareCodec.parseMessage(parts[2]);
            if (parsed.hasSharedSession()) {
                searchSessionManager.saveShared(parsed.getShareId(), parsed.getSession());
            }
            printChatMessage(parts[1], parsed.getVisibleMessage(), false);
            return;
        }

        if (Protocol.BOT.equals(type)) {
            if (parts.length >= 2) {
                printBotMessage(parts[1]);
            }
            return;
        }

        if (Protocol.SERVER.equals(type)) {
            if (parts.length >= 2) {
                printToScroll(DIM + " ─── " + parts[1] + " ───" + RESET);
            }
            return;
        }

        if (Protocol.ASCII_ART.equals(type)) {
            if (parts.length >= 4) {
                String sender   = parts[1];
                String filename = parts[2];
                String art      = Protocol.decodeAsciiArt(parts[3]);
                String idx      = String.valueOf(imageCounter.incrementAndGet());
                asciiArtStore.put(idx, art);
                String time     = LocalTime.now().format(TIME_FMT);
                String notice   = " " + DIM + time + RESET + "  " + YELLOW + BOLD + "[" + sender + "]" + RESET
                        + " 이(가) " + filename + " 이미지를 보냈습니다.  "
                        + DIM + "→ /image " + idx + ".view" + RESET;
                printToScroll(notice);
            }
            return;
        }
        // 알 수 없는 타입은 무시
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────

    /** 번호 선택 입력 읽기. 잘못된 입력이면 0 반환 */
    private int readChoice(int max) {
        if (lineReader == null) return 0;
        try {
            String line = lineReader.readLine(" 번호 선택 (0: 취소) > ");
            if (line == null) return 0;
            int n = Integer.parseInt(sanitize(line));
            return (n < 0 || n > max) ? 0 : n;
        } catch (NumberFormatException | UserInterruptException | EndOfFileException e) {
            return 0;
        }
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[\\p{Cntrl}\\uFEFF\\p{Cf}\\uFFFD]", "")
                    .replaceAll("\\p{Z}", " ")
                    .trim();
    }

    private boolean isJoinSuccess(String response) {
        String[] parts = Protocol.parse(response);
        if (Protocol.JOIN_SUCCESS.equals(parts[0])) return true;
        System.out.println(" 입장 실패: " + (parts.length > 1 ? parts[1] : "알 수 없는 오류"));
        return false;
    }
}
