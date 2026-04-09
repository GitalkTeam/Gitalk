package com.gitalk.domain.user.view;

/**
 * JoinAndLoginView Description : 콘솔 기반 회원 가입, 일반 로그인, GitHub 로그인 화면 흐름을 담당하는 view 클래스입니다.
 * NOTE : view 계층 클래스이며, 사용자 입력과 출력은 처리하고 실제 비즈니스 로직은 service 계층에 위임합니다.
 *
 * @author jki
 * @since 04-07 (화) 오후 3:00
 */
import com.gitalk.common.util.Layout;
import com.gitalk.common.util.Screen;
import com.gitalk.domain.oauth.github.exception.GithubAuthorizationPendingException;
import com.gitalk.domain.oauth.github.model.GithubDeviceCode;
import com.gitalk.domain.oauth.github.service.GithubAuthService;
import com.gitalk.domain.user.model.Users;
import com.gitalk.domain.user.service.UserService;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;

public class JoinAndLoginView {

    private static final int WIDTH = 68;
    private static final String DIV = "─".repeat(WIDTH);

    private final UserService userService;
    private final GithubAuthService githubAuthService;
    private final BufferedReader reader;
    private final Console console;

    public JoinAndLoginView(UserService userService, GithubAuthService githubAuthService, BufferedReader reader) {
        this.userService = userService;
        this.githubAuthService = githubAuthService;
        this.reader = reader;
        this.console = System.console();
    }

