package com.gitalk.domain.user.service;

import com.gitalk.domain.user.model.Users;
import com.gitalk.domain.user.repository.UserRepository;

import java.security.MessageDigest;
import java.util.Optional;

/**
 * UserService Description : 일반 회원 가입, 비밀번호 암호화, 로그인 검증을 처리하는 사용자 서비스입니다.
 * NOTE : service 계층 클래스이며, LOCAL 계정 정책을 관리하고 실제 저장은 UserRepository에 위임합니다.
 *
 * @author jki
 * @since 04-07 (화) 오후 2:47
 */
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<Users> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Users login(String email, String password) {
        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("로그인 정보가 올바르지 않습니다."));

        if (!"LOCAL".equals(user.getType())) {
            throw new RuntimeException("GitHub 회원입니다. GitHub 로그인을 사용하세요.");
        }

        if (user.getPassword() == null || !user.getPassword().equals(encrypt(password))) {
            throw new RuntimeException("로그인 정보가 올바르지 않습니다.");
        }

        return user;
    }

    public void register(String email, String password, String nickname) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        Users user = new Users.Builder()
                .email(email)
                .password(encrypt(password))
                .nickname(nickname)
                .type("LOCAL")
                .build();

        userRepository.save(user);
    }

    private String encrypt(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("암호화 실패", e);
        }
    }
}
