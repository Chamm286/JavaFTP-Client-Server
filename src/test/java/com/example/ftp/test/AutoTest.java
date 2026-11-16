package com.example.ftp.test;

import com.example.ftp.db.DatabaseManager;
import com.example.ftp.server.FTPServer;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Tự động kiểm thử End-to-End toàn bộ hệ thống FTP:
 * 1. Dọn sạch database (bảng logs).
 * 2. Tự khởi chạy Server ngầm (nếu chưa chạy).
 * 3. Dùng Apache Commons Net FTPClient để thực hiện đầy đủ luồng:
 *    - Đăng nhập sai (admin / sai_pass) -> mã 530
 *    - Đăng nhập đúng (admin / 123456)
 *    - Upload file auto_test.txt
 *    - Download file auto_test.txt
 *    - Xóa file auto_test.txt
 *    - Đăng xuất (Logout)
 * 4. Kiểm tra Database đảm bảo đủ đúng 6 dòng log (1 FAIL, 5 SUCCESS).
 * 5. In báo cáo trạng thái ra Console (Xanh lá / Đỏ).
 */
public class AutoTest {

    // Màu ANSI cho Console
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 2121;
    private static final String TEST_FILENAME = "auto_test.txt";
    private static final String TEST_CONTENT = "Hello FTP Server! Day la file test tu dong tao boi AutoTest.java.";

