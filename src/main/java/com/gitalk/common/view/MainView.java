package com.gitalk.common.view;

import com.gitalk.common.util.Layout;
import com.gitalk.common.util.Screen;

import java.util.List;

public class MainView {

    private static final int WIDTH = 68;
    private static final String DIV = "─".repeat(WIDTH);

    private static final String[] BANNER_ART = {
        "   ____ ___  _____    _     _     _  __",
        "  / ___|_ _||_   _|  / \\   | |   | |/ /",
        " | |  _ | |   | |   / _ \\  | |   | ' / ",
        " | |_| || |   | |  / ___ \\ | |___| . \\ ",
        " \\____|___|  |_| /_/     \\|_____|_|\\_\\"
    };

    public void clearScreen() {
        Screen.clear();
    }

    public void printBanner() {
        clearScreen();
        System.out.println();
        System.out.println(DIV);
        System.out.println();
        for (String line : BANNER_ART) {
            System.out.println(centerLine(line));
        }
        System.out.println();
        System.out.println(centerLine("개발자를 위한 GitHub CLI 챗봇"));
        System.out.println();
        System.out.println(DIV);
        System.out.println();
        for (String line : Layout.centerBlock(List.of("1. 입장하기", "2. 종료하기"), WIDTH)) {
            System.out.println(line);
        }
        System.out.println();
        System.out.print(" 선택 > ");
        System.out.flush();
    }

    public void printTerminalWarning() {
        System.out.println("[주의] 터미널이 감지되지 않았습니다. 백스페이스 이슈가 발생할 수 있습니다.");
        System.out.println("       ./run.sh 로 외부 터미널에서 실행하세요.\n");
    }

    public void printExit() {
        System.out.println("Gitalk을 종료합니다.");
    }

    public void printError(String message) {
        System.err.println("오류: " + message);
    }

    private String centerLine(String text) {
        int padding = Math.max(0, (WIDTH - Layout.displayWidth(text)) / 2);
        return " ".repeat(padding) + text;
    }
}
