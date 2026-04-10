package com.gitalk;

import com.gitalk.common.api.GithubWebhookServer;
import com.gitalk.common.api.OpenAIClient;
import com.gitalk.common.util.Layout;
import com.gitalk.common.view.MainView;
import com.gitalk.domain.chat.config.MongoConnectionManager;
import com.gitalk.domain.chat.domain.ChatRoom;
import com.gitalk.domain.chat.domain.Notice;
import com.gitalk.domain.chat.repository.*;
import com.gitalk.domain.chat.search.service.ChatSearchService;
import com.gitalk.domain.chat.search.service.SearchSessionManager;
import com.gitalk.domain.chat.service.ChatRoomService;
import com.gitalk.domain.chat.service.ChatService;
import com.gitalk.domain.chat.service.MissedMessageService;
import com.gitalk.domain.chat.service.NoticeService;
import com.gitalk.domain.chat.service.RepoService;
import com.gitalk.domain.chat.session.ChatRoomSession;
import com.gitalk.domain.chat.view.ChatRoomView;
import com.gitalk.domain.chat.view.SearchView;
import com.gitalk.domain.chatbot.service.NewsService;
import com.gitalk.domain.chatbot.service.TrendingService;
import com.gitalk.domain.oauth.github.service.GithubAuthService;
import com.gitalk.domain.oauth.github.service.GithubOauthClient;
import com.gitalk.domain.user.model.Users;
import com.gitalk.domain.user.repository.UserRepository;
import com.gitalk.domain.user.service.UserService;
import com.gitalk.domain.user.view.JoinAndLoginView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GitalkApplication {

    private static final int POST_LOGIN_WIDTH = 68;
    private static final TrendingService trendingService = new TrendingService();
    private static final NewsService newsService = new NewsService();
    private static final MainView mainView = new MainView();
    private static final ChatRoomRepository sharedChatRoomRepository = new ChatRoomRepositoryImpl();
    private static final ChatRoomMemberRepository sharedMemberRepository = new ChatRoomMemberRepositoryImpl();
    private static final ChatRoomService chatRoomService = new ChatRoomService(
            sharedChatRoomRepository, sharedMemberRepository);

    private static final ChatRoomView chatRoomView = new ChatRoomView();
    private static final NoticeService noticeService = new NoticeService(new NoticeRepositoryImpl());

    private static final MongoConnectionManager mongoConnectionManager = MongoConnectionManager.getInstance();

    private static final MongoChatMessageRepository mongoChatMessageRepository =
            new MongoChatMessageRepository(mongoConnectionManager);

    private static final MongoSearchRepositoryImpl mongoSearchRepository =
            new MongoSearchRepositoryImpl(mongoConnectionManager);

    private static final ChatService chatService = new ChatService(mongoChatMessageRepository);

    private static final ChatSearchService chatSearchService =
            new ChatSearchService(mongoSearchRepository, chatService, chatRoomService);

    private static final SearchSessionManager searchSessionManager = new SearchSessionManager();
    private static final SearchView searchView = new SearchView();

    private static final MissedMessageService missedMessageService = new MissedMessageService(
            mongoChatMessageRepository, sharedMemberRepository, new OpenAIClient());

    // Repo / Webhook
    private static final RepoService repoService = new RepoService();
    // GithubWebhookServer 는 클라이언트(이 process)에선 broadcast 안 함 — null 주입 OK,
    // 단지 /repo events 가 호출하는 getRecentEvents 만 위해 client 측 인스턴스 둠.
    // 실제 webhook 수신·broadcast 는 ChatServer 측 인스턴스가 담당.
    private static final GithubWebhookServer webhookServer =
            new GithubWebhookServer(sharedChatRoomRepository, chatService);

    private static final ChatRoomSession chatRoomSession = new ChatRoomSession(
            trendingService, newsService, repoService, webhookServer,
            chatRoomService,
            new UserService(new UserRepository()),
            noticeService,
            chatSearchService, searchSessionManager, searchView,
            missedMessageService
            );

    private static UserService userService;
    private static BufferedReader reader;

    public static void main(String[] args) throws Exception {
        reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        // 1. 배너 + 메인 메뉴
        mainView.printBanner();
        String menuChoice = readLine();
        if ("2".equals(menuChoice) || menuChoice == null) { mainView.printExit(); return; }
        if (!"1".equals(menuChoice)) return;

        // 2. 로그인 / 회원가입
        UserRepository userRepository = new UserRepository();
        userService = new UserService(userRepository);
        GithubOauthClient githubOauthClient = new GithubOauthClient();
        GithubAuthService githubAuthService = new GithubAuthService(userRepository, githubOauthClient);
        JoinAndLoginView joinAndLoginView = new JoinAndLoginView(userService, githubAuthService, reader);
        Users user = joinAndLoginView.start();
        if (user == null) {
            mainView.printExit();
            return;
        }

        // 3. 로그인 후 메뉴 루프
        while (true) {
            mainView.clearScreen();
            printPostLoginMenu(user);
            String choice = readLine();

            // EOF/Ctrl+D → 종료
            if (choice == null) {
                mainView.printExit();
                return;
            }

            if (handleGlobalSlashCommand(user, choice)) {
                continue;
            }

            switch (choice.trim()) {
                case "1" -> runChatRoom(user);
                case "2" -> runNoticeBoard(user);
                case "3" -> { mainView.printExit(); return; }
            }
        }
    }

    private static void printPostLoginMenu(Users user) {
        String nick = user.getNickname() != null ? user.getNickname() : user.getEmail();
        System.out.println();
        System.out.println(Layout.center("안녕하세요, " + nick + "님!", POST_LOGIN_WIDTH));

        // 공지 건수 표시
        try {
            int count = noticeService.getRecentForUser(user.getUserId()).size();
            if (count > 0) {
                System.out.println(Layout.center(
                        "📢 공지 " + count + "건이 있습니다. (메뉴 2번에서 확인)",
                        POST_LOGIN_WIDTH));
            }
        } catch (Exception ignored) {}

        System.out.println("─".repeat(POST_LOGIN_WIDTH));
        System.out.println(Layout.center("1. 채팅방         2. 공지판              3. 종료", POST_LOGIN_WIDTH));
        System.out.println("─".repeat(POST_LOGIN_WIDTH));
        System.out.print(" 선택 > ");
        System.out.flush();
    }

    // ── 채팅방 목록 루프 ────────────────────────────────────────────────────

    private static void runChatRoom(Users user) {
        while (true) {
            mainView.clearScreen();
            // 화면 표시 / 번호 매핑이 일치하도록 TEAM 먼저, OPEN 나중으로 정렬해서 view에 넘김
            List<ChatRoom> rooms = chatRoomService.getMyRooms(user.getUserId()).stream()
                    .sorted(java.util.Comparator
                            .comparingInt((ChatRoom r) -> "TEAM".equals(r.getType()) ? 0 : 1)
                            .thenComparing(ChatRoom::getRoomId))
                    .toList();
            Map<Long, Integer> memberCounts = rooms.stream()
                    .collect(Collectors.toMap(
                            ChatRoom::getRoomId,
                            r -> chatRoomService.getMemberCount(r.getRoomId())));
            chatRoomView.printRoomList(rooms, memberCounts);

            String input = readLine();
            if (input == null || input.equals("0")) return;

            if (handleGlobalSlashCommand(user, input)) {
                continue;
            }

            if ("c".equalsIgnoreCase(input)) {
                ChatRoom room = createTeamRoom(user);
                if (room != null) enterRoom(user, room);
                continue; // 방 목록 새로고침
            }

            if ("o".equalsIgnoreCase(input)) {
                runOpenChat(user);
                continue; // 오픈 채팅 둘러보기 종료 후 내 방 목록으로 복귀
            }

            try {
                int choice = Integer.parseInt(input);
                if (choice < 1 || choice > rooms.size()) continue;
                handleRoomAction(user, rooms.get(choice - 1));
            } catch (NumberFormatException ignored) {}
        }
    }

    // ── 오픈 채팅 둘러보기 ──────────────────────────────────────────────────

    private static final int OPEN_CHAT_LIMIT = 20;

    private static void runOpenChat(Users user) {
        List<ChatRoom> rooms = chatRoomService.listPopularOpenRooms(OPEN_CHAT_LIMIT);
        String header = "오픈 채팅 — 인기";

        while (true) {
            mainView.clearScreen();
            Map<Long, Integer> memberCounts = rooms.stream()
                    .collect(Collectors.toMap(
                            ChatRoom::getRoomId,
                            r -> chatRoomService.getMemberCount(r.getRoomId())));
            chatRoomView.printOpenRoomList(header, rooms, memberCounts);

            String input = readLine();
            if (input == null || input.equals("0")) return;

            if ("c".equalsIgnoreCase(input)) {
                ChatRoom created = createOpenChat(user);
                if (created != null) enterRoom(user, created);
                rooms = chatRoomService.listPopularOpenRooms(OPEN_CHAT_LIMIT);
                header = "오픈 채팅 — 인기";
                continue;
            }

            if ("s".equalsIgnoreCase(input)) {
                String keyword = promptSearchKeyword();
                if (keyword == null || keyword.isBlank()) continue;
                rooms = chatRoomService.searchOpenRooms(keyword, OPEN_CHAT_LIMIT);
                header = "검색 결과: \"" + keyword + "\"";
                if (rooms.isEmpty()) {
                    chatRoomView.printOpenRoomNoResult(keyword);
                    chatRoomView.pressEnter();
                    readLine();
                    rooms = chatRoomService.listPopularOpenRooms(OPEN_CHAT_LIMIT);
                    header = "오픈 채팅 — 인기";
                }
                continue;
            }

            try {
                int choice = Integer.parseInt(input);
                if (choice < 1 || choice > rooms.size()) continue;
                ChatRoom selected = rooms.get(choice - 1);
                // 자동 가입 후 입장
                try {
                    chatRoomService.joinOpenRoom(selected.getRoomId(), user.getUserId());
                } catch (Exception e) {
                    System.out.println(" 입장 실패: " + e.getMessage());
                    chatRoomView.pressEnter();
                    readLine();
                    continue;
                }
                enterRoom(user, selected);
                // 입장 후 복귀 시 목록 새로고침
                rooms = chatRoomService.listPopularOpenRooms(OPEN_CHAT_LIMIT);
                header = "오픈 채팅 — 인기";
            } catch (NumberFormatException ignored) {}
        }
    }

    private static String promptSearchKeyword() {
        mainView.clearScreen();
        chatRoomView.printOpenRoomSearchPrompt();
        return readLine();
    }

    private static ChatRoom createOpenChat(Users user) {
        mainView.clearScreen();
        chatRoomView.printOpenRoomCreateForm();
        String name = readLine();
        if (name == null || name.isBlank()) return null;

        chatRoomView.printOpenRoomDescriptionPrompt();
        String description = readLine();

        chatRoomView.printOpenRoomCreateConfirm(name.trim(), description);
        String confirm = readLine();
        if (!"y".equalsIgnoreCase(confirm)) return null;

        try {
            ChatRoom room = chatRoomService.createRoom(name.trim(), "OPEN", null, description, user.getUserId());
            chatRoomView.printCreateSuccess(room);
            chatRoomView.pressEnter();
            readLine();
            return room;
        } catch (IllegalArgumentException e) {
            chatRoomView.printCreateFail(e.getMessage());
            chatRoomView.pressEnter();
            readLine();
            return null;
        }
    }

    // ── 방 액션 메뉴 ────────────────────────────────────────────────────────

    private static void handleRoomAction(Users user, ChatRoom room) {
        while (true) {
            mainView.clearScreen();
            boolean isCreator = chatRoomService.isCreator(room.getRoomId(), user.getUserId());
            Notice latestNotice = noticeService.getNotices(room.getRoomId()).stream().findFirst().orElse(null);
            chatRoomView.printRoomActionMenu(room, isCreator, latestNotice);
            String input = readLine();

            if (handleGlobalSlashCommand(user, input)) {
                continue;
            }

            switch (input == null ? "0" : input.trim()) {
                case "1" -> { enterRoom(user, room); return; }
                case "2" -> inviteToRoom(user, room);
                case "3" -> { if (isCreator && deleteRoom(user, room)) return; }
                case "0" -> { return; }
            }
        }
    }

    // ── 공지판 ──────────────────────────────────────────────────────────────

    private static void runNoticeBoard(Users user) {
        while (true) {
            mainView.clearScreen();
            List<Notice> notices = noticeService.getRecentForUser(user.getUserId());
            List<ChatRoom> myRooms = chatRoomService.getMyRooms(user.getUserId());
            Map<Long, String> roomNames = myRooms.stream()
                    .collect(Collectors.toMap(ChatRoom::getRoomId, ChatRoom::getName));

            chatRoomView.printNoticeBoard(notices, roomNames);
            String input = readLine();
            if (input == null || input.equals("0")) return;

            if (handleGlobalSlashCommand(user, input)) {
                continue;
            }

            try {
                int choice = Integer.parseInt(input);
                if (choice < 1 || choice > notices.size()) continue;
                Notice n = notices.get(choice - 1);
                mainView.clearScreen();
                chatRoomView.printNoticeDetail(n, roomNames.getOrDefault(n.getRoomId(), "알 수 없음"));
                readLine();
            } catch (NumberFormatException ignored) {}
        }
    }

    private static void enterRoom(Users user, ChatRoom room) {
        chatRoomView.printEntering(room);
        String nickname = resolveNickname(user);
        chatRoomSession.enter(user.getUserId(), nickname,
                user.getAuthAccessToken(), room.getRoomId(), room.getName());
    }

    private static boolean handleGlobalSlashCommand(Users user, String input) {
        if (input == null || !input.startsWith("/")) {
            return false;
        }

        chatRoomSession.handleOutsideRoomCommand(user.getUserId(), resolveNickname(user), input);
        return true;
    }

    private static String resolveNickname(Users user) {
        return user.getNickname() != null ? user.getNickname() : user.getEmail();
    }

    private static void inviteToRoom(Users inviter, ChatRoom room) {
        mainView.clearScreen();
        chatRoomView.printInviteForm();
        String email = readLine();
        if (email == null || email.isBlank()) return;

        userService.findByEmail(email).ifPresentOrElse(
            target -> {
                try {
                    chatRoomService.inviteUser(room.getRoomId(), inviter.getUserId(), target.getUserId());
                    String name = target.getNickname() != null ? target.getNickname() : target.getEmail();
                    chatRoomView.printInviteSuccess(name);
                } catch (Exception e) {
                    chatRoomView.printInviteFail(e.getMessage());
                }
            },
            () -> chatRoomView.printInviteFail("'" + email + "' 에 해당하는 사용자가 없습니다.")
        );
        readLine(); // pressEnter() 뒤 실제 입력 소비
    }

    /** @return true = 방이 삭제되어 액션 메뉴를 종료해야 할 때 */
    private static boolean deleteRoom(Users user, ChatRoom room) {
        mainView.clearScreen();
        chatRoomView.printDeleteConfirm(room);
        String confirm = readLine();
        if (!"y".equalsIgnoreCase(confirm)) return false;

        try {
            chatRoomService.deleteRoom(room.getRoomId(), user.getUserId());
            chatRoomView.printDeleteSuccess(room);
        } catch (Exception e) {
            System.out.println(" 삭제 실패: " + e.getMessage());
            chatRoomView.pressEnter();
            readLine();
            return false;
        }
        readLine(); // pressEnter() 뒤 실제 입력 소비
        return true;
    }

    private static ChatRoom createTeamRoom(Users user) {
        mainView.clearScreen();
        chatRoomView.printTeamCreateForm();
        String name = readLine();
        if (name == null || name.isBlank()) return null;

        // 1. 방 먼저 생성 (repo 없이) — repo 연결은 ID 필요해서 생성 후
        ChatRoom room;
        try {
            room = chatRoomService.createRoom(name.trim(), "TEAM", null, null, user.getUserId());
        } catch (IllegalArgumentException e) {
            chatRoomView.printCreateFail(e.getMessage());
            chatRoomView.pressEnter();
            readLine();
            return null;
        }

        // 2. GitHub 연동 사용자에게 repo 연결 권유
        String token = user.getAuthAccessToken();
        if (token != null && !token.isBlank()) {
            offerRepoConnection(room, token);
        } else {
            System.out.println();
            System.out.println(" ℹ GitHub 미연동 — repo 없이 생성됨 (나중에 /repo connect 로 추가 가능)");
        }

        chatRoomView.printCreateSuccess(room);
        chatRoomView.pressEnter();
        readLine();
        return room;
    }

    /**
     * 새로 만든 TEAM 방에 GitHub repo 연결 권유. 사용자가 거부하거나 실패해도
     * 방 자체는 이미 생성된 상태이므로 흐름은 계속 진행.
     */
    private static void offerRepoConnection(ChatRoom room, String token) {
        System.out.println();
        System.out.print(" GitHub repo 와 연결하시겠어요? (y/n) > ");
        System.out.flush();
        String answer = readLine();
        if (!"y".equalsIgnoreCase(answer)) return;

        System.out.println(" GitHub 에서 레포 목록을 가져오는 중...");
        List<RepoService.RepoInfo> repos;
        try {
            repos = repoService.listMyRepos(token, 100);
        } catch (Exception e) {
            System.out.println(" ✗ 레포 목록 조회 실패: " + e.getMessage());
            return;
        }
        if (repos.isEmpty()) {
            System.out.println(" 접근 가능한 레포가 없습니다.");
            return;
        }

        chatRoomView.printRepoList(repos);
        String input = readLine();
        if (input == null || input.isBlank() || "0".equals(input) || "s".equalsIgnoreCase(input)) {
            System.out.println(" 건너뛰기 — repo 없이 생성됨 (나중에 /repo connect 로 추가 가능)");
            return;
        }

        int choice;
        try {
            choice = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            System.out.println(" 잘못된 입력 — 건너뛰기");
            return;
        }
        if (choice < 1 || choice > repos.size()) {
            System.out.println(" 범위 밖 — 건너뛰기");
            return;
        }
        RepoService.RepoInfo picked = repos.get(choice - 1);

        try {
            String payloadUrl = RepoService.resolveWebhookBaseUrl() + "/" + room.getRoomId();
            RepoService.WebhookRegistration reg = repoService.registerWebhook(picked, token, payloadUrl);
            chatRoomService.linkRepo(room.getRoomId(), picked.htmlUrl(), reg.secret(), reg.hookId());
            System.out.println(" ✓ " + picked.fullName() + " 연결됨");
            System.out.println(" ✓ Webhook 자동 등록 완료 (id=" + reg.hookId() + ")");
            System.out.println(" ✓ Payload URL: " + payloadUrl);
        } catch (Exception e) {
            System.out.println(" ✗ Webhook 등록 실패: " + e.getMessage());
            System.out.println("   (admin:repo_hook scope + repo admin 권한이 필요합니다)");
            System.out.println("   방은 생성됐고 repo 없이 사용 가능. 나중에 /repo connect 로 재시도하세요.");
        }
    }

    // ── 입력 헬퍼 ──────────────────────────────────────────────────────────

    private static String readLine() {
        try {
            String line = reader.readLine();
            if (line == null) return null;
            return line.replaceAll("[\\p{Cntrl}\\uFEFF\\uFFFD]", "").trim();
        } catch (Exception e) {
            return null;
        }
    }
}
