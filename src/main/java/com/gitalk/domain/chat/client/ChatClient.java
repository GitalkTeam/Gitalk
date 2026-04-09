package com.gitalk.chat.client;

import com.gitalk.chat.service.Protocol;
import com.gitalk.common.api.ImageAsciiHttpServer;

import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * CLI 채팅 클라이언트
 *
 * 실행 방법:
 *   java com.gitalk.chat.client.ChatClient [host]
 *   host 생략 시 127.0.0.1 (로컬 테스트)
 *
 * 특수 명령어:
 *   /image   → 파일 선택 창(GUI) 또는 경로 입력 → 서버에 업로드 → ASCII 아트로 변환 후 채팅 표시
 *   /quit    → 채팅 종료
 */
public class ChatClient {

    private static final int PORT = 6000;
    private static final String nickname = "클라이언트";

    /** 수신한 ASCII 아트 저장소: 수신 순번("1","2",...) → art */
    private static final Map<String, String> asciiArtStore = new ConcurrentHashMap<>();
    /** 이미지 수신 순번 카운터 */
    private static final AtomicInteger imageCounter = new AtomicInteger(0);
    /** 뷰어 표시 중 수신된 메시지 임시 버퍼 */
    private static final List<String> viewerBuffer = Collections.synchronizedList(new ArrayList<>());
    /** 뷰어 활성화 여부 */
    private static volatile boolean viewerActive = false;

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        String host = args.length > 0 ? args[0] : "127.0.0.1";

        System.out.println("서버에 연결 중: " + host + ":" + PORT);

        try (Socket socket = new Socket(host, PORT)) {
            BufferedReader in       = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter    out      = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

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
            System.out.println("메시지를 입력하세요. /image: 이미지 변환  /quit: 종료");
            String input;
            while ((input = keyboard.readLine()) != null) {
                String trimmed = input.trim();

                if ("/quit".equalsIgnoreCase(trimmed)) {
                    out.println(Protocol.QUIT);
                    break;
                }

                if ("/image".equalsIgnoreCase(trimmed)) {
                    handleImageCommand(host, out, keyboard);
                    continue;
                }

                if (trimmed.startsWith("/image ") && trimmed.endsWith(".view")) {
                    String filename = trimmed.substring("/image ".length(), trimmed.length() - ".view".length()).trim();
                    String art = asciiArtStore.get(filename);
                    if (art == null) {
                        System.out.println("[이미지] '" + filename + "' 을 찾을 수 없습니다.");
                    } else {
                        showAsciiViewer(art, filename, keyboard);
                    }
                    continue;
                }

                if (!trimmed.isEmpty()) {
                    out.println(Protocol.buildMsgPacket(trimmed));
                }
            }

        } catch (IOException e) {
            System.out.println("서버 연결 실패: " + e.getMessage());
        }

