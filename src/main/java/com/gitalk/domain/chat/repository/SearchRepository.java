package com.gitalk.domain.chat.repository;

/**
 * SearchRepository Description : 메시지 검색과 문맥 조회 기능을 정의하는 repository interface입니다.
 * NOTE : repository 계층 인터페이스이며, service 계층이 저장소 종류에 의존하지 않도록 분리하는 역할을 합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 3:24
 */
import com.gitalk.domain.chat.domain.ChatMessage;
import com.gitalk.domain.chat.search.domain.SearchContextResult;
import com.gitalk.domain.chat.search.domain.SearchPageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SearchRepository {

    SearchPageResult<ChatMessage> searchByRoomId(Long roomId, int page, int pageSize);

    SearchPageResult<ChatMessage> searchByRoomIds(List<Long> roomIds, int page, int pageSize);

    SearchPageResult<ChatMessage> searchByKeyword(Long roomId, String keyword, int page, int pageSize);

    SearchPageResult<ChatMessage> searchByKeyword(List<Long> roomIds, String keyword, int page, int pageSize);

    Optional<ChatMessage> findByMessageId(Long messageId);

    List<ChatMessage> findContextBefore(Long roomId, LocalDateTime createdAt, int size);

    List<ChatMessage> findContextAfter(Long roomId, LocalDateTime createdAt, int size);

    SearchContextResult findMessageContext(Long messageId, int contextSize);
}
