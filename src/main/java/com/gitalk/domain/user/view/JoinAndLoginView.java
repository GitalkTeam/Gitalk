package com.gitalk.domain.user.view;

/**
 * JoinAndLoginView Description :
 * NOTE :
 *
 * @author jki
 * @since 04-07 (화) 오후 3:00
 */
import com.gitalk.domain.oauth.github.model.GithubDeviceCode;
import com.gitalk.domain.oauth.github.service.GithubAuthService;
import com.gitalk.domain.user.model.Users;
import com.gitalk.domain.user.service.UserService;
import com.gitalk.domain.oauth.github.exception.GithubAuthorizationPendingException;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

public class JoinAndLoginView {

    private final UserService userService;
    private final GithubAuthService githubAuthService;
    private final BufferedReader reader;
    private final Console console;

    public JoinAndLoginView(UserService userService, GithubAuthService githubAuthService) {
        this.userService = userService;
        this.githubAuthService = githubAuthService;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.console = System.console();
    }

    public Users start() {
        Users user;
        while (true) {
            try {
                System.out.println("────────────────────────────────────────");
                System.out.println("1. 로컬 로그인   2. GitHub 로그인   3. 회원가입   ");
                System.out.println("────────────────────────────────────────");
                System.out.print("선택: ");

                String input = reader.readLine();

                switch (input) {
                    case "1":
                        user = login();
                        if (user != null) {
                            return user;
                        }
                        break;
                    case "2":
                        user = githubLogin();
                        if (user != null) {
                            return user;
                        }
                        break;
                    case "3":
                        boolean signUpSuccess = signUp();
                        if (signUpSuccess) {
                            System.out.println("\n로그인을 진행해주세요.");
                            user = login();
                            if (user != null) {
                                return user;
                            }
                        }
                        break;
                    default:
                        System.out.println("잘못된 입력");
                }
            } catch (Exception e) {
                System.out.println("처리 실패: " + e.getMessage());
            }
        }
    }

    private Users githubLogin() {
        try {
            GithubDeviceCode deviceCode = githubAuthService.requestDeviceCode();

            System.out.println("\n[ GitHub 로그인 ]");
            System.out.println("1. 아래 URL로 이동");
            System.out.println("2. 아래 코드를 입력");
            System.out.println("3. GitHub에서 승인");
            System.out.println();
            System.out.println("URL  : " + deviceCode.getVerificationUri());
            System.out.println("CODE : " + deviceCode.getUserCode());
            System.out.println();

            int maxRetry = 5;

            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                System.out.print("승인 완료 후 Enter > ");
                reader.readLine();

                try {
                    String accessToken = githubAuthService.requestAccessTokenOnce(deviceCode);
                    Users user = githubAuthService.loginOrRegisterByAccessToken(accessToken);
                    System.out.println("GitHub 로그인 성공: " + user.getNickname());
                    return user;

                } catch (GithubAuthorizationPendingException e) {
                    System.out.println("아직 GitHub 승인이 완료되지 않았습니다. (" + attempt + "/" + maxRetry + ")");
                }
            }

            System.out.println("GitHub 승인 확인 횟수를 초과했습니다. 처음 화면으로 이동합니다.");
            return null;

        } catch (Exception e) {
            System.out.println("GitHub 로그인 실패: " + e.getMessage());
            return null;
        }
    }

    private Users login() throws IOException {
        String email = inputEmail();
        String password = inputPassword();
        Users user = userService.login(email, password);
        System.out.println("로그인 성공");
        return user;
    }

    private boolean signUp() throws IOException {
        try {
            System.out.println("\n [ 회원가입 ]");

            String email = inputEmail();
            String password = inputPassword();
            String nickname = inputNickname();

            userService.register(email, password, nickname);
            System.out.println("회원가입 성공");
            return true;

        } catch (IllegalArgumentException e) {
            System.out.println("회원가입 실패: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("입력 오류 발생");
        } catch (Exception e) {
            System.out.println("시스템 오류 발생");
        }
        return false;
    }

    // ===== 입력 메서드 =====

    private String inputEmail() throws IOException {
        while (true) {
            System.out.print("이메일: ");
            String email = reader.readLine();

            try {
                validateRequired(email, "이메일");
                validateEmailFormat(email);
                return email;
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private String inputPassword() throws IOException {
        while (true) {
            String password;

            if (console != null) {
                char[] pwChars = console.readPassword("비밀번호: ");
                password = new String(pwChars);
            } else {
                // IDE에서는 console이 null일 수 있음
                System.out.print("비밀번호: ");
                password = reader.readLine();
            }

            try {
                validateRequired(password, "비밀번호");
                return password;
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private String inputNickname() throws IOException {
        while (true) {
            System.out.print("닉네임: ");
            String nickname = reader.readLine();

            try {
                validateRequired(nickname, "닉네임");
                return nickname;
            } catch (IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    // ===== 유효성 메서드 =====

    private void validateRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수 입력입니다.");
        }
    }

    private void validateEmailFormat(String email) {
        String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
        if (!email.matches(regex)) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
    }
}