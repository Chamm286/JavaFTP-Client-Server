package com.example.ftp.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quản lý Socket FTP phía Client.
 *
 * <p>Cung cấp các hàm gửi lệnh, đọc phản hồi và các thao tác FTP quan trọng:
 * connect, login, listFiles, uploadFile, downloadFile. Tất cả các lệnh đều được
 * đồng bộ hoá để an toàn khi chạy nền.</p>
 */
public class FTPClientSocket {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int DATA_TIMEOUT_MS = 60_000;
    private static final int BUFFER_SIZE = 64 * 1024;

    private static final Pattern PASV_PATTERN =
            Pattern.compile("\\((\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3}),(\\d{1,3})\\)");
    private static final Pattern UNIX_LIST = Pattern.compile(
            "^([-dl])([rwxst-]{9})\\s+\\d+\\s+\\S+\\s+\\S+\\s+(\\d+)\\s+"
                    + "([A-Za-z]{3}\\s+\\d{1,2}\\s+(?:\\d{1,2}:\\d{2}|\\d{4}))\\s+(.+)$");

    private final ReentrantLock lock = new ReentrantLock();

    private volatile Socket controlSocket;
    private volatile BufferedReader reader;
    private volatile BufferedWriter writer;

    private String host = "127.0.0.1";
    private int port = 2121;
    private volatile boolean connected = false;

    private volatile String loggedInFullname = "";
    private volatile String loggedInRole = "user";

    public String getLoggedInFullname() { return loggedInFullname; }
    public String getLoggedInRole() { return loggedInRole; }

    public FTPClientSocket() {
    }

