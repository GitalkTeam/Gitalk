package com.gitalk.domain.user.service;

import com.gitalk.domain.user.model.Users;
import com.gitalk.domain.user.repository.UserRepository;

import java.security.MessageDigest;

/**
 * UserService Description :
 * NOTE :
 *
 * @author jki
 * @since 04-07 (화) 오후 2:47
 */
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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