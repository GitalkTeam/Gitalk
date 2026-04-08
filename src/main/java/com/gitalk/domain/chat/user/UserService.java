package com.gitalk.chat.user;
/*
 * 유저 서비스 - 로그인 로직 담당
 */
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 로그인 처리
     * @return 인증 성공 시 User, 실패 시 null
     */
    public User login(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getPassword().equals(password))
                .orElse(null);
    }
}
