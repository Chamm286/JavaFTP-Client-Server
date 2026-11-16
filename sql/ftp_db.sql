-- =====================================================================
-- DATABASE CHO UNG DUNG FTP (XAMPP / MySQL port 3307)
-- Chay: mysql -u root -p --port=3307 < sql/ftp_db.sql
-- Hoac import truc tiep trong phpMyAdmin.
-- LUU Y: mat khau luu plain text theo yeu cau do an (KHONG bam MD5/SHA).
-- =====================================================================

CREATE DATABASE IF NOT EXISTS lab2_ftp_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE lab2_ftp_db;

CREATE TABLE IF NOT EXISTS users (
    id         INT          NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(64)  NOT NULL COMMENT 'plain text',
    fullname   VARCHAR(120) NOT NULL DEFAULT '',
    role       VARCHAR(20)  NOT NULL DEFAULT 'user',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS logs (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    action     VARCHAR(50)  NOT NULL,
    filename   VARCHAR(255) NULL,
    ip_address VARCHAR(45)  NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_logs_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Tai khoan mau (mat khau: 123456)
INSERT INTO users (username, password, fullname, role)
VALUES ('admin', '123456', 'Nguyễn Thị Bích Trâm', 'admin')
ON DUPLICATE KEY UPDATE password = '123456',
                        fullname = 'Nguyễn Thị Bích Trâm',
                        role = 'admin';

INSERT INTO users (username, password, fullname, role)
VALUES ('tri', '123456', 'Trần Văn Tri', 'user')
ON DUPLICATE KEY UPDATE password = '123456',
                        fullname = 'Trần Văn Tri',
                        role = 'user';

SELECT id, username, fullname, role FROM users;