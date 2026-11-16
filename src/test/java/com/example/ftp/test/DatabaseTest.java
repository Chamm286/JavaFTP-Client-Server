package com.example.ftp.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseTest {
    public static void main(String[] args) {
        System.out.println("Bắt đầu test kết nối...");

        String url = "jdbc:mysql://localhost:3307/Lab2_ftp_db"
                + "?useUnicode=true&characterEncoding=UTF-8"
                + "&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Ho_Chi_Minh";
        String user = "root";
        String password = "";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) AS total FROM users")) {

                if (rs.next()) {
                    System.out.println("KẾT NỐI THÀNH CÔNG! Database Lab2_ftp_db đã tồn tại.");
                    System.out.println("Số lượng user trong bảng users: " + rs.getInt("total"));
                }
            }
        } catch (Exception e) {
            System.out.println("KẾT NỐI THẤT BẠI!");
            System.out.println("Lỗi chi tiết: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
