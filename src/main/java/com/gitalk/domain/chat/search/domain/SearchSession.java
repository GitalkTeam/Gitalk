package com.gitalk.domain.chat.search.domain;

/**
 * SearchSession Description : 검색 메타데이터와 문맥이 포함된 결과를 함께 담는 검색 세션 모델입니다.
 * NOTE : search domain 상태 객체이며, 사용자별 최근 검색 결과를 보관하고 공유/조회 흐름에서 재사용됩니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 5:40
 */

import java.time.LocalDateTime;
import java.util.List;

public class SearchSession {

    private final Long userId;
    private final Long roomId;
    private final String keyword;
    private final List<SearchContextResult> contextResults;
    private final LocalDateTime searchedAt;
    private final boolean searchedInsideRoom;

    public SearchSession(Long userId,
                         Long roomId,
                         String keyword,
                         List<SearchContextResult> contextResults,
                         LocalDateTime searchedAt,
                         boolean searchedInsideRoom) {
        this.userId = userId;
        this.roomId = roomId;
        this.keyword = keyword;
        this.contextResults = contextResults;
        this.searchedAt = searchedAt;
        this.searchedInsideRoom = searchedInsideRoom;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public String getKeyword() {
        return keyword;
    }

    public List<SearchContextResult> getContextResults() {
        return contextResults;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }

    public boolean isSearchedInsideRoom() {
        return searchedInsideRoom;
    }
}
