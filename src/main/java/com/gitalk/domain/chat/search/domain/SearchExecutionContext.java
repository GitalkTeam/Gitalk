package com.gitalk.domain.chat.search.domain;

/**
 * SearchExecutionContext Description : 검색 실행 시 필요한 사용자 정보와 현재 방 상태를 담는 컨텍스트 모델입니다.
 * NOTE : search domain DTO이며, 대상 방 결정과 검색 권한 검증에 필요한 문맥 정보를 제공합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 5:40
 */
public class SearchExecutionContext {

    private final Long userId;
    private final String nickname;
    private final Long currentRoomId;
    private final boolean joinedCurrentRoom;

    public SearchExecutionContext(Long userId, String nickname, Long currentRoomId, boolean joinedCurrentRoom) {
        this.userId = userId;
        this.nickname = nickname;
        this.currentRoomId = currentRoomId;
        this.joinedCurrentRoom = joinedCurrentRoom;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public Long getCurrentRoomId() {
        return currentRoomId;
    }

    public boolean isJoinedCurrentRoom() {
        return joinedCurrentRoom;
    }
}
