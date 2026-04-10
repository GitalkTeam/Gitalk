package com.gitalk.domain.chat.session;

import com.gitalk.common.api.ImageAsciiHttpServer;
import com.gitalk.common.util.Screen;
import com.gitalk.common.util.Spinner;
import com.gitalk.domain.chat.search.domain.SearchExecutionContext;
import com.gitalk.domain.chat.search.domain.SearchSession;
import com.gitalk.domain.chat.search.util.SearchCommandParser;
import com.gitalk.domain.chat.search.util.SearchShareCodec;
import com.gitalk.domain.chat.search.service.ChatSearchService;
import com.gitalk.domain.chat.search.domain.SearchCommand;
import com.gitalk.domain.chat.search.service.SearchSessionManager;
import com.gitalk.domain.chat.service.ChatRoomService;
import com.gitalk.domain.chat.service.NoticeService;
import com.gitalk.domain.chat.service.Protocol;
import com.gitalk.domain.chat.domain.Notice;
import com.gitalk.domain.chat.view.SearchView;
import com.gitalk.domain.chatbot.model.NewsItem;
import com.gitalk.domain.chatbot.model.TrendingRepo;
import com.gitalk.domain.chatbot.model.WebhookEvent;
import com.gitalk.domain.chatbot.service.NewsService;
import com.gitalk.domain.chatbot.service.TrendingService;
import com.gitalk.domain.chatbot.service.WebhookService;
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

    private static final String HOST = "127.0.0.1";
    private static final int    PORT = 6000;

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
    private final WebhookService   webhookService;
    private final ChatRoomService  chatRoomService;
    private final UserService      userService;
    private final NoticeService    noticeService;

    private final ChatSearchService chatSearchService;
    private final SearchSessionManager searchSessionManager;
    private final SearchView searchView;
    // 세션 컨텍스트 (enter() 동안만 유효)
    private LineReader lineReader;
    private Terminal   terminal;
    private PrintWriter socketOut;
    private Long currentRoomId;
    private Long currentUserId;
    private String currentNickname;

    // 이미지(ASCII 아트) 수신 저장소 — 세션 동안 유지
    private final Map<String, String> asciiArtStore = new ConcurrentHashMap<>();
    private final AtomicInteger imageCounter = new AtomicInteger(0);

    // ASCII 뷰어 활성 여부 + 뷰어 동안 들어온 메시지 버퍼 (printToScroll 모니터로 보호)
    private volatile boolean viewerActive = false;
    private final List<String> viewerBuffer = new ArrayList<>();

    public ChatRoomSession(TrendingService trendingService,
                           NewsService newsService,
                           WebhookService webhookService,
                           ChatRoomService chatRoomService,
                           UserService userService,
                           NoticeService noticeService,
                           ChatSearchService chatSearchService,
                           SearchSessionManager searchSessionManager,
                           SearchView searchView) {
        this.trendingService = trendingService;
        this.newsService     = newsService;
        this.webhookService  = webhookService;
        this.chatRoomService = chatRoomService;
        this.userService     = userService;
        this.noticeService   = noticeService;
        this.chatSearchService   = chatSearchService;
        this.searchSessionManager   = searchSessionManager;
        this.searchView   = searchView;
    }

    // ── 진입점 ───────────────────────────────────────────────────────────────

    public void enter(Long userId, String nickname, Long roomId, String roomName) {
        currentUserId = userId;
        currentRoomId = roomId;
        currentNickname = nickname;
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
            if (response == null || !isJoinSuccess(response)) return;

            // 환영 배너 (LineReader 활성 전에 직접 터미널에 출력)
            terminal.writer().println(BOLD + " ━━━ " + roomName + " ━━━" + RESET
                    + "  " + DIM + "/quit 으로 퇴장" + RESET);
            terminal.writer().println(DIM + " ─── 챗봇을 사용하려면 /help 를 입력하세요 ───" + RESET);
            terminal.writer().flush();

            // 수신 스레드
            Thread receiver = new Thread(() -> {
                try {
                    String packet;
                    while ((packet = in.readLine()) != null) {
                        String formatted = formatPacket(packet);
                        if (!formatted.isEmpty()) printToScroll(formatted);
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
                    printToScroll(formatOwn(nickname, input));
                }
            }

        } catch (IOException e) {
            System.out.println("\n 채팅 서버 연결 실패: " + e.getMessage());
            System.out.println(" 서버가 실행 중인지 확인하세요. (bash run.sh server)");
        } finally {
            socketOut = null;
            lineReader = null;
            terminal = null;
            currentRoomId = null;
            currentUserId = null;
            currentNickname = null;
            Spinner.setSuppressed(false);
        }
    }

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

    // ── 출력 (JLine printAbove 위임) ────────────────────────────────────────

    private synchronized void printToScroll(String text) {
        // 뷰어가 떠 있는 동안에는 채팅 스크롤에 그리지 않고 버퍼링 → 뷰어 종료 시 한 번에 흘림
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

    /** 챗봇 결과: 로컬에 [봇] 포맷으로 표시 + 다른 클라이언트에 BOT 패킷 릴레이 */
    private void broadcast(String line) {
        String time = LocalTime.now().format(TIME_FMT);
        String prefix = " " + DIM + time + RESET + "  " + YELLOW + BOLD + "[봇]" + RESET + "  ";
        printToScroll(prefix + line);
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
            case "webhook" -> cmdWebhook(arg);
            case "invite"  -> cmdInvite(arg);
            case "notice"  -> cmdNotice(arg);
            case "image"   -> cmdImage(arg);
            case "search"  -> cmdSearch(arg, nickname);
            case "help"    -> cmdHelp();
            default        -> printToScroll(DIM + " 알 수 없는 명령어. /help 로 목록 확인" + RESET);
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

    private void cmdWebhook(String sub) {
        switch (sub.toLowerCase()) {
            case "start" -> {
                if (webhookService.isRunning()) {
                    printToScroll(YELLOW + " 웹훅 서버가 이미 실행 중입니다." + RESET);
                    return;
                }
                try {
                    webhookService.start(event -> {
                        for (String line : event.toString().split("\n"))
                            printToScroll(YELLOW + " [웹훅] " + line + RESET);
                    });
                    printToScroll(GREEN + " 웹훅 서버 시작됨" + RESET);
                } catch (Exception e) {
                    printToScroll(" 웹훅 시작 실패: " + e.getMessage());
                }
            }
            case "stop" -> {
                webhookService.stop();
                printToScroll(DIM + " 웹훅 서버 중지됨" + RESET);
            }
            case "list" -> {
                List<WebhookEvent> events = webhookService.getEvents();
                if (events.isEmpty()) {
                    printToScroll(DIM + " 수신된 웹훅 이벤트 없음" + RESET);
                } else {
                    printToScroll(BOLD + " ─── 웹훅 이벤트 ───" + RESET);
                    for (WebhookEvent e : events)
                        for (String line : e.toString().split("\n"))
                            printToScroll(" " + line);
                }
            }
            default -> printToScroll(" 사용법: /webhook start | stop | list");
        }
    }

    private void cmdInvite(String email) {
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
                printToScroll(formatOwn(nickname, shareMessage));
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

    private void cmdHelp() {
        printToScroll(BOLD + " ─── 명령어 ───" + RESET);
        printToScroll(" /notice             공지 목록 보기");
        printToScroll(" /notice <제목>      공지 등록");
        printToScroll(" /invite <이메일>    멤버 초대");
        printToScroll(" /trend [언어]       GitHub 트렌딩 조회  (예: /trend python)");
        printToScroll(" /news               Hacker News Top 5");
        printToScroll(" /webhook start      웹훅 서버 시작");
        printToScroll(" /webhook stop       웹훅 서버 중지");
        printToScroll(" /webhook list       웹훅 이벤트 목록");
        printToScroll(" /image              이미지 업로드 → ASCII 아트 변환 후 방에 공유");
        printToScroll(" /image <N>.view     수신한 N번 이미지 ASCII 아트 보기");
        printToScroll(" /search <키워드>    현재 방 메시지 검색");
        printToScroll(" /search -r <방번호> <키워드>  특정 방 검색");
        printToScroll(" /search -s         최근 검색 결과 공유");
        printToScroll(" /search -v <shareId>  공유된 검색 결과 보기");
        printToScroll(" /help               이 목록 표시");
        printToScroll(" /quit               채팅방 퇴장");
    }

    // ── 출력 포맷 ─────────────────────────────────────────────────────────────

    private String formatPacket(String packet) {
        String[] parts = Protocol.parse(packet);
        String time = LocalTime.now().format(TIME_FMT);
        return switch (parts[0]) {
            case Protocol.MSG -> {
                if (parts.length < 3) {
                    yield "";
                }
                SearchShareCodec.ParsedSharedMessage parsed = SearchShareCodec.parseMessage(parts[2]);
                if (parsed.hasSharedSession()) {
                    searchSessionManager.saveShared(parsed.getShareId(), parsed.getSession());
                }
                yield " " + DIM + time + RESET + "  " + BOLD + "[" + parts[1] + "]" + RESET + "  " + parsed.getVisibleMessage();
            }
            case Protocol.BOT -> parts.length >= 2
                    ? " " + DIM + time + RESET + "  " + YELLOW + BOLD + "[봇]" + RESET + "  " + parts[1]
                    : "";
            case Protocol.SERVER -> parts.length >= 2
                    ? DIM + " ─── " + parts[1] + " ───" + RESET
                    : "";
            case Protocol.ASCII_ART -> {
                if (parts.length < 4) yield "";
                String sender   = parts[1];
                String filename = parts[2];
                String art      = Protocol.decodeAsciiArt(parts[3]);
                String idx      = String.valueOf(imageCounter.incrementAndGet());
                asciiArtStore.put(idx, art);
                yield " " + DIM + time + RESET + "  " + YELLOW + BOLD + "[" + sender + "]" + RESET
                        + " 이(가) " + filename + " 이미지를 보냈습니다.  "
                        + DIM + "→ /image " + idx + ".view" + RESET;
            }
            default -> "";
        };
    }

    private String formatOwn(String nickname, String content) {
        String time = LocalTime.now().format(TIME_FMT);
        return " " + DIM + time + RESET
                + "  " + CYAN + BOLD + "[나] " + nickname + RESET
                + "  " + content;
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
