package com.gitalk.domain.user.model;

public class Users {
    private Long userid;
    private Long githubId;
    private String email;
    private String password;
    private String nickname;
    private String profileUrl;
    private String type;
    private String authAccessToken;

    private Users(Builder builder) {
        this.userid = builder.userid;
        this.githubId = builder.githubId;
        this.email = builder.email;
        this.password = builder.password;
        this.nickname = builder.nickname;
        this.profileUrl = builder.profileUrl;
        this.type = builder.type;
        this.authAccessToken = builder.authAccessToken;
    }

    public Long getUserId() { return userid; }
    public Long getGithubId() { return githubId; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
    public String getProfileUrl() { return profileUrl; }
    public String getType() { return type; }
    public String getAuthAccessToken() { return authAccessToken; }

    public static class Builder {
        private Long userid;
        private Long githubId;
        private String email;
        private String password;
        private String nickname;
        private String profileUrl;
        private String type;
        private String authAccessToken;

        public Builder userid(Long userid) { this.userid = userid; return this; }
        public Builder githubId(Long githubId) { this.githubId = githubId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder password(String password) { this.password = password; return this; }
        public Builder nickname(String nickname) { this.nickname = nickname; return this; }
        public Builder profileUrl(String profileUrl) { this.profileUrl = profileUrl; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder authAccessToken(String authAccessToken) { this.authAccessToken = authAccessToken; return this; }

        public Users build() {
            if (email == null || email.isBlank()) {
                throw new IllegalStateException("email은 필수입니다.");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalStateException("type은 필수입니다.");
            }
            return new Users(this);
        }
    }
}