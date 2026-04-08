package com.gitalk.chat.client;

import com.gitalk.chat.service.Protocol;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * CLI 채팅 클라이언트
 *
 * 실행 방법:
 *   java main.java.com.gitalk.chat.client.ChatClient [host]
 *   host 생략 시 127.0.0.1 (로컬 테스트)
 *   나중에 AWS EC2 주소를 인자로 전달하면 바로 연결 가능
 *
 * DB 연동 후 로그인(아이디/비밀번호) 방식으로 복구 예정
 */
public class ChatClient {

    private static final int PORT = 6000;
    private static final String nickname = "클라이언트";

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        String host = args.length > 0 ? args[0] : "127.0.0.1";

        System.out.println("서버에 연결 중: " + host + ":" + PORT);

        try (Socket socket = new Socket(host, PORT)) {
            BufferedReader in       = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter    out      = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

            // ── 닉네임 입력 ────────────────────────────────────────────────
//            System.out.print("닉네임: ");
//            String nickname = keyboard.readLine();
//            if (nickname == null || nickname.isBlank()) {
//                System.out.println("닉네임을 입력해야 합니다.");
//                return;
//            }

            out.println(Protocol.buildJoinPacket(nickname.trim()));

            String response = in.readLine();
            if (response == null) {
                System.out.println("서버 연결이 끊겼습니다.");
                return;
            }

            if (!handleJoinResponse(response)) {
                return;
            }

            // ── 수신 스레드 시작 ─────────────────────────────────────────
            Thread receiver = new Thread(() -> receiveLoop(in));
            receiver.setDaemon(true);
            receiver.start();

            // ── 송신 루프 ────────────────────────────────────────────────
            System.out.println("메시지를 입력하세요. 종료: /quit");
            String input;
            while ((input = keyboard.readLine()) != null) {
                if ("/quit".equalsIgnoreCase(input.trim())) {
                    out.println(Protocol.QUIT);
                    break;
                }
                if (!input.isBlank()) {
                    out.println(Protocol.buildMsgPacket(input));
                }
            }

        } catch (IOException e) {
            System.out.println("서버 연결 실패: " + e.getMessage());
        }

        System.out.println("연결을 종료했습니다.");
    }

    // ── 입장 응답 처리 ─────────────────────────────────────────────────────

    private static boolean handleJoinResponse(String response) {
        String[] parts = Protocol.parse(response);
        String type = parts[0];

        if (Protocol.JOIN_SUCCESS.equals(type)) {
            String nickname = parts.length > 1 ? parts[1] : "?";
            System.out.println("환영합니다, " + nickname + "님!");
            System.out.println("---------------------------------");
            return true;
        }

        if (Protocol.JOIN_FAILED.equals(type)) {
            String reason = parts.length > 1 ? parts[1] : "알 수 없는 오류";
            System.out.println("입장 실패: " + reason);
            return false;
        }

        System.out.println("알 수 없는 응답: " + response);
        return false;
    }

    // ── 수신 루프 ─────────────────────────────────────────────────────────

    private static void receiveLoop(BufferedReader in) {
        try {
            String packet;
            while ((packet = in.readLine()) != null) {
                displayPacket(packet);
            }
        } catch (IOException e) {
            System.out.println("\n서버와 연결이 끊겼습니다.");
        }
    }

    private static void displayPacket(String packet) {
        String[] parts = Protocol.parse(packet);
        String type = parts[0];

        switch (type) {
            case Protocol.MSG:
                // parts[1] = sender, parts[2] = content
                if (parts.length >= 3) {
                    System.out.println("[" + parts[1] + "] " + parts[2]);
                }
                break;

            case Protocol.SERVER:
                if (parts.length >= 2) {
                    System.out.println("*** " + parts[1] + " ***");
                }
                break;

            default:
                System.out.println("[알림] " + packet);
        }
    }
}
