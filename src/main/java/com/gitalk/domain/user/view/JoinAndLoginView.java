package com.gitalk.domain.user.view;

/**
 * JoinAndLoginView Description :
 * NOTE :
 *
 * @author jki
 * @since 04-07 (화) 오후 3:00
 */
import com.gitalk.domain.user.service.UserService;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.io.IOException;

public class JoinAndLoginView {


    private final UserService userService;
    private final BufferedReader reader;
    private final Console console;

    public JoinAndLoginView(UserService userService) {
        this.userService = userService;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.console = System.console();
    }

    public void start() {
        outer:
        while (true) {
            try {
                System.out.println("────────────────────────────────────────────────────────────────────");
                System.out.println("   1. 로그인                      2. 회원가입");
                System.out.println("────────────────────────────────────────────────────────────────────");
                System.out.print("선택: ");

                String input = reader.readLine();

                switch (input) {
                    case "1":
                        if (login()) {
                            break outer; // while 탈출
                        } else {
                            System.out.println("로그인 실패");
                        }
                        break;

                    case "2":
                        if (signUp()) {
                            break outer; // while 탈출
                        }
                        break;

                    default:
                        System.out.println("잘못된 입력");
                }

            } catch (IOException e) {
                System.out.println("입력 오류 발생");
            }
        }
    }


    public boolean signUp() {

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

    public boolean login() {
        try {
            String email = inputEmail();
            String password = inputPassword();

            return userService.login(email, password);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return false;
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