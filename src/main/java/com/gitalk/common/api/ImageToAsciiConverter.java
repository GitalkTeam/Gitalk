package com.gitalk.common.api;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * 이미지 → ANSI 컬러 ASCII 아트 변환기
 *
 * jpg / png / gif / bmp  → Java 내장 ImageIO로 직접 변환 (ESC 손실 없음)
 * webp 등 미지원 포맷    → ascii-image-converter 바이너리 폴백
 *                          (stdout을 바이트 그대로 읽어 ESC 보존)
 */
public class ImageToAsciiConverter {

    private static final String BIN_DIR =
            "src/main/java/com/gitalk/common/api/ImageToAscii/ascii-image-converter";

    /**
     * 터미널 문자 종횡비 (문자 높이 / 문자 너비).
     * 값을 낮추면 세로가 늘어나고, 높이면 세로가 압축된다.
     * 일반 모노스페이스 폰트 기준: 1.8 ~ 2.0
     */
    private static final double CHAR_ASPECT = 1.8;

    /**
     * 문자 밀도 맵 — 인덱스 0이 가장 어두운(조밀) 문자, 끝이 가장 밝은(희박) 문자.
     * 밝기 값에 반비례해서 인덱스를 선택한다.
     */
    private static final char[] DENSITY =
            "@%#Wmqpdbkhao*+=-:,.  ".toCharArray();

    // ── 공개 API ──────────────────────────────────────────────────────────

    /**
     * termCols x (termRows-4) 안에 종횡비를 유지하며 letterbox 변환.
     * termRows 에서 4를 빼는 것은 뷰어 UI(제목+구분선×2+안내)를 제외하기 위함.
     * 0 이하 값은 해당 방향 제한 없음.
     */
    public static String convert(File imageFile, int termCols, int termRows) throws IOException {
        BufferedImage img = tryReadImage(imageFile);
        int[] dims = letterbox(
                img != null ? img.getWidth()  : 0,
                img != null ? img.getHeight() : 0,
                termCols,
                termRows > 0 ? termRows - 4 : 0
        );
        if (img != null) {
            return render(img, dims[0], dims[1]);
        }
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

    // ── Java 렌더링 ──────────────────────────────────────────────────────

    /**
     * 이미지를 outW × outH 문자 격자로 렌더링한다.
     *
     * 각 셀(cell)에 대응하는 픽셀 영역의 평균 RGB를 구한 뒤:
     *   1) 밝기(luminance)로 문자 선택
     *   2) ANSI True-Color 전경색으로 문자 출력
     *   3) 줄 끝에 색상 리셋(\u001B[0m)
     */
    private static String render(BufferedImage img, int outW, int outH) {
        int imgW = img.getWidth();
        int imgH = img.getHeight();

        StringBuilder sb = new StringBuilder(outW * outH * 20);
        sb.append("\u001B[0m");

        for (int row = 0; row < outH; row++) {
            int py0 = (int) Math.round((double)  row      / outH * imgH);
            int py1 = (int) Math.round((double) (row + 1) / outH * imgH);
            py1 = Math.min(py1, imgH);

            for (int col = 0; col < outW; col++) {
                int px0 = (int) Math.round((double)  col      / outW * imgW);
                int px1 = (int) Math.round((double) (col + 1) / outW * imgW);
                px1 = Math.min(px1, imgW);

                // 영역 내 픽셀 평균 RGB
                long sumR = 0, sumG = 0, sumB = 0, count = 0;
                for (int y = py0; y < py1; y++) {
                    for (int x = px0; x < px1; x++) {
                        int rgb = img.getRGB(x, y);
                        sumR += (rgb >> 16) & 0xFF;
                        sumG += (rgb >>  8) & 0xFF;
                        sumB +=  rgb        & 0xFF;
                        count++;
                    }
                }
                if (count == 0) count = 1;

                int r = (int)(sumR / count);
                int g = (int)(sumG / count);
                int b = (int)(sumB / count);

                // ITU-R BT.601 밝기
                double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                int idx = (int)((1.0 - lum) * (DENSITY.length - 1));
                idx = Math.max(0, Math.min(DENSITY.length - 1, idx));

                sb.append("\u001B[38;2;")
                  .append(r).append(';')
                  .append(g).append(';')
                  .append(b).append('m')
                  .append(DENSITY[idx]);
            }

            sb.append("\u001B[0m\n");
        }

        return sb.toString().trim();
    }

    // ── 바이너리 폴백 ────────────────────────────────────────────────────

    private static String convertViaBinary(File imageFile, int termCols) throws IOException {
        int[] dims = calcDimensions(imageFile, termCols);
        return convertViaBinary(imageFile, dims[0], dims[1]);
    }

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

    private static int[] calcDimensions(File imageFile, int termCols) {
        // 바이너리 폴백 경로: ImageIO로 읽지 못했으므로 기본값 사용
        int w = termCols > 0 ? termCols : 80;
        return new int[]{w, w / 2};
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
