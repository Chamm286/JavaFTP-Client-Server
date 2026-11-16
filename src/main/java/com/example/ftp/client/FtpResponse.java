package com.example.ftp.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phản hồi của FTP Server.
 *
 * <p>Mã phản hồi FTP là 3 chữ số (ví dụ 220, 230, 150, 226). Phản hồi có thể
 * là một dòng hoặc nhiều dòng (multiline, bắt đầu bằng "211-" và kết thúc
 * bằng "211 ").</p>
 */
public final class FtpResponse {

    private final int code;
    private final String message;
    private final List<String> lines;

    public FtpResponse(int code, String message, List<String> lines) {
        this.code = code;
        this.message = message == null ? "" : message;
        this.lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getLines() {
        return lines;
    }

    public boolean isPositiveCompletion() {
        return code >= 200 && code < 300;
    }

    public boolean isIntermediate() {
        return code >= 100 && code < 200;
    }

    @Override
    public String toString() {
        return code + " " + message;
    }

    /**
     * Đọc một phản hồi FTP đầy đủ từ BufferedReader.
     */
    public static FtpResponse read(java.io.BufferedReader reader) throws java.io.IOException {
        String first = reader.readLine();
        if (first == null) {
            throw new java.io.EOFException("Server đã đóng kết nối.");
        }

        Pattern pattern = Pattern.compile("^(\\d{3})([ -])(.*)$");
        Matcher matcher = pattern.matcher(first);
        if (!matcher.matches()) {
            throw new java.io.IOException("Phản hồi FTP không hợp lệ: " + first);
        }

        int code = Integer.parseInt(matcher.group(1));
        String separator = matcher.group(2);
        List<String> lines = new ArrayList<>();
        lines.add(first);

        String message = matcher.group(3);
        if ("-".equals(separator)) {
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    throw new java.io.EOFException("Phản hồi FTP bị cắt cụt.");
                }
                lines.add(line);
                if (line.startsWith(code + " ")) {
                    message = line.substring(4);
                    break;
                }
            }
        }

        return new FtpResponse(code, message, lines);
    }
}
