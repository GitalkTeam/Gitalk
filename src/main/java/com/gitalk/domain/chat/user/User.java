package com.gitalk.chat.user;

/**
 * 유저 도메인 객체 (DAO)
 * 유저 정보를 담는 불변 객체
 */
public class User {

    private final String id;
    private final String username;
    private final String password;
    private final String nickname;

    public User(String id, String username, String password, String nickname) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.nickname = nickname;
    }

    public String getId()       { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }

    @Override
    public String toString() {
        return "User{id='" + id + "', username='" + username + "', nickname='" + nickname + "'}";
    }
}
