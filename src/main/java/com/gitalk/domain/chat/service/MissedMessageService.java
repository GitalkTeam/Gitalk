package com.gitalk.domain.chat.service;

import com.gitalk.common.api.OpenAIClient;
import com.gitalk.domain.chat.domain.Message;
import com.gitalk.domain.chat.repository.ChatRoomMemberRepository;
import com.gitalk.domain.chat.repository.MessageRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 미독 메시지(놓친 메시지) 조회·요약·다운로드 서비스.
 *
 * - 기준 시각: chat_room_members.last_seen_at
 * - 메시지 본문: Mongo chat_messages (사용자 발언만 저장됨)
 * - AI 요약: OpenAIClient
 * - 다운로드: ~/Downloads 고정
 */
public class MissedMessageService {

    /** 화면 표시(/missed) 한도 */
    public static final int VIEW_LIMIT_COUNT = 200;
    public static final int VIEW_LIMIT_HOURS = 24 * 7;        // 7일

    /** 다운로드(/missed save) 한도 */
    public static final int EXPORT_LIMIT_COUNT = 5000;
    public static final int EXPORT_LIMIT_HOURS = 24 * 30;     // 30일

    private static final DateTimeFormatter TS_FMT       = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_SHORT   = DateTimeFormatter.ofPattern("MM/dd HH:mm");
    private static final DateTimeFormatter FILENAME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final MessageRepository messageRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final OpenAIClient openAIClient;

    public MissedMessageService(MessageRepository messageRepository,
                                ChatRoomMemberRepository memberRepository,
                                OpenAIClient openAIClient) {
        this.messageRepository = messageRepository;
        this.memberRepository  = memberRepository;
        this.openAIClient      = openAIClient;
    }

    // ── 조회 ───────────────────────────────────────────────────────────────

    /** 마지막 접속 시각이 없으면 빈 Optional. */
    public Optional<LocalDateTime> getLastSeen(Long userId, Long roomId) {
        return memberRepository.getLastSeen(userId, roomId);
    }

    /** 화면 표시용: 최근 200건 / 7일 안의 미독 메시지 (시간 오름차순) */
    public List<Message> findMissedForView(Long userId, Long roomId) {
        return findMissed(userId, roomId, VIEW_LIMIT_COUNT, VIEW_LIMIT_HOURS);
    }

    /** 다운로드용: 최근 5000건 / 30일 안의 미독 메시지 (시간 오름차순) */
    public List<Message> findMissedForExport(Long userId, Long roomId) {
        return findMissed(userId, roomId, EXPORT_LIMIT_COUNT, EXPORT_LIMIT_HOURS);
    }

