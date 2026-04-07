package com.gitalk;

import com.gitalk.chatbot.ChatBot;
import com.gitalk.user.JoinAndLoginView;
import com.gitalk.user.UserRepository;
import com.gitalk.user.UserService;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;

public class GitalkApplication {

    public static void main(String[] args) throws Exception {
        System.out.println("\nGitalk - 개발자를 위한 CLI 챗봇\n");

        // 1. 의존성 생성
        UserRepository userRepository = new UserRepository();
        UserService userService = new UserService(userRepository);
        JoinAndLoginView joinAndLoginView = new JoinAndLoginView(userService);

        // 2. 프로그램 시작
        joinAndLoginView.start();


        Console console = System.console();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        ChatBot chatBot = new ChatBot(reader);
        chatBot.printHelp();

        if (console == null) {
            System.out.println("[주의] 터미널이 감지되지 않았습니다. 백스페이스 이슈가 발생할 수 있습니다.");
            System.out.println("       ./run.sh 로 외부 터미널에서 실행하세요.\n");
        }

        while (true) {
            try {
                String line;
                if (console != null) {
                    line = console.readLine("> ");
                } else {
                    System.out.print("> ");
                    System.out.flush();
                    line = reader.readLine();
                }
                if (line == null) break;

                String input = line.replaceAll("[\\p{Cntrl}\\uFEFF]", "")
                        .replaceAll("\\p{Z}", " ")
                        .trim();
                if (input.isEmpty()) continue;
                if ("exit".equalsIgnoreCase(input)) {
                    System.out.println("Gitalk을 종료합니다.");
                    System.exit(0);
                }

                chatBot.handleCommand(input);

            } catch (RuntimeException e) {
                // Ctrl+C (UserInterruptException) 처리
                if (e.getClass().getSimpleName().equals("UserInterruptException")) {
                    System.out.println("\nGitalk을 종료합니다.");
                    System.exit(0);
                }
                System.err.println("오류: " + e.getMessage());
            }
        }
    }
}