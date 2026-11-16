package com.example.ftp.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Quản lý kết nối MySQL (XAMPP) và các thao tác với database.
 *
 * <p>Database: {@code lab2_ftp_db}. Bảng {@code users} lưu mật khẩu dạng
 * plain text (theo yêu cầu đồ án, KHÔNG băm) và gồm các cột:
 * id, username, password, fullname, role.</p>
 */
public class DatabaseManager {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3307/lab2_ftp_db"
                    + "?useUnicode=true&characterEncoding=UTF-8"
                    + "&useSSL=false&allowPublicKeyRetrieval=true"
                    + "&serverTimezone=Asia/Ho_Chi_Minh";

    private final String url;
    private final String username;
    private final String password;
    private volatile String lastError = "";

    public DatabaseManager() {
        Properties props = loadProperties();

        this.url = firstNonBlank(
                System.getProperty("ftp.db.url"),
                System.getenv("FTP_DB_URL"),
                props.getProperty("db.url"),
                DEFAULT_URL);

        this.username = firstNonBlank(
                System.getProperty("ftp.db.username"),
                System.getenv("FTP_DB_USER"),
                props.getProperty("db.username"),
                "root");

        this.password = firstNonBlank(
                System.getProperty("ftp.db.password"),
                System.getenv("FTP_DB_PASSWORD"),
                props.getProperty("db.password"),
                "");
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Tự động tạo database, bảng và chèn dữ liệu mẫu.
     *
     * <p>Mật khẩu lưu thẳng (plain text), KHÔNG dùng MD5/SHA.</p>
     */
    public void initDatabase() {
        String dbName = databaseName();
        String base = baseUrl();
        try (Connection conn = DriverManager.getConnection(base, username, password);
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            System.out.println("[DB] Database '" + dbName + "' đã sẵn sàng.");
        } catch (SQLException ex) {
            System.err.println("[DB] Không tạo được database: " + ex.getMessage());
            return;
        }

        try (Connection conn = openConnection();
             Statement st = conn.createStatement()) {

            // Bảng users: id, username, password (plain), fullname, role
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id INT NOT NULL AUTO_INCREMENT, "
                    + "username VARCHAR(50) NOT NULL, "
                    + "password VARCHAR(64) NOT NULL, "
                    + "fullname VARCHAR(120) NOT NULL DEFAULT '', "
                    + "role VARCHAR(20) NOT NULL DEFAULT 'user', "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (id), UNIQUE KEY uk_users_username (username)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // Tương thích database cũ (nếu đã tạo trước đây thiếu cột).
            addColumnIfMissing(conn, "users", "fullname",
                    "VARCHAR(120) NOT NULL DEFAULT ''");
            addColumnIfMissing(conn, "users", "role",
                    "VARCHAR(20) NOT NULL DEFAULT 'user'");

            st.execute("CREATE TABLE IF NOT EXISTS logs ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT, "
                    + "username VARCHAR(50) NOT NULL, "
                    + "action VARCHAR(50) NOT NULL, "
                    + "filename VARCHAR(255) NULL, "
                    + "ip_address VARCHAR(45) NULL, "
                    + "status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS', "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (id), KEY idx_logs_created (created_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // Dữ liệu mẫu CHÍNH XÁC, mật khẩu thô 123456.
            st.execute("INSERT INTO users (username, password, fullname, role) VALUES "
                    + "('admin', '123456', 'Nguyễn Thị Bích Trâm', 'admin') "
                    + "ON DUPLICATE KEY UPDATE password='123456', "
                    + "fullname='Nguyễn Thị Bích Trâm', role='admin'");

            st.execute("INSERT INTO users (username, password, fullname, role) VALUES "
                    + "('tri', '123456', 'Trần Văn Tri', 'user') "
                    + "ON DUPLICATE KEY UPDATE password='123456', "
                    + "fullname='Trần Văn Tri', role='user'");

            System.out.println("[DB] Đã tạo bảng users/logs và seed admin/tri (mật khẩu 123456).");
        } catch (SQLException ex) {
            System.err.println("[DB] Lỗi khởi tạo bảng: " + ex.getMessage());
        }
    }

    private void addColumnIfMissing(Connection conn, String table, String column, String definition) {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition);
        } catch (SQLException ignored) {
            // Cột đã tồn tại -> bỏ qua.
        }
    }

    /** So sánh mật khẩu plain text trực tiếp. */
    public boolean checkLogin(String username, String password) throws SQLException {
        if (username == null || username.isBlank()) return false;
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String stored = rs.getString("password");
                if (stored == null) return false;
                String plain = password == null ? "" : password;
                return stored.equals(plain);
            }
        }
    }

    /** @return role (admin/user) của username, mặc định "user". */
    public String getPermission(String username) {
        String sql = "SELECT role FROM users WHERE username = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("role");
            }
        } catch (SQLException ex) {
            System.err.println("[DB] Không đọc được role: " + ex.getMessage());
        }
        return "user";
    }

    public String getFullname(String username) {
        String sql = "SELECT fullname FROM users WHERE username = ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String fullname = rs.getString("fullname");
                    if (fullname != null && !fullname.isBlank()) return fullname;
                }
            }
        } catch (SQLException ex) {
            System.err.println("[DB] Không đọc được fullname: " + ex.getMessage());
        }
        return username;
    }

    public void logAction(String username, String action, String filename) {
        logAction(username, action, filename, null, "SUCCESS");
    }

    public void writeLog(String username, String action, String filename, String ip, String status) {
        logAction(username, action, filename, ip, status);
    }

    public void writeLog(String username, String action, String filename) {
        logAction(username, action, filename, null, "SUCCESS");
    }

    public void logAction(String username, String action, String filename, String ip, String status) {
        String sql = "INSERT INTO logs (username, action, filename, ip_address, status) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username == null || username.isBlank() ? "unknown" : username);
            ps.setString(2, action == null ? "UNKNOWN" : action.toUpperCase(Locale.ROOT));
            ps.setString(3, filename);
            ps.setString(4, ip);
            ps.setString(5, status == null ? "SUCCESS" : status);
            ps.executeUpdate();
        } catch (SQLException ex) {
            System.err.println("[DB] Không ghi được log: " + ex.getMessage());
        }
    }

    public List<String> getLogs() {
        List<String> result = new ArrayList<>();
        for (LogRow row : getLogRows()) {
            result.add("[" + row.createdAt() + "] " + row.username() + " | " + row.action()
                    + " | " + (row.filename() == null ? "-" : row.filename())
                    + " | " + (row.ip() == null ? "-" : row.ip())
                    + " | " + row.status());
        }
        return result;
    }

    public List<LogRow> getLogRows() {
        return getLogRows(200);
    }

    public List<LogRow> getLogRows(int limit) {
        List<LogRow> result = new ArrayList<>();
        String sql = "SELECT username, action, filename, ip_address, status, created_at "
                + "FROM logs ORDER BY id DESC LIMIT ?";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new LogRow(
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("filename"),
                            rs.getString("ip_address"),
                            rs.getString("status"),
                            String.valueOf(rs.getTimestamp("created_at"))));
                }
            }
        } catch (SQLException ex) {
            result.add(new LogRow("DB_ERROR", ex.getMessage(), null, null, "FAIL", ""));
        }
        return result;
    }

    public long countLogsToday() {
        String sql = "SELECT COUNT(*) FROM logs WHERE DATE(created_at) = CURDATE()";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getLong(1);
        } catch (SQLException ex) {
            System.err.println("[DB] countLogsToday: " + ex.getMessage());
        }
        return 0;
    }

    public List<UserRow> getAllUsers() {
        List<UserRow> result = new ArrayList<>();
        String sql = "SELECT id, username, role, created_at FROM users ORDER BY id";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new UserRow(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        String.valueOf(rs.getTimestamp("created_at"))));
            }
        } catch (SQLException ex) {
            System.err.println("[DB] getAllUsers: " + ex.getMessage());
        }
        return result;
    }

    public void addUser(String user, String plainPassword, String role) throws SQLException {
        String sql = "INSERT INTO users (username, password, fullname, role) VALUES (?, ?, ?, ?)";
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user);
            ps.setString(2, plainPassword == null ? "" : plainPassword);
            ps.setString(3, user);
            ps.setString(4, role == null ? "user" : role);
            ps.executeUpdate();
        }
    }

    public void updateUser(int id, String role, String newPlainPassword) throws SQLException {
        if (newPlainPassword == null || newPlainPassword.isBlank()) {
            String sql = "UPDATE users SET role = ? WHERE id = ?";
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, role);
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        } else {
            String sql = "UPDATE users SET role = ?, password = ? WHERE id = ?";
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, role);
                ps.setString(2, newPlainPassword);
                ps.setInt(3, id);
                ps.executeUpdate();
            }
        }
    }

    public void deleteUser(int id) throws SQLException {
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public String getUrl() {
        return url;
    }

    public String databaseName() {
        int q = url.indexOf('?');
        String noQuery = q < 0 ? url : url.substring(0, q);
        int slash = noQuery.lastIndexOf('/');
        return slash < 0 ? "lab2_ftp_db" : noQuery.substring(slash + 1);
    }

    public boolean testConnection() {
        try (Connection conn = openConnection()) {
            boolean ok = conn.isValid(3);
            if (ok) lastError = "";
            return ok;
        } catch (SQLException ex) {
            lastError = ex.getMessage();
            System.err.println("[DB] Kết nối thất bại: " + ex.getMessage());
            return false;
        }
    }

    public String getLastError() {
        return lastError;
    }

    private String baseUrl() {
        int hostEnd = url.indexOf('/', url.indexOf("//") + 2);
        String base = (hostEnd < 0) ? url : url.substring(0, hostEnd) + "/";
        if (!base.contains("?") && url.contains("?")) {
            base += url.substring(url.indexOf('?'));
        }
        return base;
    }

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = DatabaseManager.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ex) {
            System.err.println("[DB] Không đọc được db.properties: " + ex.getMessage());
        }
        return props;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    public record UserRow(int id, String username, String permission, String createdAt) {}
    public record LogRow(String username, String action, String filename, String ip, String status, String createdAt) {}
}