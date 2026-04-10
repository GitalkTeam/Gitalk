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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchShareCodec {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern PAYLOAD_PATTERN =
            Pattern.compile("\\s*\\[SEARCHSHARE([A-Za-z0-9+/=]+)]\\s*$");

    private SearchShareCodec() {
    }

    public static String appendPayload(String visibleMessage, String shareId, SearchSession session) {
        ShareEnvelope envelope = new ShareEnvelope();
        envelope.shareId = shareId;
        envelope.session = toPayload(session);

        String encoded = Base64.getEncoder()
                .encodeToString(GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8));

        return visibleMessage + " [SEARCHSHARE" + encoded + "]";
    }

    public static ParsedSharedMessage parseMessage(String content) {
        if (content == null) {
            return new ParsedSharedMessage("", null, null);
        }

        Matcher matcher = PAYLOAD_PATTERN.matcher(content);
        if (!matcher.find()) {
            return new ParsedSharedMessage(content, null, null);
        }

        String visibleMessage = content.substring(0, matcher.start()).trim();
        String encoded = matcher.group(1);

        try {
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            ShareEnvelope envelope = GSON.fromJson(json, ShareEnvelope.class);
            if (envelope == null || envelope.shareId == null || envelope.session == null) {
                return new ParsedSharedMessage(visibleMessage, null, null);
            }
            return new ParsedSharedMessage(
                    visibleMessage,
                    envelope.shareId,
                    fromPayload(envelope.session)
            );
        } catch (Exception e) {
            return new ParsedSharedMessage(visibleMessage, null, null);
        }
    }

    public static final class ParsedSharedMessage {
        private final String visibleMessage;
        private final String shareId;
        private final SearchSession session;

        public ParsedSharedMessage(String visibleMessage, String shareId, SearchSession session) {
            this.visibleMessage = visibleMessage;
            this.shareId = shareId;
            this.session = session;
        }

        public String getVisibleMessage() {
            return visibleMessage;
        }

        public String getShareId() {
            return shareId;
        }

        public SearchSession getSession() {
            return session;
        }

        public boolean hasSharedSession() {
            return shareId != null && session != null;
        }
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
        if (payload.contextResults != null) {
            for (ContextPayload contextPayload : payload.contextResults) {
                contextResults.add(new SearchContextResult(
                        fromPayload(contextPayload.centerMessage),
                        fromPayloadMessages(contextPayload.beforeMessages),
                        fromPayloadMessages(contextPayload.afterMessages)
                ));
            }
        }

        LocalDateTime searchedAt = payload.searchedAt != null
                ? LocalDateTime.parse(payload.searchedAt)
                : LocalDateTime.now();

        return new SearchSession(
                payload.userId,
                payload.roomId,
                payload.keyword,
                contextResults,
                searchedAt,
                payload.searchedInsideRoom
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

    private static final class ShareEnvelope {
        private String shareId;
        private SessionPayload session;
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
