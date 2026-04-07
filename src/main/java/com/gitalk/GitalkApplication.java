package com.gitalk;

import com.gitalk.chatbot.ChatBot;

import java.util.Scanner;

public class GitalkApplication {

    public static void main(String[] args) {
        System.out.println("""
                ╔══════════════════════════════════════════╗
                ║   Gitalk - 개발자를 위한 CLI 챗봇             ║
                ╚══════════════════════════════════════════╝
                """);

        ChatBot chatBot = new ChatBot();
        chatBot.printHelp();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) continue;
            if ("exit".equalsIgnoreCase(input)) {
                System.out.println("Gitalk을 종료합니다.");
                System.exit(0);
            }

            chatBot.handleCommand(input);
        }
    }
}
