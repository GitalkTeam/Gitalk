package com.gitalk.domain.user.repository;

import com.gitalk.domain.user.model.Users;
import com.gitalk.common.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserRepository {

    public Optional<Users> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Users user = new Users.Builder()
                        .userid(rs.getLong("userid"))
                        .email(rs.getString("email"))
                        .password(rs.getString("password"))
                        .build();

                return Optional.of(user);
            }

        } catch (SQLException e) {
            throw new RuntimeException("DB 조회 실패", e);
        }

        return Optional.empty();
    }

    // 이메일 중복 체크
    public boolean existsByEmail(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";

        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            throw new RuntimeException("이메일 중복 체크 실패", e);
        }
        return false;
    }

    // 회원 저장
    public void save(Users user) {
        String sql = "INSERT INTO users (email, password, nickname, profile_url, type) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getEmail());
            pstmt.setString(2, user.getPassword());
            pstmt.setString(3, user.getNickname());
            pstmt.setString(4, user.getProfileUrl());
            pstmt.setString(5, user.getType());

            pstmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("회원 저장 실패", e);
        }
    }
}