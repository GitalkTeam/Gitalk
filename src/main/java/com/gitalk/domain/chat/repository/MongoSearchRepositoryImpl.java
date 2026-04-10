package com.gitalk.domain.chat.repository;

/**
 * MongoSearchRepositoryImpl Description : 메시지 검색, 페이징, 문맥 조회를 MongoDB 기준으로 처리하는 repository 구현체입니다.
 * NOTE : repository 계층의 SearchRepository 구현이며, 정규화된 텍스트와 시간 조건을 이용해 검색과 문맥 조회를 수행합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 3:51
 */
import com.gitalk.domain.chat.config.MongoConnectionManager;
import com.gitalk.domain.chat.domain.ChatMessage;
import com.gitalk.domain.chat.search.domain.SearchContextResult;
import com.gitalk.domain.chat.search.domain.SearchPageResult;
import com.gitalk.domain.chat.exception.ChatRepositoryException;
import com.gitalk.domain.chat.search.util.TextNormalizer;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class MongoSearchRepositoryImpl implements SearchRepository {

    private final MongoCollection<Document> collection;

    public MongoSearchRepositoryImpl(MongoConnectionManager connectionManager) {
        this.collection = connectionManager.getChatMessageCollection();
    }

    @Override
    public SearchPageResult<ChatMessage> searchByRoomId(Long roomId, int page, int pageSize) {
        Bson filter = Filters.eq("roomId", roomId);
        return search(filter, page, pageSize);
    }

    @Override
    public SearchPageResult<ChatMessage> searchByRoomIds(List<Long> roomIds, int page, int pageSize) {
        if (roomIds == null || roomIds.isEmpty()) {
            return new SearchPageResult<>(Collections.emptyList(), page, pageSize, 0);
        }
        Bson filter = Filters.in("roomId", roomIds);
        return search(filter, page, pageSize);
    }

    @Override
    public SearchPageResult<ChatMessage> searchByKeyword(Long roomId, String keyword, int page, int pageSize) {
        String normalizedKeyword = TextNormalizer.normalize(keyword);
        Bson filter = Filters.and(
                Filters.eq("roomId", roomId),
                Filters.regex("normalizedContent", Pattern.quote(normalizedKeyword))
        );
        return search(filter, page, pageSize);
    }

    @Override
    public SearchPageResult<ChatMessage> searchByKeyword(List<Long> roomIds, String keyword, int page, int pageSize) {
        if (roomIds == null || roomIds.isEmpty()) {
            return new SearchPageResult<>(Collections.emptyList(), page, pageSize, 0);
        }

        String normalizedKeyword = TextNormalizer.normalize(keyword);
        Bson filter = Filters.and(
                Filters.in("roomId", roomIds),
                Filters.regex("normalizedContent", Pattern.quote(normalizedKeyword))
        );
        return search(filter, page, pageSize);
    }

    @Override
    public Optional<ChatMessage> findByMessageId(Long messageId) {
        if (messageId == null) {
            throw new ChatRepositoryException("messageId는 필수입니다.");
        }

        try {
            Document document = collection.find(Filters.eq("messageId", messageId)).first();
            return Optional.ofNullable(document).map(this::toChatMessage);
        } catch (Exception e) {
            throw new ChatRepositoryException("messageId 조회 실패", e);
        }
    }

    @Override
    public List<ChatMessage> findContextBefore(Long roomId, LocalDateTime createdAt, int size) {
        int safeSize = validateContextSize(size);

        try {
            Bson filter = Filters.and(
                    Filters.eq("roomId", roomId),
                    Filters.lt("createdAt", toDate(createdAt))
            );

            List<ChatMessage> result = new ArrayList<>();
            FindIterable<Document> documents = collection.find(filter)
                    .sort(Sorts.descending("createdAt"))
                    .limit(safeSize);

            for (Document document : documents) {
                result.add(toChatMessage(document));
            }

            Collections.reverse(result);
            return result;
        } catch (Exception e) {
            throw new ChatRepositoryException("이전 문맥 조회 실패", e);
        }
    }

    @Override
    public List<ChatMessage> findContextAfter(Long roomId, LocalDateTime createdAt, int size) {
        int safeSize = validateContextSize(size);

        try {
            Bson filter = Filters.and(
                    Filters.eq("roomId", roomId),
                    Filters.gt("createdAt", toDate(createdAt))
            );

            List<ChatMessage> result = new ArrayList<>();
            FindIterable<Document> documents = collection.find(filter)
                    .sort(Sorts.ascending("createdAt"))
                    .limit(safeSize);

            for (Document document : documents) {
                result.add(toChatMessage(document));
            }

            return result;
        } catch (Exception e) {
            throw new ChatRepositoryException("이후 문맥 조회 실패", e);
        }
    }

    @Override
    public SearchContextResult findMessageContext(Long messageId, int contextSize) {
        int safeSize = validateContextSize(contextSize);

        ChatMessage centerMessage = findByMessageId(messageId)
                .orElseThrow(() -> new ChatRepositoryException("기준 메시지를 찾을 수 없습니다. messageId=" + messageId));

        List<ChatMessage> beforeMessages =
                findContextBefore(centerMessage.getRoomId(), centerMessage.getCreatedAt(), safeSize);

        List<ChatMessage> afterMessages =
                findContextAfter(centerMessage.getRoomId(), centerMessage.getCreatedAt(), safeSize);

        return new SearchContextResult(centerMessage, beforeMessages, afterMessages);
    }

    private SearchPageResult<ChatMessage> search(Bson filter, int page, int pageSize) {
        validatePage(page, pageSize);

        try {
            long totalCount = collection.countDocuments(filter);
            int skip = (page - 1) * pageSize;

            List<ChatMessage> items = new ArrayList<>();
            FindIterable<Document> documents = collection.find(filter)
                    .sort(Sorts.descending("createdAt"))
                    .skip(skip)
                    .limit(pageSize);

            for (Document document : documents) {
                items.add(toChatMessage(document));
            }

            return new SearchPageResult<>(items, page, pageSize, totalCount);
        } catch (Exception e) {
            throw new ChatRepositoryException("메시지 검색 실패", e);
        }
    }

    private ChatMessage toChatMessage(Document doc) {
        return new ChatMessage(
                doc.getLong("messageId"),
                doc.getLong("roomId"),
                doc.getLong("senderId"),
                doc.getString("senderNickname"),
                doc.getString("content"),
                doc.getString("normalizedContent"),
                toLocalDateTime(doc.getDate("createdAt"))
        );
    }

    private int validateContextSize(int size) {
        if (size < 1 || size > 5) {
            throw new ChatRepositoryException("context size는 1~5 범위여야 합니다.");
        }
        return size;
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new ChatRepositoryException("page는 1 이상이어야 합니다.");
        }
        if (pageSize < 1) {
            throw new ChatRepositoryException("pageSize는 1 이상이어야 합니다.");
        }
    }

    private Date toDate(LocalDateTime localDateTime) {
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