    /** 로그인/회원가입 완료 시 Users 반환. 사용자가 입력을 취소(Ctrl+D 등)하면 null. */
    public Users start() {
        Users user;
        while (true) {
            try {
                printHeader("Gitalk 로그인");
                for (String line : Layout.centerBlock(List.of(
                        "1. 로컬 로그인",
                        "2. GitHub 로그인",
                        "3. 회원가입",
                        "0. 종료"
                ), WIDTH)) {
                    System.out.println(line);
                }
                System.out.println();

                String input = readLine(" 선택 > ");
                if (input == null || "0".equals(input)) return null;

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
                            pressEnterToContinue("\n 로그인을 진행해주세요. (Enter)");
                            user = login();
                            if (user != null) {
                                return user;
                            }
                        }
                        break;
                    default:
                        printMessage("잘못된 입력입니다.");
                        pressEnterToContinue(null);
                }
            } catch (InterruptedIOException e) {
                // 사용자 인터럽트(EOF/Ctrl+D 등) → 종료 신호
                return null;
            } catch (Exception e) {
                printMessage("처리 실패: " + e.getMessage());
                pressEnterToContinue(null);
            }
        }
    }

    private Users githubLogin() {
        try {
            printHeader("GitHub 로그인");
            GithubDeviceCode deviceCode = githubAuthService.requestDeviceCode();

            for (String line : Layout.centerBlock(List.of(
                    "1. 아래 URL로 이동",
                    "2. 아래 코드를 입력",
                    "3. GitHub에서 승인"
            ), WIDTH)) {
                System.out.println(line);
            }
            System.out.println();
            System.out.println(Layout.center("URL  : " + deviceCode.getVerificationUri(), WIDTH));
            System.out.println(Layout.center("CODE : " + deviceCode.getUserCode(), WIDTH));
            System.out.println();

            int maxRetry = 5;

            for (int attempt = 1; attempt <= maxRetry; attempt++) {
                readLine(" 승인 완료 후 Enter > ");

                try {
                    String accessToken = githubAuthService.requestAccessTokenOnce(deviceCode);
                    Users user = githubAuthService.loginOrRegisterByAccessToken(accessToken);
                    printMessage("GitHub 로그인 성공: " + user.getNickname());
                    return user;

                } catch (GithubAuthorizationPendingException e) {
                    printMessage("아직 GitHub 승인이 완료되지 않았습니다. (" + attempt + "/" + maxRetry + ")");
                }
            }

            printMessage("GitHub 승인 확인 횟수를 초과했습니다. 처음 화면으로 이동합니다.");
            pressEnterToContinue(null);
            return null;

        } catch (Exception e) {
            printMessage("GitHub 로그인 실패: " + e.getMessage());
            pressEnterToContinue(null);
            return null;
        }
    }

    private Users login() throws IOException {
        printHeader("로그인");
        String email = inputEmail();
        String password = inputPassword();
        try {
            Users user = userService.login(email, password);
            printMessage("로그인 성공");
            return user;
        } catch (RuntimeException e) {
            printMessage("로그인 실패: " + e.getMessage());
            pressEnterToContinue(null);
            return null;
        }
    }

    private boolean signUp() throws IOException {
        try {
            printHeader("회원가입");

            String email = inputEmail();
            String password = inputPassword();
            String nickname = inputNickname();

            userService.register(email, password, nickname);
            printMessage("회원가입 성공");
            return true;

        } catch (InterruptedIOException e) {
            // 인터럽트는 그대로 위로 전파해서 start()가 종료 처리
            throw e;
        } catch (IllegalArgumentException e) {
            printMessage("회원가입 실패: " + e.getMessage());
        } catch (IOException e) {
            printMessage("입력 오류 발생");
        } catch (Exception e) {
            printMessage("시스템 오류 발생");
        }
        pressEnterToContinue(null);
        return false;
    }

    // ===== 입력 메서드 =====

    private String inputEmail() throws IOException {
        while (true) {
            String raw = requireInput(" 이메일   : ");
            // 이메일에는 공백/포맷 문자가 없어야 함 (IME 삽입 invisible char 방어)
            String email = raw.replaceAll("[\\s\\p{Z}\\p{Cf}]", "");
            try {
                validateRequired(email, "이메일");
                validateEmailFormat(email);
                return email;
            } catch (IllegalArgumentException e) {
                printMessage(e.getMessage());
            }
        }
    }

    private String inputPassword() throws IOException {
        while (true) {
            String password;
            if (console != null) {
                char[] pw = console.readPassword(" 비밀번호 : ");
                if (pw == null) {
                    throw new InterruptedIOException("입력이 취소되었습니다.");
                }
                password = cleanLine(new String(pw));
            } else {
                // IDE에서는 console이 null일 수 있음
                System.out.print(" 비밀번호 : ");
                System.out.flush();
                String raw = reader.readLine();
                if (raw == null) {
                    throw new InterruptedIOException("입력이 취소되었습니다.");
                }
                password = cleanLine(raw);
            }
            try {
                validateRequired(password, "비밀번호");
                return password;
            } catch (IllegalArgumentException e) {
                printMessage(e.getMessage());
            }
        }
    }

    private String inputNickname() throws IOException {
        while (true) {
            String nickname = requireInput(" 닉네임   : ");
            try {
                validateRequired(nickname, "닉네임");
                return nickname;
            } catch (IllegalArgumentException e) {
                printMessage(e.getMessage());
            }
        }
    }

    // ===== 화면 헬퍼 =====

    /** Screen.clear() + 가운데 정렬된 [ 제목 ] 헤더 + divider 출력 */
    private void printHeader(String title) {
        Screen.clear();
        System.out.println();
        System.out.println(DIV);
        System.out.println(Layout.center("[ " + title + " ]", WIDTH));
        System.out.println(DIV);
        System.out.println();
    }

    /** 일반 알림 메시지 (좌측 한 칸 들여쓰기) */
    private void printMessage(String message) {
        System.out.println(" " + message);
    }

    /** 사용자가 Enter 누를 때까지 대기 — 화면 전환 전 결과 확인용 */
    private void pressEnterToContinue(String prompt) {
        try {
            readLine(prompt != null ? prompt : "\n 계속하려면 Enter > ");
        } catch (IOException ignored) {
        }
    }

    // ===== 입력 헬퍼 =====

    /**
     * console이 있으면 console.readLine(prompt)으로 읽어 백스페이스 범위를 입력 영역으로 제한.
     * console이 없으면 (IDE 등) BufferedReader 사용.
     * EOF/스트림 종료 시 null 반환.
     */
    private String readLine(String prompt) throws IOException {
        if (console != null) {
            return cleanLine(console.readLine(prompt));
        }
        System.out.print(prompt);
        System.out.flush();
        return cleanLine(reader.readLine());
    }

    /** 필수 입력. EOF/Ctrl+D면 InterruptedIOException 던짐 → 호출 체인이 종료 처리. */
    private String requireInput(String prompt) throws IOException {
        String line = readLine(prompt);
        if (line == null) {
            throw new InterruptedIOException("입력이 취소되었습니다.");
        }
        return line;
    }

    /** 제어 문자(백스페이스 포함) · 보이지 않는 문자 · 깨진 UTF-8(\uFFFD) 제거 */
    private String cleanLine(String line) {
        if (line == null) return null;
        return line.replaceAll("[\\p{Cntrl}\\uFEFF\\uFFFD]", "").replaceAll("\\p{Z}", " ").trim();
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
