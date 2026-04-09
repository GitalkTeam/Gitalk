package com.gitalk.domain.chat.search.util;
import com.gitalk.domain.chat.domain.ChatMessage;
import com.gitalk.domain.chat.search.domain.SearchContextResult;
import com.gitalk.domain.chat.search.domain.SearchSession;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
public final class SearchSessionCodec {
    private static final Gson GSON = new GsonBuilder().create();
    private SearchSessionCodec() {
    }
    public static String encode(SearchSession session) {
        return Base64.getEncoder()
                .encodeToString(GSON.toJson(toPayload(session)).getBytes(StandardCharsets.UTF_8));
    }
    public static SearchSession decode(String encoded) {
        String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        SessionPayload payload = GSON.fromJson(json, SessionPayload.class);
        return fromPayload(payload);
    }
    private static SessionPayload toPayload(SearchSession session) {
        SessionPayload payload = new SessionPayload();
        payload.userId = session.getUserId();
        payload.roomId = session.getRoomId();
        payload.keyword = session.getKeyword();
        payload.searchedAt = session.getSearchedAt() != null ? session.getSearchedAt().toString() : null;
        payload.searchedInsideRoom = session.isSearchedInsideRoom();
        payload.contextResults = new ArrayList<>();
        for (SearchContextResult result : session.getContextResults()) {
            ContextPayload contextPayload = new ContextPayload();
            contextPayload.centerMessage = toPayload(result.getCenterMessage());
            contextPayload.beforeMessages = toPayloadMessages(result.getBeforeMessages());
            contextPayload.afterMessages = toPayloadMessages(result.getAfterMessages());
            payload.contextResults.add(contextPayload);
        }
        return payload;
    }
    private static SearchSession fromPayload(SessionPayload payload) {
        List<SearchContextResult> contextResults = new ArrayList<>();
        if (payload != null && payload.contextResults != null) {
            for (ContextPayload contextPayload : payload.contextResults) {
                contextResults.add(new SearchContextResult(
                        fromPayload(contextPayload.centerMessage),
                        fromPayloadMessages(contextPayload.beforeMessages),
                        fromPayloadMessages(contextPayload.afterMessages)
                ));
            }
        }
        LocalDateTime searchedAt = payload != null && payload.searchedAt != null
                ? LocalDateTime.parse(payload.searchedAt)
                : LocalDateTime.now();
        return new SearchSession(
                payload != null ? payload.userId : null,
                payload != null ? payload.roomId : null,
                payload != null ? payload.keyword : "",
                contextResults,
                searchedAt,
                payload != null && payload.searchedInsideRoom
        );
    }
    private static List<MessagePayload> toPayloadMessages(List<ChatMessage> messages) {
        List<MessagePayload> payloads = new ArrayList<>();
        if (messages == null) {
            return payloads;
        }
        for (ChatMessage message : messages) {
            payloads.add(toPayload(message));
        }
        return payloads;
    }
    private static List<ChatMessage> fromPayloadMessages(List<MessagePayload> payloads) {
        List<ChatMessage> messages = new ArrayList<>();
        if (payloads == null) {
            return messages;
        }
        for (MessagePayload payload : payloads) {
            messages.add(fromPayload(payload));
        }
        return messages;
    }
    private static MessagePayload toPayload(ChatMessage message) {
        if (message == null) {
            return null;
        }
        MessagePayload payload = new MessagePayload();
        payload.messageId = message.getMessageId();
        payload.roomId = message.getRoomId();
        payload.senderId = message.getSenderId();
        payload.senderNickname = message.getSenderNickname();
        payload.content = message.getContent();
        payload.normalizedContent = message.getNormalizedContent();
        payload.createdAt = message.getCreatedAt() != null ? message.getCreatedAt().toString() : null;
        return payload;
    }
    private static ChatMessage fromPayload(MessagePayload payload) {
        if (payload == null) {
            return null;
        }
        LocalDateTime createdAt = payload.createdAt != null
                ? LocalDateTime.parse(payload.createdAt)
                : LocalDateTime.now();
        return new ChatMessage(
                payload.messageId,
                payload.roomId,
                payload.senderId,
                payload.senderNickname,
                payload.content,
                payload.normalizedContent,
                createdAt
        );
    }
    private static final class SessionPayload {
        private Long userId;
        private Long roomId;
        private String keyword;
        private List<ContextPayload> contextResults;
        private String searchedAt;
        private boolean searchedInsideRoom;
    }
    private static final class ContextPayload {
        private MessagePayload centerMessage;
        private List<MessagePayload> beforeMessages;
        private List<MessagePayload> afterMessages;
    }
    private static final class MessagePayload {
        private Long messageId;
        private Long roomId;
        private Long senderId;
        private String senderNickname;
        private String content;
        private String normalizedContent;
        private String createdAt;
    }
}
