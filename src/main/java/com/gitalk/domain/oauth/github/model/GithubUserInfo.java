package com.gitalk.domain.oauth.github.model;

/**
 * GithubUserInfo Description :
 * NOTE :
 *
 * @author jki
 * @since 04-08 (수) 오후 4:56
 */
public class GithubUserInfo {
    private final Long id;
    private final String login;
    private final String email;
    private final String avatarUrl;

    public GithubUserInfo(Long id, String login, String email, String avatarUrl) {
        this.id = id;
        this.login = login;
        this.email = email;
        this.avatarUrl = avatarUrl;
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }
}