    private List<Message> findMissed(Long userId, Long roomId, int countLimit, int hoursLimit) {
        Optional<LocalDateTime> last = memberRepository.getLastSeen(userId, roomId);
        if (last.isEmpty()) {
            return List.of();
        }
        // 시간 한도 적용: max(last_seen_at, now - hoursLimit)
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hoursLimit);
        LocalDateTime since  = last.get().isAfter(cutoff) ? last.get() : cutoff;
        return messageRepository.findByRoomIdSince(roomId, since, countLimit);
    }

    // ── 갱신 ───────────────────────────────────────────────────────────────

    /** 사용자가 방에서 socket 종료 시 호출 (클라/서버 어디서든). */
    public void touchLastSeen(Long userId, Long roomId) {
        memberRepository.updateLastSeen(userId, roomId, LocalDateTime.now());
    }

    // ── AI 요약 ────────────────────────────────────────────────────────────

    /** OpenAI 호출로 메시지 묶음을 한국어로 요약. 호출 실패 시 RuntimeException. */
    public String summarizeWithAI(List<Message> messages, String roomName) {
        if (messages == null || messages.isEmpty()) {
            return "요약할 메시지가 없습니다.";
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("다음은 채팅방 \"").append(roomName == null ? "(이름 없음)" : roomName)
              .append("\"에서 사용자가 자리를 비운 사이 오간 메시지 ")
              .append(messages.size()).append("건입니다.\n");
        prompt.append("주요 내용을 한국어로 3~6개의 짧은 항목(불릿)으로 요약해 주세요.\n");
        prompt.append("- 누가 무엇을 말했는지 명확히 (이름 포함)\n");
        prompt.append("- 결정사항이나 질문은 강조\n");
        prompt.append("- 불필요한 인사·잡담은 압축\n\n");
        prompt.append("[메시지 목록]\n");
        for (Message m : messages) {
            String time = m.getCreatedAt() != null ? m.getCreatedAt().format(TIME_SHORT) : "";
            String nick = m.getSenderNickname() != null ? m.getSenderNickname() : "unknown";
            prompt.append(time).append(" ").append(nick).append(": ").append(m.getContent()).append("\n");
        }

        try {
            return openAIClient.analyze(prompt.toString());
        } catch (Exception e) {
            throw new RuntimeException("AI 요약 호출 실패: " + e.getMessage(), e);
        }
    }

    // ── 포맷 / 다운로드 ────────────────────────────────────────────────────

    public String formatPlain(List<Message> messages, String roomName, LocalDateTime since) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Gitalk: ").append(roomName == null ? "(이름 없음)" : roomName).append(" ===\n");
        if (since != null) {
            sb.append("기준: ").append(since.format(TS_FMT)).append(" 이후\n");
        }
        sb.append("총 메시지: ").append(messages.size()).append("건\n\n");
        for (Message m : messages) {
            String time = m.getCreatedAt() != null ? m.getCreatedAt().format(TS_FMT) : "(시간 없음)";
            String nick = m.getSenderNickname() != null ? m.getSenderNickname() : "unknown";
            sb.append("[").append(time).append("] ").append(nick).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    public String formatMarkdown(List<Message> messages, String roomName, LocalDateTime since) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 📥 Gitalk: ").append(roomName == null ? "(이름 없음)" : roomName).append(" — 놓친 메시지\n\n");
        if (since != null) {
            sb.append("**기준 시각**: ").append(since.format(TS_FMT)).append(" 이후  \n");
        }
        sb.append("**총 메시지**: ").append(messages.size()).append("건\n\n");
        sb.append("---\n\n");
        for (Message m : messages) {
            String time = m.getCreatedAt() != null ? m.getCreatedAt().format(TS_FMT) : "(시간 없음)";
            String nick = m.getSenderNickname() != null ? m.getSenderNickname() : "unknown";
            sb.append("### ").append(nick).append(" — ").append(time).append("\n");
            sb.append(m.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * ~/Downloads 에 파일 저장. 디렉터리가 없으면 생성.
     * 파일명은 caller 가 만들지 않고 이 메서드가 자동 생성.
     *
     * @param messages 저장할 메시지 (최소 1건)
     * @param roomName 방 이름 (파일명·헤더용)
     * @param format   "txt" 또는 "md"
     * @return 저장된 파일의 절대 경로
     */
    public Path saveToDownloads(List<Message> messages, String roomName, String format) throws IOException {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("저장할 메시지가 없습니다.");
        }
        String fmt = format == null ? "txt" : format.toLowerCase().trim();
        if (!"txt".equals(fmt) && !"md".equals(fmt)) {
            throw new IllegalArgumentException("지원하지 않는 포맷: " + format + " (txt | md)");
        }

        LocalDateTime from = messages.get(0).getCreatedAt();
        LocalDateTime to   = messages.get(messages.size() - 1).getCreatedAt();
        String fromTag = from != null ? from.format(FILENAME_FMT) : "unknown";
        String toTag   = to   != null ? to.format(FILENAME_FMT)   : "unknown";

        String safeRoom = sanitizeForFilename(roomName);
        String filename = "gitalk_" + safeRoom + "_" + fromTag + "_" + toTag + "." + fmt;

        Path downloads = Paths.get(System.getProperty("user.home"), "Downloads");
        if (!Files.exists(downloads)) {
            Files.createDirectories(downloads);
        }
        Path target = downloads.resolve(filename);

        String body = "md".equals(fmt)
                ? formatMarkdown(messages, roomName, from)
                : formatPlain(messages, roomName, from);
        Files.writeString(target, body, StandardCharsets.UTF_8);
        return target;
    }

    /** 파일명에 한글/영문/숫자/대시/언더스코어만 남기고 나머지는 _ 로 치환 */
    private static String sanitizeForFilename(String name) {
        if (name == null || name.isBlank()) return "room";
        String trimmed = name.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length(); ) {
            int cp = trimmed.codePointAt(i);
            if ((cp >= '0' && cp <= '9')
                    || (cp >= 'A' && cp <= 'Z')
                    || (cp >= 'a' && cp <= 'z')
                    || cp == '_' || cp == '-'
                    || (cp >= 0xAC00 && cp <= 0xD7A3)) {  // 한글 완성형
                sb.appendCodePoint(cp);
            } else {
                sb.append('_');
            }
            i += Character.charCount(cp);
        }
        return sb.length() > 0 ? sb.toString() : "room";
    }
}
