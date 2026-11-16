# HƯỚNG DẪN KẾT NỐI 2 MÁY (LAN / Wi-Fi)

> Chỉ máy chạy **Server** cần MySQL (XAMPP). Máy Client **không cần** MySQL —
> fullname và role được Server gửi về trong lời chào đăng nhập.

---

## Bước chuẩn bị chung (làm 1 lần trên cả 2 máy)

1. Cả 2 máy kết nối **cùng một mạng Wi-Fi / hotspot**.
2. Trên **máy Server**, mở Command Prompt và chạy:

   ```bat
   ipconfig
   ```

   Ghi lại dòng **IPv4 Address**, ví dụ: `192.168.1.25`.

3. Mở **Windows Defender Firewall → Allow an app** → tick **Java(TM) Platform SE binary**
   (cả Private và Public). Hoặc tạm thời tắt firewall cho mạng Private khi demo.
4. Port cần mở:
   - `2121` (control – bắt buộc)
   - Các port cao ngẫu nhiên (passive data) → cách đơn giản nhất là cho phép ứng dụng Java.

---

## TRƯỜNG HỢP 1 — Máy bạn là SERVER, máy Tri là CLIENT

### Máy bạn (Server)
```bat
:: 1. Bật MySQL trong XAMPP (port 3307)
:: 2. Chạy Server Manager
mvn javafx:run -Djavafx.mainClass=com.example.ftp.server.ServerManagerApp
```
Bấm **Start** để server lắng nghe port `2121`.

Chạy thêm 1 Client trên máy bạn để demo:
```bat
mvn javafx:run -Djavafx.mainClass=com.example.ftp.client.FTPClientApp
```
- Máy chủ: `127.0.0.1:2121`
- Đăng nhập: `admin / 123456`

### Máy Tri (Client)
```bat
mvn javafx:run -Djavafx.mainClass=com.example.ftp.client.FTPClientApp
```
- Máy chủ: `<IPv4 của máy bạn>:2121` — ví dụ `192.168.1.25:2121`
- Đăng nhập: `tri / 123456` (hoặc `admin / 123456`)

> Lúc này Server phục vụ **2 client cùng lúc** (đa luồng): máy bạn và máy Tri.

---

## TRƯỜNG HỢP 2 — Máy Tri là SERVER, máy bạn là CLIENT (hoán đổi)

### Máy Tri (Server)
```bat
:: Bật XAMPP MySQL trên máy Tri
mvn javafx:run -Djavafx.mainClass=com.example.ftp.server.ServerManagerApp
```
Bấm **Start**, lấy IPv4 của máy Tri bằng `ipconfig`.

### Máy bạn (Client)
```bat
mvn javafx:run -Djavafx.mainClass=com.example.ftp.client.FTPClientApp
```
- Máy chủ: `<IPv4 của máy Tri>:2121` — ví dụ `192.168.1.40:2121`
- Đăng nhập: `admin / 123456` hoặc `tri / 123456`

> Nếu máy Tri cũng mở Client thì dùng `127.0.0.1:2121`.

---

## MẸO TRẢ LỜI THẦY

- Server dùng **Java Socket TCP**, mỗi client được xử lý bởi **một thread riêng**
  (`ClientHandler`), nên nhiều máy kết nối đồng thời.
- Client **không truy cập MySQL trực tiếp**; mọi xác thực diễn ra ở Server.
- Dữ liệu upload nằm trong thư mục `server_files/` trên máy Server.
- Log đăng nhập / upload / download / delete được ghi vào bảng `logs` trong
  `lab2_ftp_db` trên máy Server.
- Nếu kết nối bị treo: kiểm tra firewall, sai IPv4, hoặc server chưa bấm **Start**.

---

## KIỂM THỬ TỰ ĐỘNG (nếu thầy yêu cầu chứng minh)

```bat
:: E2E toàn hệ thống (tự bật server ngầm, test login sai/đúng, upload, download, delete, log)
mvn test-compile exec:java -Dexec.mainClass=com.example.ftp.test.AutoTest -Dexec.classpathScope=test

:: Test đúng lớp FTPClientSocket mà UI dùng (admin + tri)
mvn test-compile exec:java -Dexec.mainClass=com.example.ftp.test.ClientSocketTest -Dexec.classpathScope=test
```