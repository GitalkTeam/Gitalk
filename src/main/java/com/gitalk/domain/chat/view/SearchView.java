package com.gitalk.domain.chat.view;

/**
 * SearchView Description : 검색 결과와 명령 도움말을 콘솔 출력용 문자열로 렌더링하는 view 클래스입니다.
 * NOTE : view 계층 포맷터이며, SearchSession 데이터를 사용자에게 보여줄 형태로 가공합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 3:25
 */

import com.gitalk.domain.chat.domain.ChatMessage;
import com.gitalk.domain.chat.search.domain.SearchContextResult;
import com.gitalk.domain.chat.search.domain.SearchSession;

import java.util.List;

public class SearchView {

    public String render(SearchSession session) {
        StringBuilder sb = new StringBuilder();

        sb.append("========== SEARCH RESULT ==========\n");
        sb.append("roomId: ").append(session.getRoomId()).append("\n");
        sb.append("keyword: ").append(session.getKeyword()).append("\n");
        sb.append("searchedAt: ").append(session.getSearchedAt()).append("\n");
        sb.append("\n");

        List<SearchContextResult> results = session.getContextResults();
        if (results.isEmpty()) {
            sb.append("검색 결과가 없습니다.\n");
            sb.append("==================================\n");
            return sb.toString();
        }

        for (int i = 0; i < results.size(); i++) {
            SearchContextResult block = results.get(i);
            sb.append("----- result #").append(i + 1).append(" -----\n");

            for (ChatMessage before : block.getBeforeMessages()) {
                sb.append(format(before)).append("\n");
            }

            sb.append("[MATCH] ").append(format(block.getCenterMessage())).append("\n");

            for (ChatMessage after : block.getAfterMessages()) {
                sb.append(format(after)).append("\n");
            }

            sb.append("\n");
        }

        sb.append("==================================\n");
        return sb.toString();
    }

    public String helpText() {
        return String.join("\n",
                "/search -h",
                "/search --help",
                "/search [keyword]",
                "/search -r [roomNo] [keyword]",
                "/search -s",
                "/search --share",
                "/search -v [shareId]",
                "/search --view [shareId]"
        );
    }

    private String format(ChatMessage message) {
        return String.format(
                "[%s] %s: %s (id=%d)",
                message.getCreatedAt(),
                message.getSenderNickname(),
                message.getContent(),
                message.getMessageId()
        );
    }
}
