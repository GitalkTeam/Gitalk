package com.gitalk.domain.chat.search.util;

/**
 * SearchWindowLauncher Description : 검색 결과를 별도 Windows 콘솔 창으로 띄워주는 실행 유틸리티입니다.
 * NOTE : search/view 보조 유틸리티이며, UTF-8 결과 텍스트를 임시 파일로 저장한 뒤 cmd 창을 실행합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 6:26
 */
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SearchWindowLauncher {

    private static final Charset CMD_CHARSET = Charset.forName("MS949");

    public void open(String title, String content) {
        try {
            Path textFile = Files.createTempFile("gitalk-search-", ".txt");
            Files.writeString(textFile, content, StandardCharsets.UTF_8);

            Path cmdFile = Files.createTempFile("gitalk-search-", ".cmd");

            String script = buildScript(title, textFile);
            Files.writeString(cmdFile, script, CMD_CHARSET);

            new ProcessBuilder(
                    "cmd", "/c",
                    "start", "\"\"",
                    "cmd", "/k",
                    "call", "\"" + cmdFile.toAbsolutePath() + "\""
            ).start();

        } catch (IOException e) {
            throw new IllegalStateException("검색 결과 창을 열 수 없습니다: " + e.getMessage(), e);
        }
    }

    private String buildScript(String title, Path textFile) {
        String safeTitle = sanitize(title);
        String safePath = textFile.toAbsolutePath().toString();

        return "@echo off\r\n"
                + "title " + safeTitle + "\r\n"
                + "chcp 65001 > nul\r\n"
                + "cls\r\n"
                + "echo ========================================\r\n"
                + "echo " + safeTitle + "\r\n"
                + "echo ========================================\r\n"
                + "echo.\r\n"
                + "type \"" + safePath + "\"\r\n"
                + "echo.\r\n"
                + "echo ----------------------------------------\r\n"
                + "echo Press any key to close...\r\n"
                + "pause > nul\r\n";
    }

    private String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "Search Result";
        }
        return text.replace("\"", "").replace("&", "^&").trim();
    }
}
