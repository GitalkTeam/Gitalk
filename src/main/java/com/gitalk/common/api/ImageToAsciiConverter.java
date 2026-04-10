package com.gitalk.common.api;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * 이미지 → ANSI 컬러 ASCII 아트 변환기
 *
 * jpg / png / gif / bmp / webp 등 모든 포맷을
 * ascii-image-converter OS별 바이너리로 변환한다.
 * (stdout을 바이트 그대로 읽어 ESC 시퀀스 보존)
 */
public class ImageToAsciiConverter {

    private static final String BIN_DIR =
            "src/main/java/com/gitalk/common/api/ImageToAscii/ascii-image-converter";

    // ── 공개 API ──────────────────────────────────────────────────────────

    /**
     * termCols x (termRows-4) 안에 종횡비를 유지하며 letterbox 변환.
     * termRows 에서 4를 빼는 것은 뷰어 UI(제목+구분선×2+안내)를 제외하기 위함.
     * 0 이하 값은 해당 방향 제한 없음.
     */
    public static String convert(File imageFile, int termCols, int termRows) throws IOException {
        // 이미지 크기를 읽어 letterbox 계산에만 사용 (렌더링은 바이너리에 위임)
        BufferedImage img = tryReadImage(imageFile);
        int[] dims = letterbox(
                img != null ? img.getWidth()  : 0,
                img != null ? img.getHeight() : 0,
                termCols,
                termRows > 0 ? termRows - 4 : 0
        );
        return convertViaBinary(imageFile, dims[0], dims[1]);
    }

    public static String convert(File imageFile, int termCols) throws IOException {
        return convert(imageFile, termCols, 0);
    }

    /**
     * 종횡비를 유지하며 maxCols x maxRows 안에 꽉 차게 맞추는 출력 크기를 계산한다.
     * 문자 종횡비 보정: 높이 방향은 픽셀의 절반 수 만큼의 문자가 필요하다(~2:1).
     *
     * imgW/imgH = 0 이면 기본값(maxCols x maxCols/2) 반환.
     */
    private static int[] letterbox(int imgW, int imgH, int maxCols, int maxRows) {
        if (imgW <= 0 || imgH <= 0) {
            int w = maxCols > 0 ? maxCols : 80;
            return new int[]{w, maxRows > 0 ? maxRows : w / 2};
        }

        // 문자 종횡비 보정 후 스케일 계산
        double scaleW = maxCols > 0 ? (double) maxCols / imgW                : Double.MAX_VALUE;
        double scaleH = maxRows > 0 ? (double) maxRows * 2.0 / imgH          : Double.MAX_VALUE;
        double scale  = Math.min(Math.min(scaleW, scaleH), 1.0); // 원본보다 크게는 키우지 않음

        int outW = Math.max((int)(imgW * scale), 1);
        int outH = Math.max((int)(imgH * scale / 2.0), 1);
        return new int[]{outW, outH};
    }

    // ── 바이너리 변환 ────────────────────────────────────────────────────

    private static String convertViaBinary(File imageFile, int width, int height) throws IOException {
        File binary = resolveBinary();
        ProcessBuilder pb = new ProcessBuilder(
                binary.getAbsolutePath(),
                imageFile.getAbsolutePath(),
                "-d", width + "," + height,
                "-C"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        // readAllBytes()로 바이트 그대로 읽어 ESC(0x1B) 보존
        byte[] raw = process.getInputStream().readAllBytes();
        try { process.waitFor(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        String result = new String(raw, StandardCharsets.UTF_8).trim();
        if (result.isEmpty()) {
            throw new IOException("변환 결과가 비어 있습니다. 지원 형식: jpg, png, gif, bmp, webp");
        }
        return result;
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────

    /** null 반환 시 미지원 포맷 — 예외를 던지지 않는다 */
    private static BufferedImage tryReadImage(File imageFile) {
        try {
            return ImageIO.read(imageFile);
        } catch (Exception e) {
            return null;
        }
    }

    private static File resolveBinary() throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        String binaryName;
        if (os.contains("win")) {
            binaryName = "window_ascii-image-converter.exe";
        } else if (os.contains("mac") || os.contains("darwin")) {
            binaryName = "mac_ascii-image-converter";
        } else {
            binaryName = "linux_ascii-image-converter";
        }

        File binary = new File(BIN_DIR, binaryName);
        if (!binary.exists()) {
            throw new IOException("바이너리를 찾을 수 없습니다: " + binary.getAbsolutePath());
        }
        if (!os.contains("win") && !binary.canExecute()) {
            binary.setExecutable(true);
        }
        return binary;
    }
}
