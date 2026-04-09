package com.gitalk.domain.chat.repository;

import com.gitalk.common.util.DBConnection;
import com.gitalk.domain.chat.domain.ChatRoom;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ChatRoomRepositoryImpl implements ChatRoomRepository {

    private static final String SELECT_BASE = """
            SELECT r.roomid, r.name, r.type, r.team_url, r.creator_id, r.created_at,
                   u.nickname AS creator_nickname
            FROM chat_rooms r
            LEFT JOIN users u ON r.creator_id = u.userid
            """;

    @Override
    public ChatRoom save(ChatRoom room) {
        String sql = "INSERT INTO chat_rooms (name, type, team_url, creator_id) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, room.getName());
            pstmt.setString(2, room.getType());
            pstmt.setString(3, room.getTeamUrl());
            if (room.getCreatorId() != null) pstmt.setLong(4, room.getCreatorId());
            else pstmt.setNull(4, java.sql.Types.BIGINT);
            pstmt.executeUpdate();
            ResultSet keys = pstmt.getGeneratedKeys();
            if (keys.next()) {
                return findById(keys.getLong(1)).orElseThrow();
            }
            throw new RuntimeException("채팅방 저장 후 ID 조회 실패");
        } catch (SQLIntegrityConstraintViolationException e) {
            // 함수형 유니크 인덱스(uq_open_room_name)에 걸린 경우 — 서비스 레이어가 친화적 메시지로 변환할 수 있도록 그대로 던짐
            throw new RuntimeException("채팅방 저장 실패: 중복된 이름", e);
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 저장 실패", e);
        }
    }

    @Override
    public Optional<ChatRoom> findById(Long roomId) {
        String sql = SELECT_BASE + " WHERE r.roomid = ?";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, roomId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return Optional.of(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 조회 실패", e);
        }
        return Optional.empty();
    }

    @Override
    public List<ChatRoom> findAll() {
        String sql = SELECT_BASE + " ORDER BY r.created_at DESC";
        List<ChatRoom> result = new ArrayList<>();
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 목록 조회 실패", e);
        }
        return result;
    }

    @Override
    public List<ChatRoom> findByType(String type) {
        String sql = SELECT_BASE + " WHERE r.type = ? ORDER BY r.created_at DESC";
        List<ChatRoom> result = new ArrayList<>();
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, type);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) result.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 타입 조회 실패", e);
        }
        return result;
    }

    @Override
    public boolean existsByTypeAndName(String type, String name) {
        String sql = "SELECT 1 FROM chat_rooms WHERE type = ? AND name = ? LIMIT 1";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, type);
            pstmt.setString(2, name);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 이름 중복 확인 실패", e);
        }
    }

    @Override
    public void deleteById(Long roomId) {
        String sql = "DELETE FROM chat_rooms WHERE roomid = ?";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, roomId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 삭제 실패", e);
        }
    }

    private ChatRoom mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        long creatorIdRaw = rs.getLong("creator_id");
        Long creatorId = rs.wasNull() ? null : creatorIdRaw;
        return new ChatRoom(
                rs.getLong("roomid"),
                rs.getString("name"),
                rs.getString("type"),
                rs.getString("team_url"),
                creatorId,
                rs.getString("creator_nickname"),
                ts != null ? ts.toLocalDateTime() : null
        );
    }
}
