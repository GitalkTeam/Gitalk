package com.gitalk.domain.chat.search.domain;

/**
 * SearchCommand Description : /search 명령에서 해석된 옵션과 플래그를 담는 검색 명령 모델입니다.
 * NOTE : search domain 요청 객체이며, SearchCommandParser가 생성하고 ChatSearchService가 사용합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 5:39
 */
public class SearchCommand {

    private final Long roomId;
    private final String keyword;
    private final boolean help;
    private final boolean share;
    private final boolean view;
    private final String shareId;

    public SearchCommand(Long roomId,
                         String keyword,
                         boolean help,
                         boolean share,
                         boolean view,
                         String shareId) {
        this.roomId = roomId;
        this.keyword = keyword;
        this.help = help;
        this.share = share;
        this.view = view;
        this.shareId = shareId;
    }

    public Long getRoomId() {
        return roomId;
    }

    public String getKeyword() {
        return keyword;
    }

    public boolean isHelp() {
        return help;
    }

    public boolean isShare() {
        return share;
    }

    public boolean isView() {
        return view;
    }

    public String getShareId() {
        return shareId;
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
