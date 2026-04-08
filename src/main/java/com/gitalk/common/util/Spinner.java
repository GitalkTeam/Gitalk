package com.gitalk.common.util;

public class Spinner {

    private static final String[] FRAMES = { "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏" };
    private static final boolean IS_TERMINAL = System.console() != null;

    private Thread thread;
    private volatile boolean running;

    public void start(String message) {
        if (IS_TERMINAL) {
            running = true;
            thread = new Thread(() -> {
                int i = 0;
                while (running) {
                    System.out.print("\r " + FRAMES[i++ % FRAMES.length] + " " + message);
                    System.out.flush();
                    try { Thread.sleep(80); } catch (InterruptedException e) { break; }
                }
                System.out.print("\r" + " ".repeat(80) + "\r");
                System.out.flush();
            });
            thread.setDaemon(true);
            thread.start();
        } else {
            System.out.print(" " + message + "...");
            System.out.flush();
        }
    }

    public void stop() {
        if (IS_TERMINAL) {
            running = false;
            try { thread.join(500); } catch (InterruptedException ignored) {}
        } else {
            System.out.println(" 완료");
        }
    }
}
