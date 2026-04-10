package com.gitalk.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 터미널 표시 너비를 다루는 공용 유틸.
 *
 * Java String 의 length() 는 코드포인트 개수일 뿐이라 한글·이모지처럼
 * 터미널에서 2컬럼을 차지하는 문자를 1컬럼으로 셈한다.
 * 이 클래스는 "터미널이 실제로 그릴 컬럼 수" 기준으로 동작한다.
 *
 *   displayWidth("안녕")    == 4
 *   displayWidth("hi📢")    == 4
 *   fit("안녕하세요", 6)    == "안녕…"          (총 6컬럼)
 *   fit("hi", 6)           == "hi    "        (총 6컬럼)
 *   padLeft("3", 4)        == "   3"
 *   center("Gitalk", 10)   == "  Gitalk  "
 */
public final class Layout {

    private static final String ELLIPSIS = "…";  // 1컬럼 가정

    private Layout() {}

    /** 터미널 표시 너비 (한글·CJK·흔한 이모지를 2컬럼으로 계산) */
    public static int displayWidth(String s) {
        if (s == null || s.isEmpty()) return 0;
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return w;
    }

    /**
     * 정확히 width 컬럼이 되도록 맞춘다.
     * - 짧으면 우측에 공백 패딩
     * - 길면 끝을 ellipsis(…) 로 잘라 width 컬럼에 맞춤
     *
     * truncate 가 한글 등 2-컬럼 문자 경계 때문에 width 보다 1 작은 결과를 낼 수 있어,
     * 마지막에 한 번 더 우측 패딩으로 정확히 width 컬럼을 보장한다.
     */
    public static String fit(String s, int width) {
        if (width <= 0) return "";
        String text = s == null ? "" : s;
        int w = displayWidth(text);
        if (w == width) return text;
        if (w <  width) return text + " ".repeat(width - w);
        String truncated = truncate(text, width);
        int tw = displayWidth(truncated);
        return tw < width ? truncated + " ".repeat(width - tw) : truncated;
    }

    /** 좌측 공백으로 width 컬럼까지 패딩 (숫자·뱃지 우측 정렬용) */
    public static String padLeft(String s, int width) {
        String text = s == null ? "" : s;
        int diff = width - displayWidth(text);
        return diff <= 0 ? text : " ".repeat(diff) + text;
    }

    /** 우측 공백 패딩 (초과해도 자르지 않음) */
    public static String padRight(String s, int width) {
        String text = s == null ? "" : s;
        int diff = width - displayWidth(text);
        return diff <= 0 ? text : text + " ".repeat(diff);
    }

    /** width 컬럼 안에서 가운데 정렬 */
    public static String center(String s, int width) {
        String text = s == null ? "" : s;
        int diff = width - displayWidth(text);
        if (diff <= 0) return text;
        int left = diff / 2;
        return " ".repeat(left) + text + " ".repeat(diff - left);
    }

    /**
     * 여러 줄을 한 블록으로 보고, 블록의 가장 긴 줄이 width 기준으로 가운데가 되도록
     * 모든 줄에 동일한 좌측 패딩을 적용한다. 줄들의 좌측 변은 가지런히 유지된다.
     */
    public static List<String> centerBlock(List<String> lines, int width) {
        int maxW = 0;
        for (String line : lines) maxW = Math.max(maxW, displayWidth(line));
        int pad = Math.max(0, (width - maxW) / 2);
        String prefix = " ".repeat(pad);
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) result.add(prefix + line);
        return result;
    }

    /**
     * 표시 너비 기준으로 maxWidth 컬럼 이내로 자르고 끝에 …(1컬럼)을 붙인다.
     * 결과 너비는 항상 maxWidth 이하.
     */
    public static String truncate(String s, int maxWidth) {
        if (s == null) return "";
        if (maxWidth <= 0) return "";
        if (maxWidth == 1) return ELLIPSIS;
        if (displayWidth(s) <= maxWidth) return s;

        int budget = maxWidth - 1;  // … 자리 1컬럼
        StringBuilder sb = new StringBuilder();
        int used = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int cw = isWide(cp) ? 2 : 1;
            if (used + cw > budget) break;
            sb.appendCodePoint(cp);
            used += cw;
            i += Character.charCount(cp);
        }
        return sb.toString() + ELLIPSIS;
    }

    /**
     * 단어 단위 줄바꿈. 각 줄이 maxWidth 컬럼을 넘지 않도록 자른다.
     * 한 단어가 maxWidth 보다 길면 그 단어는 잘리지 않고 한 줄로 둔다.
     */
    public static List<String> wrapWords(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            result.add("");
            return result;
        }
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        for (String word : text.split("\\s+")) {
            if (word.isEmpty()) continue;
            int wordWidth = displayWidth(word);
            int extra = lineWidth == 0 ? 0 : 1; // 단어 사이 공백 1
            if (lineWidth + extra + wordWidth > maxWidth && lineWidth > 0) {
                result.add(line.toString());
                line.setLength(0);
                line.append(word);
                lineWidth = wordWidth;
            } else {
                if (extra > 0) { line.append(' '); lineWidth++; }
                line.append(word);
                lineWidth += wordWidth;
            }
        }
        if (line.length() > 0) result.add(line.toString());
        if (result.isEmpty()) result.add("");
        return result;
    }

    private static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)        // Hangul Jamo
            || (cp >= 0x2E80 && cp <= 0x303E)        // CJK Radicals + Punctuation
            || (cp >= 0x3041 && cp <= 0x33FF)        // Hiragana, Katakana, Bopomofo
            || (cp >= 0x3400 && cp <= 0x4DBF)        // CJK Extension A
            || (cp >= 0x4E00 && cp <= 0x9FFF)        // CJK Unified Ideographs
            || (cp >= 0xA000 && cp <= 0xA4CF)        // Yi
            || (cp >= 0xAC00 && cp <= 0xD7A3)        // Hangul Syllables
            || (cp >= 0xF900 && cp <= 0xFAFF)        // CJK Compatibility Ideographs
            || (cp >= 0xFE30 && cp <= 0xFE4F)        // CJK Compatibility Forms
            || (cp >= 0xFF00 && cp <= 0xFF60)        // Fullwidth Forms
            || (cp >= 0xFFE0 && cp <= 0xFFE6)        // Fullwidth signs
            || (cp >= 0x1F300 && cp <= 0x1F9FF)      // 이모지 대역
            || (cp >= 0x1FA00 && cp <= 0x1FAFF);     // 이모지 확장
    }
}
