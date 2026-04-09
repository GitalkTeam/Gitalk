package com.gitalk.domain.chat.exception;

/**
 * ChatRepositoryException Description : 채팅 저장소 처리와 영속화 과정에서 발생하는 예외를 표현하는 전용 예외 클래스입니다.
 * NOTE : exception 계층의 런타임 예외이며, MongoDB 및 검색 저장소 관련 오류를 일관된 형태로 감싸는 데 사용합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 3:26
 */
public class ChatRepositoryException extends RuntimeException {

    public ChatRepositoryException(String message) {
        super(message);
    }

    public ChatRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
