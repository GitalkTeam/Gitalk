package com.gitalk.domain.chat.search.domain;

/**
 * SearchContextResult Description : 검색에 일치한 메시지와 그 전후 문맥 메시지를 함께 묶어 표현하는 domain 결과 객체입니다.
 * NOTE : domain 계층 DTO이며, 검색 결과 화면에서 앞뒤 문맥 블록을 구성할 때 사용합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 3:24
 */
import com.gitalk.domain.chat.domain.ChatMessage;

import java.util.Collections;
import java.util.List;

public class SearchContextResult {

    private final ChatMessage centerMessage;
    private final List<ChatMessage> beforeMessages;
    private final List<ChatMessage> afterMessages;

    public SearchContextResult(ChatMessage centerMessage,
                               List<ChatMessage> beforeMessages,
                               List<ChatMessage> afterMessages) {
        this.centerMessage = centerMessage;
        this.beforeMessages = beforeMessages == null ? Collections.emptyList() : beforeMessages;
        this.afterMessages = afterMessages == null ? Collections.emptyList() : afterMessages;
    }

    public ChatMessage getCenterMessage() {
        return centerMessage;
    }

    public List<ChatMessage> getBeforeMessages() {
        return beforeMessages;
    }

    public List<ChatMessage> getAfterMessages() {
        return afterMessages;
    }
}
