package com.gitalk.domain.user.repository;

import com.gitalk.common.util.DBConnection;
import com.gitalk.domain.user.model.Users;

import java.sql.*;
import java.util.Optional;

public class UserRepository {

    public Optional<Users> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("DB 조회 실패", e);
        }
        return Optional.empty();
    }

    public Optional<Users> findByGithubId(Long githubId) {
        String sql = "SELECT * FROM users WHERE github_id = ?";

        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, githubId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("GitHub 사용자 조회 실패", e);
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
        String sql = """
            INSERT INTO users
            (github_id, email, password, nickname, profile_url, type, auth_access_token)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DBConnection.makeConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (user.getGithubId() != null) {
                pstmt.setLong(1, user.getGithubId());
            } else {
                pstmt.setNull(1, java.sql.Types.BIGINT);
            }

            pstmt.setString(2, user.getEmail());

            if (user.getPassword() != null) {
                pstmt.setString(3, user.getPassword());
            } else {
                pstmt.setNull(3, java.sql.Types.VARCHAR);
            }

            pstmt.setString(4, user.getNickname());
            pstmt.setString(5, user.getProfileUrl());
            pstmt.setString(6, user.getType());

            if (user.getAuthAccessToken() != null) {
                pstmt.setString(7, user.getAuthAccessToken());
            } else {
                pstmt.setNull(7, java.sql.Types.VARCHAR);
            }

            pstmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("회원 저장 실패", e);
        }
    }

    private Users map(ResultSet rs) throws SQLException {
        return new Users.Builder()
                .userid(rs.getLong("userid"))
                .githubId(rs.getObject("github_id") != null ? rs.getLong("github_id") : null)
                .email(rs.getString("email"))
                .password(rs.getString("password"))
                .nickname(rs.getString("nickname"))
                .profileUrl(rs.getString("profile_url"))
                .type(rs.getString("type"))
                .authAccessToken(rs.getString("auth_access_token"))
                .build();
    }
}