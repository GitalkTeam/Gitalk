package com.gitalk.domain.chat.config;

/**
 * MongoConnectionManager Description : 채팅 기능에서 공용으로 사용하는 MongoDB 연결과 컬렉션을 초기화하고 제공하는 설정 컴포넌트입니다.
 * NOTE : config 계층의 싱글톤 클래스이며, repository 계층이 데이터베이스와 메시지 컬렉션을 가져올 때 사용합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 3:27
 */
import com.gitalk.domain.chat.exception.ChatRepositoryException;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public class MongoConnectionManager {

    private static MongoConnectionManager instance;

    private final MongoClient mongoClient;
    private final MongoDatabase database;
    private final String collectionName;

    private MongoConnectionManager() {
        try {
            String uri = getRequired("mongodb.uri");
            String databaseName = getRequired("mongodb.database");
            this.collectionName = getRequired("mongodb.collection");

            ConnectionString connectionString = new ConnectionString(uri);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .build();

            this.mongoClient = MongoClients.create(settings);
            this.database = mongoClient.getDatabase(databaseName);

        } catch (Exception e) {
            throw new ChatRepositoryException("MongoDB 연결 초기화 실패", e);
        }
    }

    private String getRequired(String key) {
        String value = com.gitalk.common.util.AppConfig.get(key);

        if (value == null || value.isBlank()) {
            throw new ChatRepositoryException("필수 설정값이 없습니다. key=" + key);
        }

        return value.trim();
    }

    public static synchronized MongoConnectionManager getInstance() {
        if (instance == null) {
            instance = new MongoConnectionManager();
        }
        return instance;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getChatMessageCollection() {
        return database.getCollection(collectionName);
    }

    public void close() {
        mongoClient.close();
    }
}
