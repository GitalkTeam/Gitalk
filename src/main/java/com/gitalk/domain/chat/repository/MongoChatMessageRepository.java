package com.gitalk.domain.chat.repository;

/**
 * MongoChatMessageRepository Description : 채팅방 메시지를 MongoDB에 저장하고 조회하는 repository 구현체입니다.
 * NOTE : repository 계층의 기본 MessageRepository 구현이며, 검색 성능을 위한 인덱스 생성도 함께 담당합니다.
 * @author jki
 * @since  04-09 (목) 오후 4:20
 */
import com.gitalk.domain.chat.config.MongoConnectionManager;
import com.gitalk.domain.chat.domain.ChatMessage;
import com.gitalk.domain.chat.domain.Message;
import com.gitalk.domain.chat.exception.ChatRepositoryException;
import com.gitalk.domain.chat.search.util.TextNormalizer;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Primary message repository used by the application runtime.
 */
public class MongoChatMessageRepository implements MessageRepository {

    private static final AtomicLong MESSAGE_ID_SEQUENCE =
            new AtomicLong(System.currentTimeMillis());

    private final MongoCollection<Document> collection;

    public MongoChatMessageRepository(MongoConnectionManager connectionManager) {
        this.collection = connectionManager.getChatMessageCollection();
    }

    public void createIndexes() {
        try {
            collection.createIndex(
                    Indexes.ascending("messageId"),
                    new IndexOptions().unique(true)
            );

            collection.createIndex(
                    Indexes.compoundIndex(
                            Indexes.ascending("roomId"),
                            Indexes.descending("createdAt")
                    )
            );

            collection.createIndex(
                    Indexes.compoundIndex(
                            Indexes.ascending("roomId"),
                            Indexes.ascending("normalizedContent"),
                            Indexes.descending("createdAt")
                    )
            );
        } catch (Exception e) {
            throw new ChatRepositoryException("Mongo 인덱스 생성 실패", e);
        }
    }

    @Override
    public void save(Message message) {
        validate(message);

        try {
            ChatMessage chatMessage = toChatMessage(message);

            Document document = new Document()
                    .append("messageId", chatMessage.getMessageId())
                    .append("roomId", chatMessage.getRoomId())
                    .append("senderId", chatMessage.getSenderId())
                    .append("senderNickname", chatMessage.getSenderNickname())
                    .append("content", chatMessage.getContent())
                    .append("normalizedContent", chatMessage.getNormalizedContent())
                    .append("createdAt", toDate(chatMessage.getCreatedAt()));

            collection.insertOne(document);
        } catch (Exception e) {
            throw new ChatRepositoryException("Mongo 메시지 저장 실패", e);
        }
    }

    @Override
    public List<Message> findByRoomId(Long roomId) {
        return findByRoomId(roomId, Integer.MAX_VALUE);
    }

    @Override
    public List<Message> findByRoomId(Long roomId, int limit) {
        if (roomId == null) {
            throw new ChatRepositoryException("roomId는 필수입니다.");
        }
        if (limit < 1) {
            throw new ChatRepositoryException("limit는 1 이상이어야 합니다.");
        }

        try {
            List<Message> result = new ArrayList<>();

            for (Document doc : collection.find(Filters.eq("roomId", roomId))
                    .sort(Sorts.descending("createdAt"))
                    .limit(limit)) {

                result.add(toMessage(doc));
            }

            return result;
        } catch (Exception e) {
            throw new ChatRepositoryException("Mongo 메시지 조회 실패", e);
        }
    }

    @Override
    public List<Message> findByRoomIdSince(Long roomId, LocalDateTime since, int limit) {
        if (roomId == null) {
            throw new ChatRepositoryException("roomId는 필수입니다.");
        }
        if (since == null) {
            return Collections.emptyList();
        }
        if (limit < 1) {
            throw new ChatRepositoryException("limit는 1 이상이어야 합니다.");
        }

        try {
            Date sinceDate = Date.from(since.atZone(ZoneId.systemDefault()).toInstant());
            List<Message> result = new ArrayList<>();

            // since 보다 나중에 생긴 메시지, 최신 limit 개를 가져온 뒤 시간 오름차순으로 뒤집어 반환.
            // (오래된 limit 개가 아니라 최신 limit 개를 잘라야 사용자가 가장 최근 미독을 본다)
            List<Message> recent = new ArrayList<>();
            for (Document doc : collection.find(
                    Filters.and(
                            Filters.eq("roomId", roomId),
                            Filters.gt("createdAt", sinceDate)
                    ))
                    .sort(Sorts.descending("createdAt"))
                    .limit(limit)) {

                recent.add(toMessage(doc));
            }
            // recent 는 desc, 화면에 chronological(asc) 로 보여야 하니 뒤집기
            for (int i = recent.size() - 1; i >= 0; i--) {
                result.add(recent.get(i));
            }
            return result;
        } catch (Exception e) {
            throw new ChatRepositoryException("Mongo 미독 메시지 조회 실패", e);
        }
    }

    private ChatMessage toChatMessage(Message message) {
        return new ChatMessage(
                generateMessageId(),
                message.getRoomId(),
                message.getUserId(),
                message.getSenderNickname(),
                message.getContent(),
                TextNormalizer.normalize(message.getContent()),
                message.getCreatedAt()
        );
    }

    private Message toMessage(Document doc) {
        return new Message(
                doc.getLong("messageId"),
                doc.getLong("senderId"),
                doc.getLong("roomId"),
                doc.getString("content"),
                doc.getString("senderNickname"),
                toLocalDateTime(doc.getDate("createdAt"))
        );
    }

    private Long generateMessageId() {
        return MESSAGE_ID_SEQUENCE.incrementAndGet();
    }

    private void validate(Message message) {
        if (message == null) {
            throw new ChatRepositoryException("message는 null일 수 없습니다.");
        }
        if (message.getUserId() == null) {
            throw new ChatRepositoryException("userId는 필수입니다.");
        }
        if (message.getRoomId() == null) {
            throw new ChatRepositoryException("roomId는 필수입니다.");
        }
        if (message.getSenderNickname() == null || message.getSenderNickname().isBlank()) {
            throw new ChatRepositoryException("senderNickname은 필수입니다.");
        }
        if (message.getContent() == null || message.getContent().isBlank()) {
            throw new ChatRepositoryException("content는 비어 있을 수 없습니다.");
        }
        if (message.getCreatedAt() == null) {
            throw new ChatRepositoryException("createdAt은 필수입니다.");
        }
    }

    private Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
