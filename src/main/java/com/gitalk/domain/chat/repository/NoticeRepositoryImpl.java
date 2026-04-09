package com.gitalk.domain.chat.repository;

import com.gitalk.common.util.DBConnection;
import com.gitalk.domain.chat.domain.Notice;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NoticeRepositoryImpl implements NoticeRepository {

    @Override
    public Notice save(Notice notice) {
        String sql = "INSERT INTO notices (userid, roomid, title, content) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, notice.getUserId());
            pstmt.setLong(2, notice.getRoomId());
            pstmt.setString(3, notice.getTitle());
            pstmt.setString(4, notice.getContent());
            pstmt.executeUpdate();
            ResultSet keys = pstmt.getGeneratedKeys();
            if (keys.next()) return findById(keys.getLong(1)).orElseThrow();
            throw new RuntimeException("공지 저장 후 ID 조회 실패");
        } catch (SQLException e) {
            throw new RuntimeException("공지 저장 실패", e);
        }
    }

    @Override
    public Optional<Notice> findById(Long noticeId) {
        String sql = """
                SELECT n.*, u.nickname FROM notices n
                LEFT JOIN users u ON n.userid = u.userid
                WHERE n.noticeid = ?
                """;
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, noticeId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("공지 조회 실패", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Notice> findByRoomId(Long roomId) {
        String sql = """
                SELECT n.*, u.nickname FROM notices n
                LEFT JOIN users u ON n.userid = u.userid
                WHERE n.roomid = ?
                ORDER BY n.created_at DESC
                """;
        List<Notice> result = new ArrayList<>();
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, roomId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("공지 목록 조회 실패", e);
        }
        return result;
    }

    @Override
    public List<Notice> findRecentByUserId(Long userId, int limit) {
        String sql = """
                SELECT n.*, u.nickname FROM notices n
                LEFT JOIN users u ON n.userid = u.userid
                WHERE n.roomid IN (SELECT roomid FROM chat_room_members WHERE userid = ?)
                ORDER BY n.created_at DESC
                LIMIT ?
                """;
        List<Notice> result = new ArrayList<>();
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("공지 조회 실패", e);
        }
        return result;
    }

    private Notice mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return new Notice(
                rs.getLong("noticeid"),
                rs.getLong("userid"),
                rs.getLong("roomid"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("nickname"),
                ts != null ? ts.toLocalDateTime() : null
        );
    }
}