    public FTPClientSocket(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean isConnected() {
        return connected && controlSocket != null && !controlSocket.isClosed();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    // -----------------------------------------------------------------
    // Kết nối / đăng nhập
    // -----------------------------------------------------------------

    public void connect() throws IOException {
        connect(host, port);
    }

    public void connect(String host, int port) throws IOException {
        lock.lock();
        try {
            closeQuietly();
            this.host = host;
            this.port = port;

            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            controlSocket = socket;
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // Tạm thời đánh dấu đã kết nối để readResponse() có thể đọc greeting.
            connected = true;
            FtpResponse greeting = readResponse();
            if (greeting.getCode() != 220) {
                closeQuietly();
                throw new IOException("Server không gửi mã 220: " + greeting);
            }
        } finally {
            lock.unlock();
        }
    }

    public void login(String username, String password) throws IOException {
        lock.lock();
        try {
            ensureConnected();
            FtpResponse userResp = sendAndReceive("USER " + username);
            if (userResp.getCode() != 331 && userResp.getCode() != 230) {
                throw new IOException("USER thất bại: " + userResp);
            }
            if (userResp.getCode() == 331) {
                FtpResponse passResp = sendAndReceive("PASS " + password);
                if (passResp.getCode() != 230) {
                    throw new IOException("PASS thất bại: " + passResp);
                }
                parseWelcome(passResp.getMessage());
            } else {
                parseWelcome(userResp.getMessage());
            }
            // Chuyển sang binary mode ngay từ đầu.
            sendAndReceive("TYPE I");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Server trả về: "Login successful. Welcome, Nguyễn Thị Bích Trâm (admin)."
     * Tách fullname + role để hiển thị trên Client mà không cần kết nối MySQL.
     */
    private void parseWelcome(String message) {
        if (message == null) return;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("Welcome,\\s*(.*?)\\s*\\((\\w+)\\)")
                .matcher(message);
        if (m.find()) {
            loggedInFullname = m.group(1).trim();
            loggedInRole = m.group(2).trim();
        }
    }

    public void disconnect() throws IOException {
        lock.lock();
        try {
            if (connected) {
                try {
                    sendAndReceive("QUIT");
                } catch (IOException ignored) {
                }
            }
        } finally {
            lock.unlock();
        }
        closeQuietly();
    }

    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        connected = false;
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {
        }
        try {
            if (reader != null) reader.close();
        } catch (IOException ignored) {
        }
        try {
            if (controlSocket != null) controlSocket.close();
        } catch (IOException ignored) {
        }
        writer = null;
        reader = null;
        controlSocket = null;
    }

    // -----------------------------------------------------------------
    // Gửi lệnh / đọc phản hồi
    // -----------------------------------------------------------------

    public void sendCommand(String command) throws IOException {
        if (command == null) {
            throw new IllegalArgumentException("Lệnh không được null.");
        }
        if (command.contains("\r") || command.contains("\n")) {
            throw new IllegalArgumentException("Lệnh FTP không được chứa ký tự xuống dòng.");
        }
        lock.lock();
        try {
            ensureConnected();
            BufferedWriter w = writer;
            if (w == null) throw new IOException("Chưa kết nối tới Server.");
            String logCommand = command.toUpperCase(Locale.ROOT).startsWith("PASS ") ? "PASS ****" : command;
            System.out.println("[CLIENT] >> " + logCommand);
            w.write(command + "\r\n");
            w.flush();
        } finally {
            lock.unlock();
        }
    }

    public FtpResponse readResponse() throws IOException {
        lock.lock();
        try {
            ensureConnected();
            BufferedReader r = reader;
            if (r == null) throw new IOException("Chưa kết nối tới Server.");
            FtpResponse resp = FtpResponse.read(r);
            System.out.println("[CLIENT] << " + resp);
            return resp;
        } finally {
            lock.unlock();
        }
    }

    public FtpResponse sendAndReceive(String command) throws IOException {
        sendCommand(command);
        return readResponse();
    }

    // -----------------------------------------------------------------
    // Liệt kê file
    // -----------------------------------------------------------------

    public List<RemoteFile> listFiles() throws IOException {
        lock.lock();
        try {
            ensureConnected();
            sendAndReceive("TYPE I");
            Socket dataSocket = openPassiveDataConnection();

            FtpResponse resp = sendAndReceive("LIST");
            if (resp.getCode() != 150 && resp.getCode() != 125) {
                safeClose(dataSocket);
                throw new IOException("LIST thất bại: " + resp);
            }

            List<String> rawLines = new ArrayList<>();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(
                    dataSocket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    rawLines.add(line);
                }
            } finally {
                safeClose(dataSocket);
            }

            FtpResponse done = readResponse();
            if (done.getCode() != 226 && done.getCode() != 250) {
                throw new IOException("LIST chưa hoàn tất: " + done);
            }

            return parseList(rawLines);
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------------
    // Upload
    // -----------------------------------------------------------------

    public void uploadFile(java.io.File localFile) throws IOException {
        uploadFile(localFile, localFile.getName(), p -> {
        });
    }

    public void uploadFile(java.io.File localFile, DoubleConsumer progress) throws IOException {
        uploadFile(localFile, localFile.getName(), progress);
    }

    public void uploadFile(java.io.File localFile, String remoteName, DoubleConsumer progress) throws IOException {
        if (localFile == null || !localFile.isFile()) {
            throw new IOException("File local không tồn tại: " + localFile);
        }
        if (remoteName == null || remoteName.isBlank()) {
            throw new IOException("Tên file remote không hợp lệ.");
        }

        lock.lock();
        try {
            ensureConnected();
            sendAndReceive("TYPE I");
            Socket dataSocket = openPassiveDataConnection();

            FtpResponse resp = sendAndReceive("STOR " + remoteName);
            if (resp.getCode() != 150 && resp.getCode() != 125) {
                safeClose(dataSocket);
                throw new IOException("STOR thất bại: " + resp);
            }

            long total = Math.max(1L, localFile.length());
            long sent = 0;
            try (InputStream in = Files.newInputStream(localFile.toPath());
                 OutputStream out = dataSocket.getOutputStream()) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    sent += n;
                    if (progress != null) {
                        progress.accept(Math.min(1.0, (double) sent / total));
                    }
                }
                out.flush();
            } finally {
                safeClose(dataSocket);
            }
            if (progress != null) {
                progress.accept(1.0);
            }

            FtpResponse done = readResponse();
            if (done.getCode() != 226 && done.getCode() != 250) {
                throw new IOException("Upload chưa hoàn tất: " + done);
            }
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------------
    // Download
    // -----------------------------------------------------------------

    public void downloadFile(String remoteName) throws IOException {
        downloadFile(remoteName, new java.io.File(remoteName), p -> {
        });
    }

    public void downloadFile(String remoteName, java.io.File localFile) throws IOException {
        downloadFile(remoteName, localFile, p -> {
        });
    }

    public void downloadFile(String remoteName, java.io.File localFile, DoubleConsumer progress) throws IOException {
        if (remoteName == null || remoteName.isBlank()) {
            throw new IOException("Tên file remote không hợp lệ.");
        }
        if (localFile == null) {
            throw new IOException("File local không hợp lệ.");
        }

        lock.lock();
        try {
            ensureConnected();
            sendAndReceive("TYPE I");

            long total = fileSize(remoteName);
            Socket dataSocket = openPassiveDataConnection();

            FtpResponse resp = sendAndReceive("RETR " + remoteName);
            if (resp.getCode() != 150 && resp.getCode() != 125) {
                safeClose(dataSocket);
                throw new IOException("RETR thất bại: " + resp);
            }

            Path target = localFile.toPath().toAbsolutePath();
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = (parent == null)
                    ? Files.createTempFile(".ftp-", ".part")
                    : Files.createTempFile(parent, ".ftp-", ".part");

            long received = 0;
            try (InputStream in = dataSocket.getInputStream();
                 OutputStream out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    received += n;
                    if (progress != null) {
                        if (total > 0) {
                            progress.accept(Math.min(1.0, (double) received / total));
                        } else {
                            progress.accept(-1); // Không biết tổng kích thước -> indeterminate.
                        }
                    }
                }
                out.flush();
            } finally {
                safeClose(dataSocket);
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            if (progress != null) {
                progress.accept(1.0);
            }

            FtpResponse done = readResponse();
            if (done.getCode() != 226 && done.getCode() != 250) {
                throw new IOException("Download chưa hoàn tất: " + done);
            }
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------------
    // Xóa file (DELE)
    // -----------------------------------------------------------------

    /**
     * Lấy thư mục hiện tại trên server (lệnh PWD).
     */
    public String pwd() throws IOException {
        lock.lock();
        try {
            ensureConnected();
            FtpResponse resp = sendAndReceive("PWD");
            if (resp.getCode() != 257) {
                throw new IOException("PWD thất bại: " + resp);
            }
            String message = resp.getMessage();
            int first = message.indexOf('"');
            int last = message.lastIndexOf('"');
            if (first >= 0 && last > first) {
                return message.substring(first + 1, last);
            }
            return message.trim();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Chuyển vào thư mục con trên server (lệnh CWD).
     */
    public void changeDirectory(String dirName) throws IOException {
        if (dirName == null || dirName.isBlank()) {
            throw new IOException("Tên thư mục không hợp lệ.");
        }
        lock.lock();
        try {
            ensureConnected();
            FtpResponse resp = sendAndReceive("CWD " + dirName);
            if (resp.getCode() != 250) {
                throw new IOException("CWD thất bại: " + resp);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Quay lên thư mục cha trên server (lệnh CDUP).
     */
    public void changeToParent() throws IOException {
        lock.lock();
        try {
            ensureConnected();
            FtpResponse resp = sendAndReceive("CDUP");
            if (resp.getCode() != 250) {
                throw new IOException("CDUP thất bại: " + resp);
            }
        } finally {
            lock.unlock();
        }
    }

    public void deleteFile(String remoteName) throws IOException {
        if (remoteName == null || remoteName.isBlank()) {
            throw new IOException("Tên file không hợp lệ.");
        }
        lock.lock();
        try {
            ensureConnected();
            FtpResponse resp = sendAndReceive("DELE " + remoteName);
            if (resp.getCode() != 250) {
                throw new IOException("DELE thất bại: " + resp);
            }
        } finally {
            lock.unlock();
        }
    }

    // -----------------------------------------------------------------
    // Các hàm hỗ trợ
    // -----------------------------------------------------------------

    private long fileSize(String remoteName) {
        try {
            FtpResponse r = sendAndReceive("SIZE " + remoteName);
            if (r.getCode() == 213) {
                return Long.parseLong(r.getMessage().trim());
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private Socket openPassiveDataConnection() throws IOException {
        FtpResponse resp = sendAndReceive("PASV");
        if (resp.getCode() != 227) {
            throw new IOException("PASV thất bại: " + resp);
        }

        Matcher matcher = PASV_PATTERN.matcher(resp.getMessage());
        if (!matcher.find()) {
            throw new IOException("Không parse được phản hồi PASV: " + resp);
        }

        String ip = matcher.group(1) + "." + matcher.group(2) + "."
                + matcher.group(3) + "." + matcher.group(4);
        int port = Integer.parseInt(matcher.group(5)) * 256
                + Integer.parseInt(matcher.group(6));

        // Nếu server trả về địa chỉ không dùng được, dùng lại host control.
        if ("0.0.0.0".equals(ip)) {
            ip = host;
        }

        Socket dataSocket = new Socket();
        try {
            dataSocket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
        } catch (IOException ex) {
            // Thử lại với host control nếu server trả IP khác.
            if (!ip.equals(host)) {
                dataSocket = new Socket();
                dataSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            } else {
                throw ex;
            }
        }
        dataSocket.setSoTimeout(DATA_TIMEOUT_MS);
        return dataSocket;
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Chưa kết nối tới Server.");
        }
    }

    private List<RemoteFile> parseList(List<String> rawLines) {
        List<RemoteFile> result = new ArrayList<>();
        for (String line : rawLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("total")) {
                continue;
            }

            Matcher m = UNIX_LIST.matcher(trimmed);
            if (m.matches()) {
                String perms = m.group(1) + m.group(2);
                long size = 0L;
                try {
                    size = Long.parseLong(m.group(3));
                } catch (NumberFormatException ignored) {
                }
                String modified = m.group(4);
                String name = m.group(5).trim();
                boolean dir = perms.startsWith("d");
                result.add(new RemoteFile(name, size, dir, modified, perms));
            } else {
                // Fallback: coi toàn bộ dòng còn lại là tên file.
                result.add(new RemoteFile(trimmed, 0L, false, "", ""));
            }
        }
        return result;
    }

    private void safeClose(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
