package com.gitalk.domain.chat.repository;

import com.gitalk.common.util.DBConnection;
import com.gitalk.domain.chat.domain.Message;

import java.sql.*;
import java.util.ArrayList;
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
}
