package com.gitalk.domain.oauth.github.exception;

/**
 * GithubAuthorizationPendingException Description :
 * NOTE :
 *
 * @author jki
 * @since 04-08 (수) 오후 5:41
 */
public class GithubAuthorizationPendingException extends RuntimeException {
    public GithubAuthorizationPendingException(String message) {
        super(message);
    }
}