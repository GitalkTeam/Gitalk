package com.gitalk.domain.chat.search.service;

/**
 * ChatSearchService Description : 검색 명령을 검증하고 검색 세션 결과를 조합하는 채팅 검색 서비스입니다.
 * NOTE : search/service 성격의 클래스이며, 방 접근 검증과 repository 조회, 문맥 결과 조합을 함께 조율합니다.
 * 채팅 명령/권한/세션 조합까지 포함한 유스케이스 서비스
 * @author jki
 * @since 04-09 (목) 오후 5:41
 */
import com.gitalk.domain.chat.domain.ChatMessage;
import com.gitalk.domain.chat.domain.ChatRoom;
import com.gitalk.domain.chat.search.domain.SearchContextResult;
import com.gitalk.domain.chat.search.domain.SearchPageResult;
import com.gitalk.domain.chat.repository.SearchRepository;
import com.gitalk.domain.chat.search.domain.SearchExecutionContext;
import com.gitalk.domain.chat.search.domain.SearchSession;
import com.gitalk.domain.chat.search.domain.SearchCommand;
import com.gitalk.domain.chat.service.ChatRoomService;
import com.gitalk.domain.chat.service.ChatService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ChatSearchService {

    private final SearchRepository searchRepository;
    private final ChatService chatService;
    private final ChatRoomService chatRoomService;

    public ChatSearchService(SearchRepository searchRepository,
                             ChatService chatService,
                             ChatRoomService chatRoomService) {
        this.searchRepository = searchRepository;
        this.chatService = chatService;
        this.chatRoomService = chatRoomService;
    }

    public SearchSession search(SearchCommand command, SearchExecutionContext context) {
        validateSearchPermission(command, context);

        Long targetRoomId = resolveTargetRoomId(command, context);
        boolean insideRoomSearch = context.isJoinedCurrentRoom() && targetRoomId.equals(context.getCurrentRoomId());

        SearchPageResult<ChatMessage> pageResult =
                searchRepository.searchByKeyword(targetRoomId, command.getKeyword(), 1, 20);

        List<SearchContextResult> contextBlocks = new ArrayList<>();
        for (ChatMessage message : pageResult.getItems()) {
            contextBlocks.add(searchRepository.findMessageContext(message.getMessageId(), 3));
        }

        return new SearchSession(
                context.getUserId(),
                targetRoomId,
                command.getKeyword(),
                contextBlocks,
                LocalDateTime.now(),
                insideRoomSearch
        );
    }

    private void validateSearchPermission(SearchCommand command, SearchExecutionContext context) {
        if (command.isShare()) {
            return;
        }

        if (!command.hasKeyword()) {
            throw new IllegalArgumentException("검색어를 입력하세요.");
        }

        if (command.getRoomId() == null && !context.isJoinedCurrentRoom()) {
            throw new IllegalArgumentException("채팅방 밖에서는 /search -r [roomNo] [keyword] 형식만 가능합니다.");
        }

        if (command.getRoomId() != null) {
            resolveRequestedRoomId(command.getRoomId(), context.getUserId());
            return;
        }

        if (!context.isJoinedCurrentRoom()) {
            throw new IllegalArgumentException("현재 참여 중인 방이 없습니다.");
        }
    }

    private Long resolveTargetRoomId(SearchCommand command, SearchExecutionContext context) {
        if (command.getRoomId() != null) {
            return resolveRequestedRoomId(command.getRoomId(), context.getUserId());
        }
        return context.getCurrentRoomId();
    }

    private Long resolveRequestedRoomId(Long roomNumber, Long userId) {
        List<ChatRoom> myRooms = chatRoomService.getMyRooms(userId);
        int index = Math.toIntExact(roomNumber) - 1;
        if (index < 0 || index >= myRooms.size()) {
            throw new IllegalArgumentException("Invalid room number.");
        }
        return myRooms.get(index).getRoomId();
    }
}
