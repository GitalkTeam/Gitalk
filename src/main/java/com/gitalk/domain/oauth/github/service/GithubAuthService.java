package com.gitalk.domain.oauth.github.service;
import com.gitalk.domain.oauth.github.model.GithubDeviceCode;
import com.gitalk.domain.oauth.github.model.GithubUserInfo;
import com.gitalk.domain.user.model.Users;
import com.gitalk.domain.user.repository.UserRepository;

import java.util.Optional;

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

        // 1. 이미 GitHub 연동된 사용자 → 즉시 로그인
        Optional<Users> byGithubId = userRepository.findByGithubId(githubUser.getId());
        if (byGithubId.isPresent()) {
            return byGithubId.get();
        }

        // 2. 같은 이메일의 로컬 계정이 존재 → 자동 연동 후 로그인
        Optional<Users> byEmail = userRepository.findByEmail(githubUser.getEmail());
        if (byEmail.isPresent()) {
            return linkGithubToExisting(byEmail.get(), githubUser, accessToken);
        }

        // 3. 완전 신규 → GitHub 회원으로 등록
        return registerGithubUser(githubUser, accessToken);
    }

    private Users linkGithubToExisting(Users existing, GithubUserInfo githubUser, String accessToken) {
        userRepository.linkGithub(
                existing.getUserId(),
                githubUser.getId(),
                accessToken,
                githubUser.getAvatarUrl()
        );
        return userRepository.findByGithubId(githubUser.getId())
                .orElseThrow(() -> new RuntimeException("GitHub 연동 후 조회 실패"));
    }

    private Users registerGithubUser(GithubUserInfo githubUser, String accessToken) {
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