    public static void main(String[] args) {
        System.out.println(CYAN + BOLD + "==========================================================" + RESET);
        System.out.println(CYAN + BOLD + "         BẮT ĐẦU TỰ ĐỘNG KIỂM THỬ HỆ THỐNG FTP (E2E)      " + RESET);
        System.out.println(CYAN + BOLD + "==========================================================" + RESET);

        FTPServer embeddedServer = null;
        DatabaseManager db = new DatabaseManager();

        try {
            // -----------------------------------------------------------
            // BƯỚC 1: KẾT NỐI VÀ DỌN SẠCH DATABASE
            // -----------------------------------------------------------
            printStep(1, "Kết nối Database MySQL port 3307 & dọn sạch bảng logs");
            db.initDatabase();

            try (Connection conn = db.openConnection();
                 Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM logs");
                System.out.println("   -> " + GREEN + "Đã kết nối Database thành công và xóa sạch dữ liệu cũ trong bảng 'logs'." + RESET);
            } catch (Exception ex) {
                fail("Không thể kết nối hoặc dọn sạch bảng logs: " + ex.getMessage());
            }

            // -----------------------------------------------------------
            // BƯỚC 2: KHỞI CHẠY SERVER NGẦM (NẾU CHƯA CHẠY)
            // -----------------------------------------------------------
            printStep(2, "Kiểm tra và khởi chạy FTP Server trên port " + PORT);
            boolean serverAlreadyRunning = isPortInUse(HOST, PORT);

            if (serverAlreadyRunning) {
                System.out.println("   -> " + YELLOW + "Server đã chạy sẵn trên cổng " + PORT + ". Sử dụng Server hiện tại." + RESET);
            } else {
                System.out.println("   -> Server chưa chạy. Đang tự động khởi tạo Server trong luồng riêng...");
                Path rootDir = Paths.get("server_files").toAbsolutePath().normalize();
                Files.createDirectories(rootDir);
                embeddedServer = new FTPServer(PORT, rootDir, db);
                embeddedServer.start();

                System.out.println("   -> Chờ 2 giây để Server khởi động và sẵn sàng nhận kết nối...");
                Thread.sleep(2000);
                System.out.println("   -> " + GREEN + "FTP Server đã chạy ngầm thành công trên port " + PORT + "." + RESET);
            }

            // -----------------------------------------------------------
            // BƯỚC 3: GIẢ LẬP CLIENT BẰNG APACHE COMMONS NET
            // -----------------------------------------------------------
            printStep(3, "Chạy kịch bản Client mô phỏng bằng Apache Commons Net FTPClient");
            FTPClient ftp = new FTPClient();
            ftp.setConnectTimeout(5000);
            ftp.setDefaultTimeout(10000);

            // 3.1: Kết nối
            System.out.print("   [1/7] Kết nối tới " + HOST + ":" + PORT + " ... ");
            ftp.connect(HOST, PORT);
            int reply = ftp.getReplyCode();
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftp.disconnect();
                fail("Server từ chối kết nối! Mã phản hồi: " + reply);
            }
            System.out.println(GREEN + "OK (Mã 220)" + RESET);

            // 3.2: Đăng nhập sai
            System.out.print("   [2/7] Thử đăng nhập SAI (admin / sai_pass) ... ");
            boolean wrongLogin = ftp.login("admin", "sai_pass");
            int wrongCode = ftp.getReplyCode();
            if (wrongLogin) {
                fail("LỖI: Server chấp nhận mật khẩu sai!");
            }
            if (wrongCode != 530) {
                fail("LỖI: Kỳ vọng mã lỗi 530 nhưng nhận được: " + wrongCode);
            }
            System.out.println(GREEN + "Bị từ chối chính xác (Mã 530 Login incorrect)" + RESET);

            // 3.3: Đăng nhập đúng
            System.out.print("   [3/7] Đăng nhập ĐÚNG (admin / 123456) ... ");
            boolean okLogin = ftp.login("admin", "123456");
            int okCode = ftp.getReplyCode();
            if (!okLogin || okCode != 230) {
                fail("Đăng nhập đúng thất bại! Mã phản hồi: " + okCode);
            }
            System.out.println(GREEN + "Thành công (Mã 230 User logged in)" + RESET);

            // Thiết lập Passive Mode & Binary
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            // 3.4: Upload file
            System.out.print("   [4/7] Upload file '" + TEST_FILENAME + "' lên Server ... ");
            byte[] fileBytes = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
            try (ByteArrayInputStream uploadStream = new ByteArrayInputStream(fileBytes)) {
                boolean uploaded = ftp.storeFile(TEST_FILENAME, uploadStream);
                if (!uploaded) {
                    fail("Upload file thất bại! Phản hồi từ Server: " + ftp.getReplyString());
                }
            }
            System.out.println(GREEN + "Upload thành công (Mã 226)" + RESET);

            // 3.5: Download file
            System.out.print("   [5/7] Download file '" + TEST_FILENAME + "' từ Server về ... ");
            ByteArrayOutputStream downloadStream = new ByteArrayOutputStream();
            boolean downloaded = ftp.retrieveFile(TEST_FILENAME, downloadStream);
            if (!downloaded) {
                fail("Download file thất bại! Phản hồi từ Server: " + ftp.getReplyString());
            }
            String downloadedContent = downloadStream.toString(StandardCharsets.UTF_8);
            if (!TEST_CONTENT.equals(downloadedContent)) {
                fail("Nội dung file tải về không khớp nội dung gốc!");
            }
            System.out.println(GREEN + "Download & kiểm tra nội dung toàn vẹn thành công" + RESET);

            // 3.6: Xóa file trên Server
            System.out.print("   [6/7] Xóa file '" + TEST_FILENAME + "' trên Server (DELE) ... ");
            boolean deleted = ftp.deleteFile(TEST_FILENAME);
            if (!deleted) {
                fail("Xóa file trên Server thất bại! Phản hồi: " + ftp.getReplyString());
            }
            System.out.println(GREEN + "Đã xóa file thành công (Mã 250)" + RESET);

            // 3.7: Đăng xuất
            System.out.print("   [7/7] Đăng xuất (QUIT) ... ");
            boolean loggedOut = ftp.logout();
            ftp.disconnect();
            System.out.println(GREEN + "Đã gửi QUIT và đóng kết nối an toàn" + RESET);

            // Chờ 500ms để đảm bảo các thread ghi log đã hoàn tất commit vào DB
            Thread.sleep(500);

            // -----------------------------------------------------------
            // BƯỚC 4: KIỂM TRA TOÀN DIỆN DATABASE BẢNG LOGS
            // -----------------------------------------------------------
            printStep(4, "Kiểm tra Database: Đối soát chính xác 6 dòng nhật ký trong bảng logs");

            List<LogRow> actualLogs = new ArrayList<>();
            try (Connection conn = db.openConnection();
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT username, action, filename, status, created_at FROM logs ORDER BY id ASC")) {

                while (rs.next()) {
                    actualLogs.add(new LogRow(
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("filename"),
                            rs.getString("status"),
                            rs.getString("created_at")
                    ));
                }
            }

            System.out.println(CYAN + "   DANH SÁCH LOGS THỰC TẾ GHI NHẬN TRONG MYSQL:" + RESET);
            System.out.println("   +-----+----------+----------+-------------------+---------+---------------------+");
            System.out.println("   | STT | Username | Action   | Filename          | Status  | Created At          |");
            System.out.println("   +-----+----------+----------+-------------------+---------+---------------------+");

            int idx = 1;
            for (LogRow row : actualLogs) {
                String statusColor = "SUCCESS".equalsIgnoreCase(row.status) ? GREEN : RED;
                System.out.printf("   | %-3d | %-8s | %-8s | %-17s | %s%-7s%s | %-19s |\n",
                        idx++,
                        row.username,
                        row.action,
                        (row.filename == null ? "-" : row.filename),
                        statusColor, row.status, RESET,
                        row.createdAt);
            }
            System.out.println("   +-----+----------+----------+-------------------+---------+---------------------+");

            // Kiểm tra số lượng
            if (actualLogs.size() != 6) {
                fail("Số lượng log không đúng! Kỳ vọng đúng 6 dòng nhưng thực tế có " + actualLogs.size() + " dòng.");
            }

            // Kiểm tra từng dòng theo đúng kịch bản
            assertLogRow(actualLogs.get(0), "admin", "LOGIN", null, "FAIL");
            assertLogRow(actualLogs.get(1), "admin", "LOGIN", null, "SUCCESS");
            assertLogRow(actualLogs.get(2), "admin", "UPLOAD", TEST_FILENAME, "SUCCESS");
            assertLogRow(actualLogs.get(3), "admin", "DOWNLOAD", TEST_FILENAME, "SUCCESS");
            assertLogRow(actualLogs.get(4), "admin", "DELETE", TEST_FILENAME, "SUCCESS");
            assertLogRow(actualLogs.get(5), "admin", "LOGOUT", null, "SUCCESS");

            System.out.println("   -> " + GREEN + "Toàn bộ 6 dòng log đều khớp hoàn hảo (1 FAIL, 5 SUCCESS)." + RESET);

            // -----------------------------------------------------------
            // TẤT CẢ TEST ĐỀU THÀNH CÔNG
            // -----------------------------------------------------------
            System.out.println("\n" + GREEN + BOLD + "======================================================================" + RESET);
            System.out.println(GREEN + BOLD + "   ✅ TẤT CẢ TEST ĐỀU THÀNH CÔNG! HỆ THỐNG HOẠT ĐỘNG HOÀN HẢO.   " + RESET);
            System.out.println(GREEN + BOLD + "======================================================================" + RESET);

        } catch (TestFailureException ex) {
            System.err.println("\n" + RED + BOLD + "======================================================================" + RESET);
            System.err.println(RED + BOLD + "   ❌ KIỂM THỬ THẤT BẠI: " + ex.getMessage() + RESET);
            System.err.println(RED + BOLD + "======================================================================" + RESET);
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("\n" + RED + BOLD + "======================================================================" + RESET);
            System.err.println(RED + BOLD + "   ❌ LỖI NGOẠI LỆ HỆ THỐNG: " + ex.getMessage() + RESET);
            System.err.println(RED + BOLD + "======================================================================" + RESET);
            ex.printStackTrace();
            System.exit(1);
        } finally {
            if (embeddedServer != null && embeddedServer.isRunning()) {
                System.out.println("\n" + YELLOW + "-> Đang dừng FTP Server ngầm..." + RESET);
                embeddedServer.stop();
                System.out.println(GREEN + "-> Đã dọn dẹp và dừng Server ngầm thành công." + RESET);
            }
            try {
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void printStep(int num, String desc) {
        System.out.println("\n" + YELLOW + BOLD + ">> BƯỚC " + num + ": " + desc + RESET);
    }

    private static void fail(String message) {
        throw new TestFailureException(message);
    }

    private static void assertLogRow(LogRow row, String expUser, String expAction, String expFile, String expStatus) {
        if (!expUser.equalsIgnoreCase(row.username) || !expAction.equalsIgnoreCase(row.action) || !expStatus.equalsIgnoreCase(row.status)) {
            fail(String.format("Log sai! Kỳ vọng: [%s, %s, %s, %s] nhưng nhận được: [%s, %s, %s, %s]",
                    expUser, expAction, expFile, expStatus,
                    row.username, row.action, row.filename, row.status));
        }
        if (expFile != null && !expFile.equals(row.filename)) {
            fail(String.format("Log sai tên file! Kỳ vọng '%s' nhưng nhận được '%s'", expFile, row.filename));
        }
    }

    private static boolean isPortInUse(String host, int port) {
        try (Socket s = new Socket(host, port)) {
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static class LogRow {
        final String username;
        final String action;
        final String filename;
        final String status;
        final String createdAt;

        LogRow(String username, String action, String filename, String status, String createdAt) {
            this.username = username;
            this.action = action;
            this.filename = filename;
            this.status = status;
            this.createdAt = createdAt;
        }
    }

    private static class TestFailureException extends RuntimeException {
        TestFailureException(String message) {
            super(message);
        }
    }
}
