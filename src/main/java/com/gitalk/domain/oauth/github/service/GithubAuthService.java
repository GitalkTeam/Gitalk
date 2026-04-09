package com.gitalk.domain.oauth.github.service;
import com.gitalk.domain.oauth.github.model.GithubDeviceCode;
import com.gitalk.domain.oauth.github.model.GithubUserInfo;
import com.gitalk.domain.user.model.Users;
import com.gitalk.domain.user.repository.UserRepository;

/**
 * GithubAuthService Description :
 * NOTE :
 *
 * @author jki
 * @since 04-08 (수) 오후 4:57
 */
public class GithubAuthService {

    private final UserRepository userRepository;
    private final GithubOauthClient githubOauthClient;

    public GithubAuthService(UserRepository userRepository, GithubOauthClient githubOauthClient) {
        this.userRepository = userRepository;
        this.githubOauthClient = githubOauthClient;
    }

    public GithubDeviceCode requestDeviceCode() {
        return githubOauthClient.requestDeviceCode();
    }

    public String requestAccessTokenOnce(GithubDeviceCode deviceCode) {
        return githubOauthClient.requestAccessTokenOnce(deviceCode);
    }

    public Users loginOrRegisterByAccessToken(String accessToken) {
        GithubUserInfo githubUser = githubOauthClient.fetchGithubUser(accessToken);

        return userRepository.findByGithubId(githubUser.getId())
                .orElseGet(() -> registerGithubUser(githubUser, accessToken));
    }

    private Users registerGithubUser(GithubUserInfo githubUser, String accessToken) {
        if (userRepository.existsByEmail(githubUser.getEmail())) {
            throw new RuntimeException("이미 같은 이메일의 계정이 존재합니다.");
        }

        Users user = new Users.Builder()
                .githubId(githubUser.getId())
                .email(githubUser.getEmail())
                .nickname(githubUser.getLogin())
                .profileUrl(githubUser.getAvatarUrl())
                .type("GITHUB")
                .authAccessToken(accessToken)
                .build();

        userRepository.save(user);

        return userRepository.findByGithubId(githubUser.getId())
                .orElseThrow(() -> new RuntimeException("GitHub 회원 저장 후 조회 실패"));
    }
}