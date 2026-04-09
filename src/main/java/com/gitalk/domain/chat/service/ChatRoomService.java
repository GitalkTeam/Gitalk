package com.gitalk.domain.chat.service;

import com.gitalk.domain.chat.domain.ChatRoom;
import com.gitalk.domain.chat.repository.ChatRoomMemberRepository;
import com.gitalk.domain.chat.repository.ChatRoomRepository;

import java.util.List;

public class ChatRoomService {

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

    /** 방 생성 후 생성자를 자동으로 멤버에 추가 */
    public ChatRoom createRoom(String name, String type, String teamUrl, Long creatorId) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("방 이름을 입력해주세요.");
        }
        if (trimmed.length() > 30) {
            throw new IllegalArgumentException("방 이름은 30자 이하여야 합니다.");
        }
        // OPEN 타입은 전역 유니크. TEAM 은 자유.
        if ("OPEN".equals(type) && chatRoomRepository.existsByTypeAndName("OPEN", trimmed)) {
            throw new IllegalArgumentException("이미 같은 이름의 오픈채팅방이 있습니다.");
        }
        try {
            ChatRoom room = chatRoomRepository.save(new ChatRoom(trimmed, type, teamUrl, creatorId));
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
}
