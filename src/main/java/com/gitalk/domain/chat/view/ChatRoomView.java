package com.gitalk.domain.chat.view;

import com.gitalk.common.util.Layout;
import com.gitalk.domain.chat.domain.ChatRoom;
import com.gitalk.domain.chat.domain.Notice;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatRoomView {

    private static final int WIDTH = 68;
    private static final String DIV = "─".repeat(WIDTH);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yy/MM/dd");

    // ── 채팅방 목록 컬럼 폭 (표시 너비 기준) ─────────────────────────────────
    private static final int W_INDEX   = 3;   // " 1." ~ "99."
    private static final int W_TYPE    = 6;   // "[오픈]" 또는 "[팀]"
    private static final int W_NAME    = 22;  // 방 이름 (초과 시 …)
    private static final int W_CREATOR = 10;  // 만든이 닉네임
    private static final int W_DATE    = 8;   // yy/MM/dd
    private static final int W_COUNT   = 5;   // "999명"

    // ── 공지판 컬럼 폭 ───────────────────────────────────────────────────────
    private static final int W_NOTICE_INDEX = 3;
    private static final int W_NOTICE_ROOM  = 12;  // "[방이름]" 포함
    private static final int W_NOTICE_TITLE = 30;
    private static final int W_NOTICE_TIME  = 11;  // "MM/dd HH:mm"

    // ── 단일 행 항목 최대 폭 (방 액션 메뉴 등) ─────────────────────────────
    private static final int W_ROOM_HEADER  = 60;
    private static final int W_NOTICE_LINE  = 50;

    private static String centerLine(String text) {
        return Layout.center(text, WIDTH);
    }

    // ── 채팅방 목록 ──────────────────────────────────────────────────────────

    public void printRoomList(List<ChatRoom> rooms, Map<Long, Integer> memberCounts) {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("내 채팅방"));
        System.out.println(DIV);
        if (rooms.isEmpty()) {
            System.out.println(centerLine("참여 중인 채팅방이 없습니다."));
            System.out.println(centerLine("방을 만들거나 초대를 받아 참여하세요."));
        } else {
            for (int i = 0; i < rooms.size(); i++) {
                ChatRoom r = rooms.get(i);
                String idx     = Layout.padLeft((i + 1) + ".", W_INDEX);
                String typeTag = Layout.fit("TEAM".equals(r.getType()) ? "[팀]" : "[오픈]", W_TYPE);
                String name    = Layout.fit(r.getName(), W_NAME);
                String creator = Layout.fit(r.getCreatorNickname() != null ? r.getCreatorNickname() : "?", W_CREATOR);
                String date    = Layout.fit(r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FMT) : "", W_DATE);
                int memberNum  = memberCounts == null ? 0 : memberCounts.getOrDefault(r.getRoomId(), 0);
                String count   = Layout.padLeft(memberNum + "명", W_COUNT);
                System.out.println(" " + idx + " " + typeTag + " " + name
                        + "  " + creator + "  " + date + "  " + count);
            }
        }
        System.out.println(DIV);
        System.out.println(centerLine("C. 방 만들기    0. 취소"));
        System.out.println(DIV);
        System.out.print(" 선택 > ");
        System.out.flush();
    }

    // ── 방 액션 메뉴 (최신 공지 포함) ───────────────────────────────────────

    public void printRoomActionMenu(ChatRoom room, boolean isCreator, Notice latestNotice) {
        String typeTag = "TEAM".equals(room.getType()) ? "[팀]" : "[오픈]";
        String header = Layout.truncate(typeTag + " " + room.getName(), W_ROOM_HEADER);
        System.out.println("\n" + DIV);
        System.out.println(centerLine(header));
        if (latestNotice != null) {
            String author = latestNotice.getAuthorNickname() != null ? latestNotice.getAuthorNickname() : "";
            String time   = latestNotice.getCreatedAt() != null ? latestNotice.getCreatedAt().format(FMT) : "";
            String title  = Layout.truncate(latestNotice.getTitle(), W_NOTICE_LINE);
            System.out.println(centerLine("📢 " + title + "  (" + author + " · " + time + ")"));
        }
        System.out.println(DIV);
        List<String> menu = new ArrayList<>();
        menu.add("1. 입장");
        menu.add("2. 멤버 초대");
        if (isCreator) menu.add("3. 방 삭제 (방장)");
        menu.add("0. 뒤로");
        for (String line : Layout.centerBlock(menu, WIDTH)) System.out.println(line);
        System.out.println(DIV);
        System.out.print(" 선택 > ");
        System.out.flush();
    }

    // ── 공지판 ───────────────────────────────────────────────────────────────

    public void printNoticeBoard(List<Notice> notices, Map<Long, String> roomNames) {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("📢 공지판"));
        System.out.println(DIV);
        if (notices.isEmpty()) {
            System.out.println(centerLine("등록된 공지가 없습니다."));
        } else {
            for (int i = 0; i < notices.size(); i++) {
                Notice n = notices.get(i);
                String roomName = roomNames.getOrDefault(n.getRoomId(), "알 수 없음");
                String time = n.getCreatedAt() != null ? n.getCreatedAt().format(FMT) : "";
                String idx   = Layout.padLeft((i + 1) + ".", W_NOTICE_INDEX);
                String room  = Layout.fit("[" + roomName + "]", W_NOTICE_ROOM);
                String title = Layout.fit(n.getTitle(), W_NOTICE_TITLE);
                String t     = Layout.fit(time, W_NOTICE_TIME);
                System.out.println(" " + idx + " " + room + " " + title + "  " + t);
            }
        }
        System.out.println(DIV);
        System.out.println(centerLine("번호: 상세 보기    0. 뒤로"));
        System.out.println(DIV);
        System.out.print(" 선택 > ");
        System.out.flush();
    }

    public void printNoticeDetail(Notice n, String roomName) {
        String author = n.getAuthorNickname() != null ? n.getAuthorNickname() : "";
        String time   = n.getCreatedAt() != null ? n.getCreatedAt().format(FMT) : "";
        String title  = Layout.truncate(n.getTitle(), 60);
        String room   = Layout.truncate(roomName, 24);
        System.out.println("\n" + DIV);
        System.out.println(centerLine("📢 " + title));
        System.out.println(DIV);
        System.out.println(centerLine("방: " + room + "  |  작성: " + author + "  |  일시: " + time));
        if (n.getContent() != null && !n.getContent().isBlank()) {
            System.out.println(DIV);
            for (String line : Layout.wrapWords(n.getContent(), 66)) {
                System.out.println(" " + line);
            }
        }
        System.out.println(DIV);
        pressEnter();
    }

    // ── 방 만들기 ────────────────────────────────────────────────────────────

    public void printCreateForm() {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("채팅방 만들기"));
        System.out.println(DIV);
        System.out.print(" 방 이름 > ");
        System.out.flush();
    }

    public void printCreateSuccess(ChatRoom room) {
        System.out.println(" '" + room.getName() + "' 채팅방이 생성되었습니다.");
    }

    public void printCreateFail(String reason) {
        System.out.println(" 방 생성 실패: " + reason);
    }

    // ── 초대 ─────────────────────────────────────────────────────────────────

    public void printInviteForm() {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("멤버 초대"));
        System.out.println(DIV);
        System.out.print(" 초대할 사용자 이메일 > ");
        System.out.flush();
    }

    public void printInviteSuccess(String name) {
        System.out.println(" '" + name + "' 님을 초대했습니다.");
        pressEnter();
    }

    public void printInviteFail(String reason) {
        System.out.println(" 초대 실패: " + reason);
        pressEnter();
    }

    // ── 삭제 ─────────────────────────────────────────────────────────────────

    public void printDeleteConfirm(ChatRoom room) {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("'" + room.getName() + "' 채팅방을 삭제하시겠습니까?"));
        System.out.println(centerLine("삭제하면 모든 멤버가 퇴장됩니다."));
        System.out.println(DIV);
        System.out.print(" 삭제하려면 'y' 입력 > ");
        System.out.flush();
    }

    public void printDeleteSuccess(ChatRoom room) {
        System.out.println(" '" + room.getName() + "' 채팅방이 삭제되었습니다.");
        pressEnter();
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    public void pressEnter() {
        System.out.print(" 계속하려면 엔터를 누르세요...");
        System.out.flush();
    }

    public void printEntering(ChatRoom room) {
        System.out.println("\n" + DIV);
        System.out.println(centerLine(room.getName() + " 채팅방에 입장합니다."));
        System.out.println(centerLine("종료: /quit"));
        System.out.println(DIV);
    }

    public void printConnectFailed() {
        System.out.println(" 채팅 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인하세요.");
    }
}
