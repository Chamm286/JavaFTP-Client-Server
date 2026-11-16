# JavaFTP — Client & Server (Mô phỏng FileZilla)

Đồ án môn **Lập trình mạng**: ứng dụng FTP Client/Server viết bằng **Java 21**, giao tiếp qua **TCP Socket**, xử lý **đa luồng**, giao diện **JavaFX** và lưu trữ **MySQL**.

> Giao diện Client và Server dùng chung chủ đề **Dark Enterprise** (nền tối, chữ sáng, điểm nhấn xanh dương) — không còn tình trạng chữ đen trên nền tối.

---

## Tính năng

- Đăng nhập qua Socket TCP, xác thực với MySQL ở phía Server.
- Server gửi **fullname + role** trong lời chào 230 → Client hiển thị đúng tên/role mà **không cần cài MySQL**.
- Upload / Download có **ProgressBar %**, chạy nền không treo giao diện.
- Duyệt cây thư mục local, double-click để vào thư mục; nút **Up** lên thư mục cha.
- Xem danh sách file remote, **Refresh**, **Delete** có hộp thoại xác nhận.
- Đăng xuất an toàn (gửi `QUIT`), quay lại màn hình Login.
- Server Manager theo dõi client đang kết nối và log thao tác.
- Hỗ trợ **nhiều client đồng thời**, kết nối qua LAN (2 máy trở lên).
- Icon Ikonli (Font Awesome 5) trên toàn bộ nút thao tác.

---

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| Giao diện | JavaFX 21 (FXML + CSS) |
| Mạng | Java TCP Socket, đa luồng |
| Cơ sở dữ liệu | MySQL 8 (XAMPP, port `3307`) |
| Icon | Ikonli FontAwesome 5 |
| Build | Maven |

---

## Tài khoản mẫu

| Username | Password | Fullname | Role |
|---|---|---|---|
| `admin` | `123456` | Nguyễn Thị Bích Trâm | `admin` |
| `tri` | `123456` | Trần Văn Tri | `user` |

> Mật khẩu lưu **plain text** theo yêu cầu đồ án (không băm MD5/SHA).

---

## Yêu cầu

- JDK 21
- Maven 3.8+
- XAMPP với MySQL đang chạy ở port **3307** (chỉ cần trên máy Server)

---

## Cài đặt & chạy

### 1. Database

Bật MySQL trong XAMPP. Server sẽ tự tạo database `lab2_ftp_db`, bảng `users`/`logs` và seed tài khoản khi chạy lần đầu.

Có thể import thủ công:

```bash
mysql -u root -p --port=3307 < sql/ftp_db.sql
```

Nếu MySQL của bạn dùng cổng/mật khẩu khác, tạo file `src/main/resources/db.properties` (xem mẫu `db.properties.example`) hoặc truyền biến môi trường `FTP_DB_URL`, `FTP_DB_USER`, `FTP_DB_PASSWORD`.

### 2. Chạy Server Manager (giao diện)

```bash
mvn javafx:run -Djavafx.mainClass=com.example.ftp.server.ServerManagerApp
```

Bấm **Start** để lắng nghe port `2121`.

Hoặc chạy Server dạng console:

```bash
mvn exec:java
```

### 3. Chạy Client

```bash
mvn javafx:run -Djavafx.mainClass=com.example.ftp.client.FTPClientApp
```

- Máy chủ: `127.0.0.1:2121` nếu chạy cùng máy.
- Máy khác trong LAN: `<IPv4 máy Server>:2121`.

### 4. Build

```bash
mvn clean package
```

---

## Kết nối 2 máy (LAN / Wi-Fi)

Chi tiết đầy đủ trong [`HUONG_DAN_KET_NOI_2_MAY.md`](HUONG_DAN_KET_NOI_2_MAY.md). Tóm tắt:

- **Trường hợp 1:** Máy bạn = Server + Client, máy Tri = Client → Tri nhập IPv4 máy bạn.
- **Trường hợp 2:** Máy Tri = Server, máy bạn = Client → bạn nhập IPv4 máy Tri.

Lấy IP bằng `ipconfig` (dòng IPv4). Trên máy Server, cho phép **Java(TM) Platform SE binary** trong Windows Firewall (Private + Public) để mở port `2121` và các port passive.

---

## Kiểm thử

```bash
# E2E: login sai/đúng, upload, download, delete, logout, log MySQL
mvn test-compile exec:java -Dexec.mainClass=com.example.ftp.test.AutoTest -Dexec.classpathScope=test

# Đúng lớp FTPClientSocket mà UI dùng (admin + tri)
mvn test-compile exec:java -Dexec.mainClass=com.example.ftp.test.ClientSocketTest -Dexec.classpathScope=test

# Nạp FXML + CSS (phát hiện lỗi giao diện runtime)
mvn test-compile exec:java -Dexec.mainClass=com.example.ftp.test.FxmlSmokeTest -Dexec.classpathScope=test
```

---

## Cấu trúc dự án

```
Lab_2_FTP/
├── src/main/java/com/example/ftp/
│   ├── client/        # FTPClientApp, LoginController, MainController, FTPClientSocket
│   ├── server/        # ServerManagerApp, FTPServer, ClientHandler, FTPServerMain
│   └── db/            # DatabaseManager (MySQL)
├── src/main/resources/
│   ├── fxml/          # LoginView.fxml, MainView.fxml
│   ├── css/           # style.css (Client dark theme), server-dark.css
│   └── db.properties.example
├── src/test/java/com/example/ftp/test/
│   ├── AutoTest.java
│   ├── ClientSocketTest.java
│   ├── FxmlSmokeTest.java
│   └── UiSnapshotTest.java
├── sql/ftp_db.sql
├── HUONG_DAN_KET_NOI_2_MAY.md
└── pom.xml
```

---

## Tác giả

- **Chamm286** — [GitHub](https://github.com/Chamm286)

## License

Dự án phục vụ mục đích học tập (Đồ án môn Lập trình mạng).
