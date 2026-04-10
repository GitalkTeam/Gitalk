package com.gitalk.domain.chat.search.domain;

/**
 * SearchPageResult Description : 채팅 메시지 검색 결과를 페이지 단위로 전달하기 위한 공통 domain 페이징 객체입니다.
 * NOTE : domain 계층 DTO이며, 조회 항목 목록과 페이지 정보, 다음 페이지 존재 여부를 함께 담습니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 3:24
 */

import java.util.Collections;
import java.util.List;

public class SearchPageResult<T> {

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final long totalCount;
    private final boolean hasNext;

    public SearchPageResult(List<T> items, int page, int pageSize, long totalCount) {
        this.items = items == null ? Collections.emptyList() : items;
        this.page = page;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.hasNext = ((long) page * pageSize) < totalCount;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public boolean isHasNext() {
        return hasNext;
    }
}
