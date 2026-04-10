package com.gitalk.domain.chat.search.util;

/**
 * SearchCommandParser Description : 사용자가 입력한 /search 문자열을 SearchCommand로 해석하는 파서입니다.
 * NOTE : search 유틸리티 성격의 헬퍼이며, 도움말, 방 선택, 공유, 조회 옵션 파싱을 담당합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 5:39
 */
import com.gitalk.domain.chat.search.domain.SearchCommand;

import java.util.ArrayList;
import java.util.List;

public final class SearchCommandParser {

    private SearchCommandParser() {
    }

    public static SearchCommand parse(String raw) {
        List<String> tokens = tokenize(raw);

        Long roomId = null;
        boolean help = false;
        boolean share = false;
        boolean view = false;
        String shareId = null;
        List<String> keywordParts = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String lower = token.toLowerCase();

            switch (lower) {
                case "-h", "--help", "help" -> help = true;

                case "-s", "--share" -> share = true;

                case "-v", "--view" -> {
                    view = true;
                    if (i + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("공유 ID를 입력하세요. 예: /search --view SR-478C6BAB");
                    }
                    shareId = tokens.get(++i);
                }

                case "-r" -> {
                    if (i + 1 >= tokens.size()) {
                        throw new IllegalArgumentException("room 번호를 입력하세요.");
                    }
                    try {
                        roomId = Long.parseLong(tokens.get(++i));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("room 번호는 숫자여야 합니다.");
                    }
                }

                default -> keywordParts.add(token);
            }
        }

        String keyword = String.join(" ", keywordParts).trim();

        if (help) {
            return new SearchCommand(null, null, true, false, false, null);
        }

        if (share) {
            return new SearchCommand(null, null, false, true, false, null);
        }

        if (view) {
            return new SearchCommand(null, null, false, false, true, shareId);
        }

        if (keyword.isBlank()) {
            throw new IllegalArgumentException("검색어를 입력하세요.");
        }

        return new SearchCommand(
                roomId,
                keyword,
                false,
                false,
                false,
                null
        );
    }

    private static List<String> tokenize(String input) {
        List<String> result = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return result;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == '"') {
                inQuote = !inQuote;
                continue;
            }

            if (Character.isWhitespace(ch) && !inQuote) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }

            current.append(ch);
        }

        if (inQuote) {
            throw new IllegalArgumentException("따옴표가 닫히지 않았습니다.");
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }
}
