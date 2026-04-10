package com.gitalk.domain.chat.repository;

import com.gitalk.domain.chat.domain.ChatRoom;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository {
    ChatRoom save(ChatRoom room);
    Optional<ChatRoom> findById(Long roomId);
    List<ChatRoom> findAll();
    List<ChatRoom> findByType(String type);
    boolean existsByTypeAndName(String type, String name);
    void deleteById(Long roomId);

    /** 멤버수 desc 로 정렬한 OPEN 방 인기 목록 */
    List<ChatRoom> findPopularOpenRooms(int limit);

    /** 이름 부분 일치(대소문자 무시) 검색, OPEN 방 한정 */
    List<ChatRoom> searchOpenRoomsByName(String keyword, int limit);

    /** repo 연결: team_url + webhook_secret + webhook_id 한 번에 갱신. webhook_id null 가능. */
    void updateRepoLink(Long roomId, String teamUrl, String webhookSecret, Long webhookId);
}
