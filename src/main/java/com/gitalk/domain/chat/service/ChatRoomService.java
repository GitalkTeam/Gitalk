package com.gitalk.domain.chat.service;

import com.gitalk.domain.chat.domain.ChatRoom;
import com.gitalk.domain.chat.repository.ChatRoomMemberRepository;
import com.gitalk.domain.chat.repository.ChatRoomRepository;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class ChatRoomService {

    /** OPEN 방 이름 규칙: 한글/영문/숫자/대시/언더스코어/공백, 3~30자 */
    private static final Pattern OPEN_ROOM_NAME_PATTERN =
            Pattern.compile("^[가-힣A-Za-z0-9_\\- ]{3,30}$");
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                           ChatRoomMemberRepository memberRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.memberRepository = memberRepository;
    }

    public List<ChatRoom> getMyRooms(Long userId) {
        return memberRepository.findRoomIdsByUserId(userId).stream()
                .map(roomId -> chatRoomRepository.findById(roomId).orElse(null))
                .filter(r -> r != null)
                .toList();
    }

    public Optional<ChatRoom> getRoom(Long roomId) {
        return chatRoomRepository.findById(roomId);
    }

    /** 방 생성 후 생성자를 자동으로 멤버에 추가. OPEN 방은 정규식 검증, TEAM 방은 길이만 검사. */
    public ChatRoom createRoom(String name, String type, String teamUrl, String description, Long creatorId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("방 이름을 입력해주세요.");
        }
        if ("OPEN".equals(type)) {
            validateOpenRoomName(trimmed);
            if (chatRoomRepository.existsByTypeAndName("OPEN", trimmed)) {
                throw new IllegalArgumentException("이미 같은 이름의 오픈채팅방이 있습니다.");
            }
        } else if (trimmed.length() > 30) {
            throw new IllegalArgumentException("방 이름은 30자 이하여야 합니다.");
        }
        String desc = description == null ? null : description.trim();
        if (desc != null && desc.isEmpty()) desc = null;
        if (desc != null && desc.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("설명은 " + MAX_DESCRIPTION_LENGTH + "자 이하여야 합니다.");
        }
        try {
            ChatRoom room = chatRoomRepository.save(new ChatRoom(trimmed, type, teamUrl, desc, creatorId));
            memberRepository.addMember(room.getRoomId(), creatorId);
            return room;
        } catch (RuntimeException e) {
            // 사전 SELECT 와 INSERT 사이의 race 에서 DB 유니크 인덱스가 잡힌 경우
            if (e.getMessage() != null && e.getMessage().contains("중복된 이름")) {
                throw new IllegalArgumentException("이미 같은 이름의 오픈채팅방이 있습니다.");
            }
            throw e;
        }
    }

    /** OPEN 방 이름 정규식 검증 */
    public void validateOpenRoomName(String name) {
        if (name == null || !OPEN_ROOM_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "오픈 채팅 이름은 한글/영문/숫자/대시(-)/언더스코어(_)/공백 3~30자만 가능합니다.");
        }
    }

    /** 인기 오픈 채팅 목록 (멤버수 desc) */
    public List<ChatRoom> listPopularOpenRooms(int limit) {
        return chatRoomRepository.findPopularOpenRooms(limit);
    }

    /** 이름 부분 일치 검색 */
    public List<ChatRoom> searchOpenRooms(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return chatRoomRepository.searchOpenRoomsByName(keyword.trim(), limit);
    }

    /** 오픈 채팅 자유 입장: 이미 멤버여도 무해 (UNIQUE 인덱스로 INSERT IGNORE 효과) */
    public void joinOpenRoom(Long roomId, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 채팅방입니다."));
        if (!room.isOpen()) {
            throw new RuntimeException("오픈 채팅방이 아닙니다.");
        }
        memberRepository.addMember(roomId, userId);
    }

    public void joinRoom(Long roomId, Long userId) {
        chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 채팅방입니다."));
        memberRepository.addMember(roomId, userId);
    }

    /** 방장만 삭제 가능. chat_room_members는 ON DELETE CASCADE로 자동 삭제 */
    public void deleteRoom(Long roomId, Long requesterId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 채팅방입니다."));
        if (!requesterId.equals(room.getCreatorId())) {
            throw new RuntimeException("방장만 채팅방을 삭제할 수 있습니다.");
        }
        chatRoomRepository.deleteById(roomId);
    }

    /** 초대: 초대자가 멤버인지 확인 후 대상 추가 */
    public void inviteUser(Long roomId, Long inviterId, Long targetUserId) {
        if (!memberRepository.isMember(roomId, inviterId)) {
            throw new RuntimeException("채팅방 멤버만 초대할 수 있습니다.");
        }
        memberRepository.addMember(roomId, targetUserId);
    }

    public boolean isCreator(Long roomId, Long userId) {
        return chatRoomRepository.findById(roomId)
                .map(r -> userId.equals(r.getCreatorId()))
                .orElse(false);
    }

    public boolean isMember(Long roomId, Long userId) {
        return memberRepository.isMember(roomId, userId);
    }

    public int getMemberCount(Long roomId) {
        return memberRepository.findUserIdsByRoomId(roomId).size();
    }

    /** 방에 GitHub repo 연결 (URL + webhook secret + GitHub 측 hook id 저장) */
    public void linkRepo(Long roomId, String teamUrl, String webhookSecret, Long webhookId) {
        chatRoomRepository.updateRepoLink(roomId, teamUrl, webhookSecret, webhookId);
    }

    /** repo 연결 해제 */
    public void unlinkRepo(Long roomId) {
        chatRoomRepository.updateRepoLink(roomId, null, null, null);
    }
}
