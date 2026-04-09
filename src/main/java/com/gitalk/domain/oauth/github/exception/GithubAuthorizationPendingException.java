package com.gitalk.domain.oauth.github.exception;

/**
 * GithubAuthorizationPendingException Description : GitHub Device Flow 인증이 아직 완료되지 않았음을 나타내는 예외 클래스입니다.
 * NOTE : oauth exception 계층 예외이며, 재시도 가능한 대기 상태를 일반 실패와 구분할 때 사용합니다.
 *
 * @author jki
 * @since 04-08 (수) 오후 5:41
 */
public class GithubAuthorizationPendingException extends RuntimeException {
    public GithubAuthorizationPendingException(String message) {
        super(message);
    }
}
