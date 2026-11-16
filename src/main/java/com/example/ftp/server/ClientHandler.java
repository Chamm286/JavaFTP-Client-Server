package com.example.ftp.server;

import com.example.ftp.db.DatabaseManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Xử lý một phiên FTP cho một Client.
 *
 * <p>Tuân thủ các lệnh FTP chuẩn: USER, PASS, PASV, LIST, RETR, STOR, QUIT,
 * cùng các lệnh bổ trợ TYPE, SYST, PWD, FEAT, SIZE, MDTM, DELE, NOOP.</p>
 */
public class ClientHandler implements Runnable {

    private static final int DATA_TIMEOUT_MS = 60_000;
    private static final DateTimeFormatter LIST_DATE =
            DateTimeFormatter.ofPattern("MMM dd HH:mm", Locale.ENGLISH);

    private final Socket controlSocket;
    private final Path rootDir;
    private final DatabaseManager database;

    private BufferedReader reader;
    private BufferedWriter writer;

    private volatile String username;
    private volatile String permission = "read";
    private volatile boolean loggedIn = false;
    private Path currentDir;
    private int connectionId = -1;

    // ServerSocket dữ liệu cho passive mode. Chỉ có một passive socket tại một thời điểm.
    private volatile ServerSocket passiveServer;

    public ClientHandler(Socket controlSocket, Path rootDir, DatabaseManager database) {
        this.controlSocket = controlSocket;
        this.rootDir = rootDir.toAbsolutePath().normalize();
        this.currentDir = this.rootDir;
        this.database = database;
    }

    @Override
    public void run() {
        String clientAddress = controlSocket.getRemoteSocketAddress().toString();
        connectionId = ServerStats.register(clientIp());
        try {
            controlSocket.setSoTimeout(0); // Control connection có thể idle lâu.
            reader = new BufferedReader(new InputStreamReader(
                    controlSocket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(
                    controlSocket.getOutputStream(), StandardCharsets.UTF_8));

            reply(220, "Welcome to FTP FileZilla Simulator. Please login.");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                int space = line.indexOf(' ');
                String command = (space < 0 ? line : line.substring(0, space)).toUpperCase(Locale.ROOT);
                String argument = (space < 0 ? "" : line.substring(space + 1).trim());

                if (!loggedIn && !Set.of("USER", "PASS", "QUIT", "FEAT", "SYST", "NOOP", "OPTS", "TYPE").contains(command)) {
                    reply(530, "Please login with USER and PASS.");
                    continue;
                }

                boolean keepRunning = handleCommand(command, argument);
                if (!keepRunning) {
                    break;
                }
            }
        } catch (IOException ex) {
            System.out.println("[CLIENT " + clientAddress + "] Kết nối bị đóng: " + ex.getMessage());
        } finally {
            if (loggedIn && username != null) {
                database.writeLog(username, "LOGOUT", null, clientIp(), "SUCCESS");
                loggedIn = false;
            }
            ServerStats.unregister(connectionId);
            closePassive();
            try {
                controlSocket.close();
            } catch (IOException ignored) {
            }
            System.out.println("[CLIENT " + clientAddress + "] Đã ngắt kết nối.");
        }
    }

