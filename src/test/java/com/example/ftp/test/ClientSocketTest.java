package com.example.ftp.test;

import com.example.ftp.client.FTPClientSocket;
import com.example.ftp.client.RemoteFile;
import com.example.ftp.db.DatabaseManager;
import com.example.ftp.server.FTPServer;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Kiểm thử đúng lớp FTPClientSocket mà UI đang dùng:
 * login admin/tri, upload, list, download, delete.
 */
public class ClientSocketTest {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 2121;

    public static void main(String[] args) throws Exception {
        DatabaseManager db = new DatabaseManager();
        db.initDatabase();

        FTPServer embedded = null;
        if (!portOpen(HOST, PORT)) {
            Path root = Paths.get("server_files").toAbsolutePath().normalize();
            Files.createDirectories(root);
            embedded = new FTPServer(PORT, root, db);
            embedded.start();
            Thread.sleep(1200);
        }

        boolean ok = true;
        try {
            ok &= testUser("admin", "123456");
            ok &= testUser("tri", "123456");
        } finally {
            if (embedded != null) embedded.stop();
        }

        System.out.println(ok ? "\n[CLIENT-TEST] ALL PASSED" : "\n[CLIENT-TEST] FAILED");
        System.exit(ok ? 0 : 1);
    }

    private static boolean testUser(String user, String pass) throws Exception {
        System.out.println("\n[CLIENT-TEST] ===== " + user + " =====");
        FTPClientSocket c = new FTPClientSocket();
        File temp = File.createTempFile("client_test_" + user + "_", ".txt");
        Files.writeString(temp.toPath(), "hello from " + user, StandardCharsets.UTF_8);
        Path downloaded = Files.createTempFile("down_" + user + "_", ".txt");

        try {
            c.connect(HOST, PORT);
            c.login(user, pass);
            System.out.println("  login OK (" + user + ") fullname='" + c.getLoggedInFullname()
                    + "', role='" + c.getLoggedInRole() + "'");

            c.uploadFile(temp, p -> {});
            System.out.println("  upload OK: " + temp.getName());

            List<RemoteFile> files = c.listFiles();
            boolean found = files.stream().anyMatch(f -> f.getName().equals(temp.getName()));
            System.out.println("  list OK: " + files.size() + " mục, thấy file upload = " + found);
            if (!found) return false;

            c.downloadFile(temp.getName(), downloaded.toFile(), p -> {});
            String content = Files.readString(downloaded, StandardCharsets.UTF_8);
            boolean same = content.equals("hello from " + user);
            System.out.println("  download OK, nội dung khớp = " + same);
            if (!same) return false;

            c.deleteFile(temp.getName());
            System.out.println("  delete OK");
            c.disconnect();
            return true;
        } catch (Exception ex) {
            System.out.println("  FAILED: " + ex);
            try { c.close(); } catch (Exception ignored) {}
            return false;
        } finally {
            Files.deleteIfExists(temp.toPath());
            Files.deleteIfExists(downloaded);
        }
    }

    private static boolean portOpen(String host, int port) {
        try (Socket s = new Socket(host, port)) { return true; }
        catch (IOException ex) { return false; }
    }
}