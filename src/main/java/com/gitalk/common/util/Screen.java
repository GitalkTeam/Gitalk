package com.gitalk.common.util;

/** 터미널 화면 제어 유틸 */
public class Screen {

    private Screen() {}

    /** 터미널 화면을 비웁니다. console 환경이면 clear 명령, 아니면 개행으로 대체합니다. */
    public static void clear() {
        if (System.console() != null) {
            try {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            } catch (Exception ignored) {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } else {
            System.out.println("\n".repeat(30));
            System.out.flush();
        }
    }
}
