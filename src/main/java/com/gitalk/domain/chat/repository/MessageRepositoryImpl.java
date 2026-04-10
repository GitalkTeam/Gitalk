package com.gitalk.domain.chat.repository;

import com.gitalk.common.util.DBConnection;
import com.gitalk.domain.chat.domain.Message;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MessageRepositoryImpl implements MessageRepository {

    @Override
    public void save(Message message) {
        String sql = "INSERT INTO messages (userid, roomid, content) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, message.getUserId());
            pstmt.setLong(2, message.getRoomId());
            pstmt.setString(3, message.getContent());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("메시지 저장 실패", e);
        }
    }

    @Override
    public List<Message> findByRoomId(Long roomId) {
        return findByRoomId(roomId, Integer.MAX_VALUE);
    }

    @Override
    public List<Message> findByRoomId(Long roomId, int limit) {
        String sql = """
                SELECT m.messageid, m.userid, m.roomid, m.content, m.created_at, u.nickname
                FROM messages m
                LEFT JOIN users u ON m.userid = u.userid
                WHERE m.roomid = ?
                ORDER BY m.created_at DESC
                LIMIT ?
                """;
        List<Message> result = new ArrayList<>();
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, roomId);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("created_at");
                result.add(new Message(
                        rs.getLong("messageid"),
                        rs.getLong("userid"),
                        rs.getLong("roomid"),
                        rs.getString("content"),
                        rs.getString("nickname"),
                        ts != null ? ts.toLocalDateTime() : null
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("메시지 조회 실패", e);
        }
        return result;
    }

    @Override
    public List<Message> findByRoomIdSince(Long roomId, LocalDateTime since, int limit) {
        if (since == null) return Collections.emptyList();
        // since 이후의 최신 limit 개를 가져와서 시간 오름차순으로 뒤집어 반환
        String sql = """
                SELECT m.messageid, m.userid, m.roomid, m.content, m.created_at, u.nickname
                FROM messages m
                LEFT JOIN users u ON m.userid = u.userid
                WHERE m.roomid = ? AND m.created_at > ?
                ORDER BY m.created_at DESC
                LIMIT ?
                """;
        List<Message> recent = new ArrayList<>();
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, roomId);
            pstmt.setTimestamp(2, Timestamp.valueOf(since));
            pstmt.setInt(3, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("created_at");
                recent.add(new Message(
                        rs.getLong("messageid"),
                        rs.getLong("userid"),
                        rs.getLong("roomid"),
                        rs.getString("content"),
                        rs.getString("nickname"),
                        ts != null ? ts.toLocalDateTime() : null
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("미독 메시지 조회 실패", e);
        }
        // desc → asc 뒤집기
        Collections.reverse(recent);
        return recent;
    }
}
