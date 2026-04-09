package com.gitalk.domain.chat.search.util;

/**
 * TextNormalizer Description : 키워드 검색이 안정적으로 동작하도록 문자열을 정규화하는 유틸리티입니다.
 * NOTE : util 계층 헬퍼이며, NFKC 정규화와 trim, 소문자 변환, 공백 정리를 수행합니다.
 *
 * @author jki
 * @since 04-09 (목) 오후 3:25
 */
import java.text.Normalizer;
import java.util.Locale;

public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKC);
        normalized = normalized.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized;
    }
}
