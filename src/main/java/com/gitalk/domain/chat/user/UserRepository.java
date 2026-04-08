package com.gitalk.chat.user;

import java.util.Optional;

/**
 * 유저 저장소 인터페이스
 * 나중에 InMemory → DB(AWS RDS) 구현체로 교체 가능
 */
public interface UserRepository {

    Optional<User> findByUsername(String username);

    void save(User user);
}
