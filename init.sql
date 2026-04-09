-- DB가 없으면 생성
CREATE DATABASE IF NOT EXISTS gitalkdb
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 사용할 DB 선택
USE gitalkdb;

-- users
CREATE TABLE IF NOT EXISTS users (
                                     userid       BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     email        VARCHAR(255) NOT NULL UNIQUE,
    password     VARCHAR(255),
    nickname     VARCHAR(100),
    profile_url  VARCHAR(255),
    type         VARCHAR(50),
    github_id    BIGINT NULL UNIQUE,
    auth_access_token VARCHAR(255) NULL,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

-- chat_rooms
-- OPEN 타입은 같은 이름으로 중복 생성 불가 (TEAM은 자유)
-- 함수형 유니크 인덱스: TEAM 행은 NULL로 평가되어 중복 허용
CREATE TABLE IF NOT EXISTS chat_rooms (
                                          roomid     BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          name       VARCHAR(255) NOT NULL,
    type       ENUM('TEAM', 'OPEN') NOT NULL,
    team_url   VARCHAR(255),
    creator_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_open_room_name ((CASE WHEN type = 'OPEN' THEN name END)),
    FOREIGN KEY (creator_id) REFERENCES users(userid) ON DELETE SET NULL
    );

-- chat_room_members
CREATE TABLE IF NOT EXISTS chat_room_members (
                                                 id      BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                 userid  BIGINT NOT NULL,
                                                 roomid  BIGINT NOT NULL,
                                                 UNIQUE KEY uq_user_room (userid, roomid),
    FOREIGN KEY (userid) REFERENCES users(userid) ON DELETE CASCADE,
    FOREIGN KEY (roomid) REFERENCES chat_rooms(roomid) ON DELETE CASCADE
    );

-- messages
CREATE TABLE IF NOT EXISTS messages (
                                        messageid  BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        userid     BIGINT NOT NULL,
                                        roomid     BIGINT NOT NULL,
                                        content    TEXT,
                                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                        FOREIGN KEY (userid) REFERENCES users(userid) ON DELETE CASCADE,
    FOREIGN KEY (roomid) REFERENCES chat_rooms(roomid) ON DELETE CASCADE
    );

-- topics
CREATE TABLE IF NOT EXISTS topics (
                                      topicid    BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      topicname  VARCHAR(100) NOT NULL
    );

-- chatroom_topics
CREATE TABLE IF NOT EXISTS chatroom_topics (
                                               id       BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               topicid  BIGINT NOT NULL,
                                               roomid   BIGINT NOT NULL,
                                               UNIQUE KEY uq_topic_room (topicid, roomid),
    FOREIGN KEY (topicid) REFERENCES topics(topicid) ON DELETE CASCADE,
    FOREIGN KEY (roomid) REFERENCES chat_rooms(roomid) ON DELETE CASCADE
    );

-- notices
CREATE TABLE IF NOT EXISTS notices (
                                       noticeid   BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       userid     BIGINT NOT NULL,
                                       roomid     BIGINT NOT NULL,
                                       title      VARCHAR(255),
    content    TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (userid) REFERENCES users(userid) ON DELETE CASCADE,
    FOREIGN KEY (roomid) REFERENCES chat_rooms(roomid) ON DELETE CASCADE
    );