    private boolean handleCommand(String command, String argument) {
        try {
            switch (command) {
                case "USER":
                    handleUser(argument);
                    break;
                case "PASS":
                    handlePass(argument);
                    break;
                case "QUIT":
                    reply(221, "Goodbye.");
                    if (loggedIn && username != null) {
                        database.writeLog(username, "LOGOUT", null, clientIp(), "SUCCESS");
                        loggedIn = false;
                    }
                    return false;
                case "PASV":
                    handlePasv();
                    break;
                case "EPSV":
                    handleEpsv();
                    break;
                case "LIST":
                    handleList(argument);
                    break;
                case "RETR":
                    handleRetr(argument);
                    break;
                case "STOR":
                    handleStor(argument);
                    break;
                case "TYPE":
                    reply(200, "Type set to " + (argument.isBlank() ? "I" : argument) + ".");
                    break;
                case "MODE":
                case "STRU":
                    reply(200, "OK.");
                    break;
                case "SYST":
                    reply(215, "UNIX Type: L8");
                    break;
                case "PWD":
                    reply(257, "\"" + displayPath(currentDir) + "\" is the current directory.");
                    break;
                case "CWD":
                    handleCwd(argument);
                    break;
                case "CDUP":
                    handleCdup();
                    break;
                case "SIZE":
                    handleSize(argument);
                    break;
                case "MDTM":
                    handleMdtm(argument);
                    break;
                case "DELE":
                    handleDele(argument);
                    break;
                case "FEAT":
                    sendFeatures();
                    break;
                case "OPTS":
                    reply(200, "OPTS UTF8 ON");
                    break;
                case "NOOP":
                    reply(200, "OK.");
                    break;
                default:
                    reply(502, "Command not implemented: " + command);
            }
        } catch (SecurityException ex) {
            reply(550, "Truy cập bị từ chối: " + ex.getMessage());
        } catch (Exception ex) {
            reply(550, "Lỗi: " + ex.getMessage());
            System.err.println("[SERVER] Lỗi xử lý lệnh " + command + ": " + ex);
        }
        return true;
    }

    private void handleUser(String argument) {
        if (argument.isBlank()) {
            reply(501, "Syntax: USER <username>");
            return;
        }
        this.username = argument;
        reply(331, "Password required for " + argument + ".");
    }

    private void handlePass(String argument) {
        if (username == null) {
            reply(503, "Send USER first.");
            return;
        }
        if (loggedIn) {
            reply(230, "Already logged in.");
            return;
        }
        try {
            boolean ok = database.checkLogin(username, argument);
            if (ok) {
                loggedIn = true;
                permission = database.getPermission(username);
                String fullname = database.getFullname(username);
                ServerStats.setUsername(connectionId, username);
                reply(230, "Login successful. Welcome, " + fullname + " (" + permission + ").");
                database.writeLog(username, "LOGIN", null, clientIp(), "SUCCESS");
            } else {
                reply(530, "Login incorrect.");
                database.writeLog(username, "LOGIN", null, clientIp(), "FAIL");
                username = null;
            }
        } catch (Exception ex) {
            reply(530, "Authentication service unavailable.");
            System.err.println("[SERVER] Lỗi xác thực: " + ex.getMessage());
        }
    }

    private void handlePasv() {
        closePassive();
        try {
            InetAddress bind = InetAddress.getByName("0.0.0.0");
            passiveServer = new ServerSocket(0, 5, bind);
            passiveServer.setSoTimeout(DATA_TIMEOUT_MS);

            String ip = getAdvertisedIp();
            int port = passiveServer.getLocalPort();
            int p1 = port / 256;
            int p2 = port % 256;
            String[] parts = ip.split("\\.");
            reply(227, "Entering Passive Mode ("
                    + String.join(",", parts) + "," + p1 + "," + p2 + ").");
        } catch (IOException ex) {
            reply(425, "Cannot open passive connection: " + ex.getMessage());
        }
    }

    private void handleEpsv() {
        closePassive();
        try {
            passiveServer = new ServerSocket(0, 5, InetAddress.getByName("0.0.0.0"));
            passiveServer.setSoTimeout(DATA_TIMEOUT_MS);
            reply(229, "Entering Extended Passive Mode (|||" + passiveServer.getLocalPort() + "|).");
        } catch (IOException ex) {
            reply(425, "Cannot open passive connection: " + ex.getMessage());
        }
    }

