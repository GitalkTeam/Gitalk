package com.gitalk.domain.user.model;

/**
 * users Description :
 * NOTE :
 *
 * @author jki
 * @since 04-07 (화) 오후 2:39
 */
public class Users {
    private Long userid;
    private String email;
    private String password;
    private String nickname;
    private String profileUrl;
    private String type;

    // getter / setter

    public Users(Long userid, String email, String password, String nickname, String profileUrl, String type) {
        this.userid = userid;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileUrl = profileUrl;
        this.type = type;
    }

    // 기본 생성자 (외부에서 직접 생성 방지)
    private Users(Builder builder) {
        this.userid = builder.userid;
        this.email = builder.email;
        this.password = builder.password;
        this.nickname = builder.nickname;
        this.profileUrl = builder.profileUrl;
        this.type = builder.type;
    }

    // Getter
    public Long getUserid() { return userid; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }
    public String getProfileUrl() { return profileUrl; }
    public String getType() { return type; }

    // Builder
    public static class Builder {
        private Long userid;
        private String email;
        private String password;
        private String nickname;
        private String profileUrl;
        private String type;

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder profileUrl(String profileUrl) {
            this.profileUrl = profileUrl;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder userid(Long userid) {
            this.userid = userid;
            return this;
        }

        public Users build() {
            if (email == null || password == null) {
                throw new IllegalStateException("email과 password는 필수입니다.");
            }
            return new Users(this);
        }
    }
}