package com.gitalk.user;

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


    public boolean login(String email, String password) {

        Users user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("존재하지 않는 사용자"));

        if (!user.getPassword().equals(encrypt(password))) {
            throw new RuntimeException("비밀번호 불일치");
        }

        return true;
    }


    public void register(String email, String password, String nickname) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        String encryptedPassword = encrypt(password);

        Users user = new Users.Builder()
                .email(email)
                .password(encryptedPassword)
                .nickname(nickname)
                .type("LOCAL")
                .build();

        userRepository.save(user);
    }

    private String encrypt(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
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