    private void handleList(String argument) {
        Path target;
        try {
            target = resolvePath(argument);
        } catch (Exception ex) {
            reply(550, ex.getMessage());
            return;
        }
        if (!Files.exists(target)) {
            reply(550, "No such file or directory.");
            return;
        }
        if (!Files.isDirectory(target)) {
            // LIST trên một file đơn lẻ: trả về chính file đó.
            target = target.getParent();
        }

        ServerSocket dataServer = passiveServer;
        if (dataServer == null) {
            reply(425, "Use PASV first.");
            return;
        }
        // Chuyển quyền sở hữu ServerSocket passive sang thao tác này.
        passiveServer = null;

        reply(150, "Opening ASCII mode data connection for file list.");

        try (ServerSocket ss = dataServer;
             Socket dataSocket = ss.accept()) {

            dataSocket.setSoTimeout(DATA_TIMEOUT_MS);
            try (BufferedWriter dataOut = new BufferedWriter(new OutputStreamWriter(
                    dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {

                // Liệt kê thư mục con trước, sau đó file, theo thứ tự tên.
                TreeMap<String, Path> dirs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
                TreeMap<String, Path> files = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

                try (var stream = Files.list(target)) {
                    stream.forEach(p -> {
                        String name = p.getFileName().toString();
                        if (".".equals(name) || "..".equals(name)) return;
                        if (Files.isDirectory(p)) dirs.put(name, p);
                        else files.put(name, p);
                    });
                }

                for (Path p : dirs.values()) dataOut.write(formatListLine(p));
                for (Path p : files.values()) dataOut.write(formatListLine(p));
                dataOut.flush();
            }
            reply(226, "Transfer complete.");
        } catch (IOException ex) {
            reply(426, "Data connection error: " + ex.getMessage());
        }
    }

    private void handleRetr(String argument) {
        if (argument.isBlank()) {
            reply(501, "Syntax: RETR <filename>");
            return;
        }
        Path target;
        try {
            target = resolvePath(argument);
        } catch (Exception ex) {
            reply(550, ex.getMessage());
            return;
        }
        if (!Files.isRegularFile(target)) {
            reply(550, "File not found or not a regular file.");
            logTransfer("DOWNLOAD", target.getFileName().toString(), "FAIL");
            return;
        }

        ServerSocket dataServer = passiveServer;
        if (dataServer == null) {
            reply(425, "Use PASV first.");
            logTransfer("DOWNLOAD", target.getFileName().toString(), "FAIL");
            return;
        }
        passiveServer = null;

        reply(150, "Opening BINARY mode data connection for " + target.getFileName() + ".");

        try (ServerSocket ss = dataServer;
             Socket dataSocket = ss.accept()) {

            dataSocket.setSoTimeout(DATA_TIMEOUT_MS);
            long size = Files.size(target);
            try (var in = Files.newInputStream(target);
                 var out = dataSocket.getOutputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                out.flush();
            }
            reply(226, "Transfer complete (" + size + " bytes).");
            ServerStats.addBytes(size);
            logTransfer("DOWNLOAD", target.getFileName().toString(), "SUCCESS");
        } catch (IOException ex) {
            reply(426, "Data connection error: " + ex.getMessage());
            logTransfer("DOWNLOAD", target.getFileName().toString(), "FAIL");
        }
    }

    private void handleStor(String argument) {
        if (argument.isBlank()) {
            reply(501, "Syntax: STOR <filename>");
            return;
        }
        if (!canWrite()) {
            reply(550, "Permission denied: account is read-only.");
            logTransfer("UPLOAD", argument, "FAIL");
            return;
        }
        Path target;
        try {
            target = resolvePath(argument);
        } catch (Exception ex) {
            reply(550, ex.getMessage());
            return;
        }
        if (Files.isDirectory(target)) {
            reply(550, "Target is a directory.");
            logTransfer("UPLOAD", target.getFileName().toString(), "FAIL");
            return;
        }
        Path parent = target.getParent();
        if (parent == null || !Files.exists(parent)) {
            reply(550, "Directory does not exist.");
            logTransfer("UPLOAD", target.getFileName().toString(), "FAIL");
            return;
        }

        ServerSocket dataServer = passiveServer;
        if (dataServer == null) {
            reply(425, "Use PASV first.");
            logTransfer("UPLOAD", target.getFileName().toString(), "FAIL");
            return;
        }
        passiveServer = null;

        reply(150, "Opening BINARY mode data connection for " + target.getFileName() + ".");

        Path tempFile = null;
        boolean success = false;
        try (ServerSocket ss = dataServer;
             Socket dataSocket = ss.accept()) {

            dataSocket.setSoTimeout(DATA_TIMEOUT_MS);
            tempFile = Files.createTempFile(parent, ".upload-", ".part");
            try (var in = dataSocket.getInputStream();
                 var out = Files.newOutputStream(tempFile)) {
                byte[] buffer = new byte[64 * 1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
                out.flush();
            }

            Files.move(tempFile, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            tempFile = null;
            success = true;

            long size = Files.size(target);
            reply(226, "Transfer complete (" + size + " bytes).");
            ServerStats.addBytes(size);
            logTransfer("UPLOAD", target.getFileName().toString(), "SUCCESS");
        } catch (IOException ex) {
            reply(426, "Data connection error: " + ex.getMessage());
            logTransfer("UPLOAD", target.getFileName().toString(), "FAIL");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
            if (!success) {
                // Giữ file cũ (nếu có) nguyên vẹn; file mới chưa được move.
            }
        }
    }

    private void handleCwd(String argument) {
        if (argument.isBlank()) {
            reply(250, "Current directory unchanged.");
            return;
        }
        Path target;
        try {
            target = resolvePath(argument);
        } catch (Exception ex) {
            reply(550, ex.getMessage());
            return;
        }
        if (!Files.isDirectory(target)) {
            reply(550, "Not a directory.");
            return;
        }
        currentDir = target;
        reply(250, "Directory changed to " + displayPath(currentDir) + ".");
    }

    private void handleCdup() {
        if (!currentDir.equals(rootDir)) {
            currentDir = currentDir.getParent();
            if (currentDir == null) currentDir = rootDir;
        }
        reply(250, "Directory changed to " + displayPath(currentDir) + ".");
    }

    private void handleSize(String argument) {
        if (argument.isBlank()) {
            reply(501, "Syntax: SIZE <filename>");
            return;
        }
        Path target;
        try {
            target = resolvePath(argument);
        } catch (Exception ex) {
            reply(550, ex.getMessage());
            return;
        }
        if (!Files.isRegularFile(target)) {
            reply(550, "File not found.");
            return;
        }
        try {
            reply(213, String.valueOf(Files.size(target)));
        } catch (IOException ex) {
            reply(550, ex.getMessage());
        }
    }

    private void handleMdtm(String argument) {
        if (argument.isBlank()) {
            reply(501, "Syntax: MDTM <filename>");
            return;
        }
        Path target;
        try {
            target = resolvePath(argument);
        } catch (Exception ex) {
            reply(550, ex.getMessage());
            return;
        }
        if (!Files.isRegularFile(target)) {
            reply(550, "File not found.");
            return;
        }
        try {
            String stamp = java.time.Instant.ofEpochMilli(Files.getLastModifiedTime(target).toMillis())
                    .atZone(ZoneId.of("UTC"))
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            reply(213, stamp);
        } catch (IOException ex) {
            reply(550, ex.getMessage());
        }
    }

    private void handleDele(String argument) {
        if (argument.isBlank()) {
            reply(501, "Syntax: DELE <filename>");
            return;
        }
        if (!canWrite()) {
            reply(550, "Permission denied: account is read-only.");
            logTransfer("DELETE", argument, "FAIL");
            return;
        }
        Path target;
        try {
            target = resolvePath(argument);
        } catch (Exception ex) {
            reply(550, ex.getMessage());
            return;
        }
        if (!Files.isRegularFile(target)) {
            reply(550, "File not found.");
            logTransfer("DELETE", target.getFileName().toString(), "FAIL");
            return;
        }
        try {
            Files.delete(target);
            reply(250, "File deleted.");
            logTransfer("DELETE", target.getFileName().toString(), "SUCCESS");
        } catch (IOException ex) {
            reply(550, ex.getMessage());
            logTransfer("DELETE", target.getFileName().toString(), "FAIL");
        }
    }

    private void sendFeatures() {
        writerPrint("211-Features:");
        writerPrint(" UTF8");
        writerPrint(" SIZE");
        writerPrint(" MDTM");
        writerPrint(" PASV");
        writerPrint(" EPSV");
        writerPrint("211 End");
        flushWriter();
    }

    private Path resolvePath(String name) {
        if (name == null || name.isBlank()) {
            return currentDir;
        }
        // Hỗ trợ LIST -a ... (tùy chọn bắt đầu bằng '-')
        String cleaned = name;
        while (cleaned.startsWith("-")) {
            int sp = cleaned.indexOf(' ');
            cleaned = (sp < 0) ? "" : cleaned.substring(sp + 1).trim();
        }
        if (cleaned.isBlank()) {
            return currentDir;
        }

        String normalized = cleaned.replace('\\', '/');
        Path candidate;
        if (normalized.startsWith("/")) {
            candidate = rootDir.resolve(normalized.substring(1)).normalize();
        } else {
            candidate = currentDir.resolve(normalized).normalize();
        }

        // Chống path traversal: phải nằm trong rootDir.
        if (!candidate.startsWith(rootDir)) {
            throw new SecurityException("Đường dẫn vượt ra ngoài thư mục server.");
        }
        return candidate;
    }

    private String displayPath(Path path) {
        if (path.equals(rootDir)) {
            return "/";
        }
        String rel = rootDir.relativize(path).toString().replace('\\', '/');
        return "/" + rel;
    }

    private String formatListLine(Path path) {
        String name = path.getFileName().toString();
        boolean dir = Files.isDirectory(path);
        String perms = dir ? "drwxr-xr-x" : "-rw-r--r--";
        long size = 0;
        try {
            if (!dir) size = Files.size(path);
        } catch (IOException ignored) {
        }
        String date;
        try {
            date = Files.getLastModifiedTime(path).toInstant()
                    .atZone(ZoneId.systemDefault())
                    .format(LIST_DATE);
        } catch (IOException ex) {
            date = "Jan 01 00:00";
        }
        return String.format(Locale.US, "%s %3d %-8s %-8s %12d %s %s\r\n",
                perms, 1, "ftp", "ftp", size, date, name);
    }

    private boolean canWrite() {
        // admin, write và user đều được phép ghi (demo đồ án).
        return "write".equalsIgnoreCase(permission)
                || "admin".equalsIgnoreCase(permission)
                || "user".equalsIgnoreCase(permission);
    }

    private void logTransfer(String action, String filename, String status) {
        if (loggedIn && username != null) {
            database.writeLog(username, action, filename, clientIp(), status);
        }
    }

    private String clientIp() {
        try {
            return controlSocket.getInetAddress().getHostAddress();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private String getAdvertisedIp() {
        InetAddress local = controlSocket.getLocalAddress();
        if (local instanceof Inet4Address) {
            return local.getHostAddress();
        }
        return "127.0.0.1";
    }

    private void closePassive() {
        ServerSocket ss = passiveServer;
        passiveServer = null;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void reply(int code, String message) {
        try {
            writer.write(code + " " + message + "\r\n");
            writer.flush();
            System.out.println("[SERVER -> CLIENT] " + code + " " + message);
        } catch (IOException ignored) {
        }
    }

    private void writerPrint(String text) {
        try {
            writer.write(text + "\r\n");
        } catch (IOException ignored) {
        }
    }

    private void flushWriter() {
        try {
            writer.flush();
        } catch (IOException ignored) {
        }
    }
}
