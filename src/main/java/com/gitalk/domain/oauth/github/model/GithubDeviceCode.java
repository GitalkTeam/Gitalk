package com.gitalk.domain.oauth.github.model;

/**
 * GithubDeviceCode Description : GitHub Device Flow 인증에 필요한 코드와 polling 정보를 담는 모델입니다.
 * NOTE : model 계층 DTO이며, GitHub 기기 인증 시작 단계의 응답 데이터를 표현합니다.
 *
 * @author jki
 * @since 04-08 (수) 오후 4:56
 */
public class GithubDeviceCode {
    private final String deviceCode;
    private final String userCode;
    private final String verificationUri;
    private final int interval;
    private final int expiresIn;

    public GithubDeviceCode(String deviceCode, String userCode, String verificationUri, int interval, int expiresIn) {
        this.deviceCode = deviceCode;
        this.userCode = userCode;
        this.verificationUri = verificationUri;
        this.interval = interval;
        this.expiresIn = expiresIn;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public String getUserCode() {
        return userCode;
    }

    public String getVerificationUri() {
        return verificationUri;
    }

    public int getInterval() {
        return interval;
    }

    public int getExpiresIn() {
        return expiresIn;
    }
}
