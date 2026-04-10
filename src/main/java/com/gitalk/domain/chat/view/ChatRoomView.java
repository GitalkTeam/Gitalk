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

    /**
     * 내 채팅방 목록 — TEAM/OPEN 섹션으로 분리해서 출력.
     * rooms 리스트는 controller 에서 TEAM 먼저, OPEN 나중으로 정렬되어 있다고 가정.
     * 번호는 섹션을 가로질러 1..N 으로 연속 부여 (사용자가 단일 숫자로 선택할 수 있도록).
     */
    public void printRoomList(List<ChatRoom> rooms, Map<Long, Integer> memberCounts) {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("내 채팅방"));
        System.out.println(DIV);

        if (rooms.isEmpty()) {
            System.out.println(centerLine("참여 중인 채팅방이 없습니다."));
            System.out.println(centerLine("[C] 팀 방 만들기 또는 [O] 오픈 채팅에서 둘러보기"));
        } else {
            // 컬럼 헤더 (회색 한 줄)
            printRoomListHeader();

            String currentType = null;
            for (int i = 0; i < rooms.size(); i++) {
                ChatRoom r = rooms.get(i);
                if (!r.getType().equals(currentType)) {
                    currentType = r.getType();
                    String label = "TEAM".equals(currentType) ? "팀 채팅" : "오픈 채팅";
                    System.out.println(" ─── " + label + " ───");
                }
                printRoomListRow(i + 1, r, memberCounts);
            }
        }

        System.out.println(DIV);
        System.out.println(centerLine("C. 팀 방 만들기    O. 오픈 채팅 둘러보기    0. 취소"));
        System.out.println(DIV);
        System.out.print(" 선택 > ");
        System.out.flush();
    }

    private void printRoomListHeader() {
        String idx     = Layout.padLeft("#", W_INDEX);
        String name    = Layout.fit("이름", W_NAME);
        String creator = Layout.fit("만든이", W_CREATOR);
        String date    = Layout.fit("날짜", W_DATE);
        String count   = Layout.padLeft("인원", W_COUNT);
        System.out.println(" " + idx + " " + name
                + "  " + creator + "  " + date + "  " + count);
    }

    private void printRoomListRow(int index, ChatRoom r, Map<Long, Integer> memberCounts) {
        String idx     = Layout.padLeft(index + ".", W_INDEX);
        String name    = Layout.fit(r.getName(), W_NAME);
        String creator = Layout.fit(r.getCreatorNickname() != null ? r.getCreatorNickname() : "?", W_CREATOR);
        String date    = Layout.fit(r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_FMT) : "", W_DATE);
        int memberNum  = memberCounts == null ? 0 : memberCounts.getOrDefault(r.getRoomId(), 0);
        String count   = Layout.padLeft(memberNum + "명", W_COUNT);
        System.out.println(" " + idx + " " + name
                + "  " + creator + "  " + date + "  " + count);
    }

    // ── 오픈 채팅 ────────────────────────────────────────────────────────────

    private static final int W_OPEN_NAME    = 22;
    private static final int W_OPEN_COUNT   = 5;   // "999명"
    private static final int W_OPEN_DESC    = 30;  // 토픽 설명

    public void printOpenRoomList(String header, List<ChatRoom> rooms, Map<Long, Integer> memberCounts) {
        System.out.println("\n" + DIV);
        System.out.println(centerLine(header));
        System.out.println(DIV);
        if (rooms == null || rooms.isEmpty()) {
            System.out.println(centerLine("표시할 오픈 채팅이 없습니다."));
            System.out.println(centerLine("[C] 키로 새 오픈 방을 만들어 보세요."));
        } else {
            printOpenRoomListHeader();
            for (int i = 0; i < rooms.size(); i++) {
                printOpenRoomListRow(i + 1, rooms.get(i), memberCounts);
            }
        }
        System.out.println(DIV);
        System.out.println(centerLine("C. 새 오픈 방 만들기    S. 검색    0. 뒤로"));
        System.out.println(DIV);
        System.out.print(" 선택 > ");
        System.out.flush();
    }

    private void printOpenRoomListHeader() {
        String idx   = Layout.padLeft("#", W_INDEX);
        String name  = Layout.fit("이름", W_OPEN_NAME);
        String count = Layout.padLeft("인원", W_OPEN_COUNT);
        String desc  = Layout.fit("설명", W_OPEN_DESC);
        System.out.println(" " + idx + " " + name + "  " + count + "  " + desc);
    }

    private void printOpenRoomListRow(int index, ChatRoom r, Map<Long, Integer> memberCounts) {
        String idx   = Layout.padLeft(index + ".", W_INDEX);
        String name  = Layout.fit(r.getName(), W_OPEN_NAME);
        int memberN  = memberCounts == null ? 0 : memberCounts.getOrDefault(r.getRoomId(), 0);
        String count = Layout.padLeft(memberN + "명", W_OPEN_COUNT);
        String desc  = Layout.fit(r.getDescription() != null ? r.getDescription() : "", W_OPEN_DESC);
        System.out.println(" " + idx + " " + name + "  " + count + "  " + desc);
    }

    public void printOpenRoomSearchPrompt() {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("오픈 채팅 검색"));
        System.out.println(DIV);
        System.out.print(" 검색어 (취소: 빈값) > ");
        System.out.flush();
    }

    public void printOpenRoomCreateForm() {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("새 오픈 채팅 만들기"));
        System.out.println(DIV);
        System.out.println(centerLine("이름 규칙: 한글/영문/숫자/대시(-)/언더스코어(_)/공백, 3~30자"));
        System.out.println(DIV);
        System.out.print(" 이름 > ");
        System.out.flush();
    }

    public void printOpenRoomDescriptionPrompt() {
        System.out.print(" 설명 (선택, Enter로 건너뛰기) > ");
        System.out.flush();
    }

    public void printOpenRoomCreateConfirm(String name, String description) {
        System.out.println();
        System.out.println(" 이름 : " + name);
        System.out.println(" 설명 : " + (description == null || description.isBlank() ? "(없음)" : description));
        System.out.print(" 생성하시겠습니까? (y/n) > ");
        System.out.flush();
    }

    public void printOpenRoomNoResult(String keyword) {
        System.out.println(" '" + keyword + "' 에 해당하는 오픈 채팅이 없습니다.");
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
        printTeamCreateForm();
    }

    public void printTeamCreateForm() {
        System.out.println("\n" + DIV);
        System.out.println(centerLine("새 팀 채팅방 만들기"));
        System.out.println(DIV);
        System.out.println(centerLine("팀원을 초대해서 사용하는 비공개 방입니다."));
        System.out.println(DIV);
        System.out.print(" 방 이름 (1~30자) > ");
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
