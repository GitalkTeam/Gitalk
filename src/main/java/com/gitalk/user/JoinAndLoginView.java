package com.gitalk.user;

/**
 * JoinAndLoginView Description :
 * NOTE :
 *
 * @author jki
 * @since 04-07 (화) 오후 3:00
 */
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class JoinAndLoginView {

    private final UserService userService;
    private final BufferedReader reader;
    public JoinAndLoginView(UserService userService) {
        this.userService = userService;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
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
            System.out.println("\n[ 회원가입 ] ");

            System.out.print("이메일: ");
            String email = reader.readLine();

            System.out.print("비밀번호: ");
            String password = reader.readLine();

            System.out.print("닉네임: ");
            String nickname = reader.readLine();

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


    public boolean login() {
        try {
            System.out.print("Email 입력: ");
            String email = reader.readLine();

            System.out.print("비밀번호 입력: ");
            String password = reader.readLine();

            return userService.login(email, password);

        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return false;
    }
}