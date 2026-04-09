package com.gitalk.domain.oauth.github.model;

/**
 * GithubUserInfo Description : 인증된 GitHub 사용자 프로필 정보를 담는 모델입니다.
 * NOTE : model 계층 DTO이며, 기존 계정 연동이나 GitHub 회원 등록 과정에서 사용됩니다.
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