        System.out.println("연결을 종료했습니다.");
    }

    // ── /image 명령 처리 ──────────────────────────────────────────────────

    private static void handleImageCommand(String host, PrintWriter out, BufferedReader keyboard) {
        System.out.println("[이미지] 파일을 선택해 주세요...");

        File selectedFile = openFileChooser(keyboard);
        if (selectedFile == null) {
            System.out.println("[이미지] 취소되었습니다.");
            return;
        }
        if (!selectedFile.exists() || !selectedFile.isFile()) {
            System.out.println("[이미지] 파일을 찾을 수 없습니다: " + selectedFile.getAbsolutePath());
            return;
        }

        String filename = stripExtension(selectedFile.getName());
        int termCols    = getTerminalColumns();
        int termRows    = getTerminalLines();
        System.out.println("[이미지] '" + selectedFile.getName() + "' 업로드 중... (터미널: "
                + (termCols > 0 ? termCols : "?") + "x" + (termRows > 0 ? termRows : "?") + ")");

        // 변환 중임을 채팅방에 알림
        out.println(Protocol.buildMsgPacket("[이미지 변환 중... 잠시 기다려주세요]"));

        // HTTP 업로드 (별도 스레드 – 응답 대기 중 채팅 수신이 막히지 않도록)
        final File fileToUpload = selectedFile;
        Thread uploadThread = new Thread(() -> uploadImage(host, fileToUpload, filename, termCols, termRows));
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /**
     * 파일 선택 창을 연다.
     * GUI 환경이면 JFileChooser, 헤드리스면 터미널에서 경로를 직접 입력받는다.
     */
    private static File openFileChooser(BufferedReader keyboard) {
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                // 시스템 Look & Feel 적용 (macOS / Windows 네이티브 UI)
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            final File[] result = {null};
            try {
                SwingUtilities.invokeAndWait(() -> {
                    JFileChooser chooser = new JFileChooser();
                    chooser.setDialogTitle("이미지 파일 선택");
                    chooser.setFileFilter(new FileNameExtensionFilter(
                            "이미지 파일 (jpg, png, gif, webp, bmp)",
                            "jpg", "jpeg", "png", "gif", "webp", "bmp"
                    ));
                    chooser.setAcceptAllFileFilterUsed(false);

                    int returnVal = chooser.showOpenDialog(null);
                    if (returnVal == JFileChooser.APPROVE_OPTION) {
                        result[0] = chooser.getSelectedFile();
                    }
                });
            } catch (Exception e) {
                System.out.println("[이미지] 파일 선택 창 오류: " + e.getMessage());
            }

            if (result[0] != null) return result[0];
            // 선택 창이 실패했으면 아래 터미널 폴백으로 이어진다
        }

        // 헤드리스 또는 Swing 오류 시 터미널에서 직접 입력
        System.out.print("[이미지] 파일 경로 입력 (취소: Enter만 입력): ");
        System.out.flush();
        try {
            String path = keyboard.readLine();
            if (path == null || path.isBlank()) return null;
            return new File(path.trim());
        } catch (IOException e) {
            return null;
        }
    }

    // ── HTTP 업로드 ───────────────────────────────────────────────────────

    private static void uploadImage(String host, File imageFile, String filename, int termCols, int termRows) {
        try {
            String contentType     = detectContentType(imageFile);
            String encodedSender   = URLEncoder.encode(nickname, StandardCharsets.UTF_8);
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8);
            String sizeParam       = (termCols > 0 ? "&cols=" + termCols : "")
                                   + (termRows > 0 ? "&rows=" + termRows : "");
            URL url = new URL("http://" + host + ":" + ImageAsciiHttpServer.HTTP_PORT
                    + "/upload?sender=" + encodedSender + "&filename=" + encodedFilename + sizeParam);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(60_000);  // 변환에 시간이 걸릴 수 있음

            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            conn.setFixedLengthStreamingMode(imageBytes.length);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(imageBytes);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                System.out.println("[이미지] 변환 완료 – 채팅창을 확인하세요.");
            } else {
                System.out.println("[이미지] 변환 실패 (서버 응답: " + code + ")");
            }

        } catch (Exception e) {
            System.out.println("[이미지] 업로드 오류: " + e.getMessage());
        }
    }

    // ── ASCII 아트 뷰어 (vim/nano 스타일 대체 화면) ──────────────────────────

    private static void showAsciiViewer(String art, String filename, BufferedReader keyboard) throws IOException {
        viewerActive = true;

        ansi("?1049h");
        ansi("2J");
        ansi("H");

        System.out.println("  [ " + filename + " ]");
        System.out.println("─".repeat(64));
        System.out.print(art);
        if (!art.endsWith("\n")) System.out.println();
        ansi("0m");
        System.out.println("─".repeat(64));
        System.out.print("  q+Enter: 채팅으로 돌아가기");
        System.out.flush();

        String line;
        while ((line = keyboard.readLine()) != null) {
            if ("q".equalsIgnoreCase(line.trim())) break;
        }

        ansi("0m");
        ansi("?1049l");
        viewerActive = false;

        synchronized (viewerBuffer) {
            if (!viewerBuffer.isEmpty()) {
                System.out.println("──── 뷰어 중 수신된 메시지 ────");
                for (String msg : viewerBuffer) System.out.println(msg);
                viewerBuffer.clear();
                System.out.println("────────────────────────────────");
            }
        }
    }

    /** ANSI 이스케이프 시퀀스 출력 (ESC [ + code) */
    private static void ansi(String code) {
        System.out.print("\u001B[" + code);
        System.out.flush();
    }

    /**
     * 현재 터미널의 컬럼 수를 감지한다.
     * Git Bash: COLUMNS 환경변수 또는 tput cols 로 감지.
     * 감지 실패 시 0 반환 (서버가 이미지 원본 크기 사용).
     */
    private static int getTerminalColumns() {
        String env = System.getenv("COLUMNS");
        if (env != null) {
            try { return Integer.parseInt(env.trim()); } catch (NumberFormatException ignored) {}
        }
        return tput("cols");
    }

    private static int getTerminalLines() {
        String env = System.getenv("LINES");
        if (env != null) {
            try { return Integer.parseInt(env.trim()); } catch (NumberFormatException ignored) {}
        }
        return tput("lines");
    }

    private static int tput(String cap) {
        try {
            Process p = new ProcessBuilder("sh", "-c", "tput " + cap + " 2>/dev/null")
                    .redirectErrorStream(false)
                    .start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor();
            if (!out.isEmpty()) return Integer.parseInt(out);
        } catch (Exception ignored) {}
        return 0;
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String detectContentType(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".png"))            return "image/png";
        if (name.endsWith(".gif"))            return "image/gif";
        if (name.endsWith(".webp"))           return "image/webp";
        if (name.endsWith(".bmp"))            return "image/bmp";
        return "image/jpeg";
    }

    // ── 입장 응답 처리 ─────────────────────────────────────────────────────

    private static boolean handleJoinResponse(String response) {
        String[] parts = Protocol.parse(response);
        String type = parts[0];

        if (Protocol.JOIN_SUCCESS.equals(type)) {
            String nick = parts.length > 1 ? parts[1] : "?";
            System.out.println("환영합니다, " + nick + "님!");
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
                if (parts.length >= 3) {
                    String line = "[" + parts[1] + "] " + parts[2];
                    if (viewerActive) viewerBuffer.add(line);
                    else System.out.println(line);
                }
                break;

            case Protocol.SERVER:
                if (parts.length >= 2) {
                    String line = "*** " + parts[1] + " ***";
                    if (viewerActive) viewerBuffer.add(line);
                    else System.out.println(line);
                }
                break;

            case Protocol.ASCII_ART:
                if (parts.length >= 4) {
                    String sender   = parts[1];
                    String filename = parts[2];
                    String art      = Protocol.decodeAsciiArt(parts[3]);
                    String idx      = String.valueOf(imageCounter.incrementAndGet());
                    asciiArtStore.put(idx, art);
                    String notice = "[" + sender + "] 이(가) " + filename + " 이미지를 보냈습니다.\n"
                            + "  → 보기: /image " + idx + ".view";
                    if (viewerActive) {
                        viewerBuffer.add(notice);
                    } else {
                        System.out.println(notice);
                    }
                }
                break;

            default:
                System.out.println("[알림] " + packet);
        }
    }

}
