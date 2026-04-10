package com.gitalk;

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
import com.gitalk.domain.chat.service.NoticeService;
import com.gitalk.domain.chat.session.ChatRoomSession;
import com.gitalk.domain.chat.view.ChatRoomView;
import com.gitalk.domain.chat.view.SearchView;
import com.gitalk.domain.chatbot.service.NewsService;
import com.gitalk.domain.chatbot.service.TrendingService;
import com.gitalk.domain.chatbot.service.WebhookService;
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
    private static final WebhookService webhookService = new WebhookService();
    private static final MainView mainView = new MainView();
    private static final ChatRoomService chatRoomService = new ChatRoomService(
            new ChatRoomRepositoryImpl(), new ChatRoomMemberRepositoryImpl());

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


    private static final ChatRoomSession chatRoomSession = new ChatRoomSession(
            trendingService, newsService, webhookService,
            new ChatRoomService(new ChatRoomRepositoryImpl(), new ChatRoomMemberRepositoryImpl()),
            new UserService(new UserRepository()),
            noticeService,
            chatSearchService, searchSessionManager, searchView
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
            List<ChatRoom> rooms = chatRoomService.getMyRooms(user.getUserId());
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
                ChatRoom room = createRoom(user);
                if (room != null) enterRoom(user, room);
                continue; // 방 목록 새로고침
            }

            try {
                int choice = Integer.parseInt(input);
                if (choice < 1 || choice > rooms.size()) continue;
                handleRoomAction(user, rooms.get(choice - 1));
            } catch (NumberFormatException ignored) {}
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
        chatRoomSession.enter(user.getUserId(), nickname, room.getRoomId(), room.getName());
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

    private static ChatRoom createRoom(Users user) {
        mainView.clearScreen();
        chatRoomView.printCreateForm();
        String name = readLine();
        if (name == null || name.isBlank()) return null;

        try {
            ChatRoom room = chatRoomService.createRoom(name.trim(), "OPEN", null, user.getUserId());
            chatRoomView.printCreateSuccess(room);
            return room;
        } catch (IllegalArgumentException e) {
            chatRoomView.printCreateFail(e.getMessage());
            chatRoomView.pressEnter();
            readLine();
            return null;
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
