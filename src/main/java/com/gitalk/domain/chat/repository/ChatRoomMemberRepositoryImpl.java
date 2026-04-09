package com.gitalk.domain.chat.repository;

import com.gitalk.common.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatRoomMemberRepositoryImpl implements ChatRoomMemberRepository {

    @Override
    public void addMember(Long roomId, Long userId) {
        String sql = "INSERT IGNORE INTO chat_room_members (userid, roomid) VALUES (?, ?)";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, roomId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 멤버 추가 실패", e);
        }
    }

    @Override
    public void removeMember(Long roomId, Long userId) {
        String sql = "DELETE FROM chat_room_members WHERE userid = ? AND roomid = ?";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, roomId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 멤버 제거 실패", e);
        }
    }

    @Override
    public boolean isMember(Long roomId, Long userId) {
        String sql = "SELECT COUNT(*) FROM chat_room_members WHERE userid = ? AND roomid = ?";
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            pstmt.setLong(2, roomId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("멤버 여부 확인 실패", e);
        }
    }

    @Override
    public List<Long> findUserIdsByRoomId(Long roomId) {
        String sql = "SELECT userid FROM chat_room_members WHERE roomid = ?";
        List<Long> result = new ArrayList<>();
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, roomId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) result.add(rs.getLong("userid"));
        } catch (SQLException e) {
            throw new RuntimeException("채팅방 멤버 목록 조회 실패", e);
        }
        return result;
    }

    @Override
    public List<Long> findRoomIdsByUserId(Long userId) {
        String sql = "SELECT roomid FROM chat_room_members WHERE userid = ?";
        List<Long> result = new ArrayList<>();
        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, userId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) result.add(rs.getLong("roomid"));
        } catch (SQLException e) {
            throw new RuntimeException("참여 채팅방 목록 조회 실패", e);
        }
        return result;
    }
}
