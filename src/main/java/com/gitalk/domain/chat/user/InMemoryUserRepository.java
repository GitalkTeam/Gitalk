package com.gitalk.chat.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 인메모리 유저 저장소 구현체 (로컬 테스트용)
 * 나중에 DB 연동 시 DatabaseUserRepository로 교체
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new HashMap<>();

    public InMemoryUserRepository() {
        // 테스트용 계정 등록
        save(new User("1", "alice",   "1234", "Alice"));
        save(new User("2", "bob",     "1234", "Bob"));
        save(new User("3", "charlie", "1234", "Charlie"));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }

    @Override
    public void save(User user) {
        store.put(user.getUsername(), user);
    }
}
