package com.gitalk.common.api;

import com.gitalk.domain.chat.service.ChatService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;

/**
 * 이미지 업로드 전용 임베디드 HTTP 서버
 *
 * 엔드포인트:
 *   POST /upload?sender=닉네임
 *   - Body : 이미지 파일의 원시 바이트
 *   - Content-Type : image/jpeg | image/png | image/gif | image/webp | image/bmp
 *
 * 처리 흐름:
 *   수신 → 임시 파일 저장 → 비율 유지 축소(Proportional Scaling)로 출력 크기 계산
 *   → ascii-image-converter 실행 → ASCII 아트 소켓 브로드캐스트 → 임시 파일 삭제
 */
public class ImageAsciiHttpServer {

    public static final int HTTP_PORT = 6001;

    private final ChatService chatService;
    private HttpServer server;

    public ImageAsciiHttpServer(ChatService chatService) {
        this.chatService = chatService;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 10);
        server.createContext("/upload", this::handleUpload);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("이미지 업로드 서버 시작 (포트: " + HTTP_PORT + ")");
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // ── 요청 핸들러 ───────────────────────────────────────────────────────

    private void handleUpload(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        String rawQuery    = exchange.getRequestURI().getRawQuery();
        String sender      = parseParam(rawQuery, "sender");
        String filename    = parseParam(rawQuery, "filename");
        Long   roomId      = parseLongParam(rawQuery, "roomId");
        int    termCols    = parseIntParam(rawQuery, "cols", 0);
        int    termRows    = parseIntParam(rawQuery, "rows", 0);
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        String ext         = contentTypeToExt(contentType);

        // roomId 필수 — 멀티방 라우팅용
        if (roomId == null) {
            sendResponse(exchange, 400, "roomId query parameter required");
            return;
        }

        // 이미지 바이트 수신
        byte[] imageBytes;
        try (InputStream body = exchange.getRequestBody()) {
            imageBytes = body.readAllBytes();
        }

        if (imageBytes.length == 0) {
            sendResponse(exchange, 400, "Empty body");
            return;
        }
        if (imageBytes.length > 20 * 1024 * 1024) {
            sendResponse(exchange, 413, "File too large (max 20MB)");
            chatService.broadcastSystemMessage(roomId, sender + " 님의 이미지가 너무 큽니다 (최대 20MB).");
            return;
        }

        // 임시 파일 저장
        File tempFile = File.createTempFile("gitalk_img_", "." + ext);
        tempFile.deleteOnExit();

        try {
            Files.write(tempFile.toPath(), imageBytes);

            System.out.printf("[ImageAscii] room=%d %s 의 이미지 변환 시작 (%s, %d bytes)%n",
                    roomId, sender, tempFile.getName(), imageBytes.length);

            String asciiArt = ImageToAsciiConverter.convert(tempFile, termCols, termRows);

            chatService.broadcastAsciiArt(roomId, sender, filename, asciiArt);

            System.out.printf("[ImageAscii] room=%d %s 의 이미지 변환 완료%n", roomId, sender);
            sendResponse(exchange, 200, "OK");

        } catch (Exception e) {
            System.err.println("[ImageAscii] 변환 오류: " + e.getMessage());
            chatService.broadcastSystemMessage(roomId, sender + " 님의 이미지 변환 실패: " + e.getMessage());
            sendResponse(exchange, 500, "Conversion failed: " + e.getMessage());
        } finally {
            tempFile.delete();
        }
    }

    // ── 유틸 ──────────────────────────────────────────────────────────────

    private static int parseIntParam(String rawQuery, String key, int defaultValue) {
        String val = parseParam(rawQuery, key);
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultValue; }
    }

    /** Long 파라미터. 누락/파싱실패 시 null. */
    private static Long parseLongParam(String rawQuery, String key) {
        String val = parseParam(rawQuery, key);
        if (val == null || "알 수 없음".equals(val)) return null;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return null; }
    }

    private static String parseParam(String rawQuery, String key) {
        if (rawQuery == null || rawQuery.isEmpty()) return "알 수 없음";
        for (String param : rawQuery.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && key.equals(kv[0])) {
                try {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return kv[1];
                }
            }
        }
        return "알 수 없음";
    }

    private static String contentTypeToExt(String contentType) {
        if (contentType == null) return "jpg";
        if (contentType.contains("png"))  return "png";
        if (contentType.contains("gif"))  return "gif";
        if (contentType.contains("webp")) return "webp";
        if (contentType.contains("bmp"))  return "bmp";
        return "jpg";
    }

    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
