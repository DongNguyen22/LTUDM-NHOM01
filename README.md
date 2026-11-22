# 🎵 Music Finder Network

Ứng dụng tra cứu thông tin bài hát và ca sĩ theo mô hình **Client–Server** bằng Java.

Client giao tiếp với Server qua **TCP Socket**. Toàn bộ xử lý nghiệp vụ, truy cập cơ sở dữ liệu, gọi API bên ngoài, cache và tìm kiếm gần đúng được thực hiện phía Server. Client tập trung vào giao diện JavaFX, hiển thị dữ liệu và phát media từ liên kết do Server cung cấp.

## Kiến trúc tổng quan

```text
JavaFX Client
     │
     │ TCP Socket
     ▼
Java TCP Server
     ├── Authentication
     ├── Search / External API
     ├── Playlist / History
     ├── Cache / Statistics
     └── PostgreSQL Database
```

## Cấu trúc thư mục dự kiến

```text
music-finder-network/
│
├── client/
│   ├── README.md
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/musicfinder/client/
│           │       ├── controller/
│           │       ├── navigation/
│           │       ├── network/
│           │       ├── player/
│           │       └── MainApp.java
│           │
│           └── resources/
│               ├── view/
│               ├── css/
│               └── assets/
│
├── server/
│   ├── README.md
│   ├── pom.xml
│   └── src/
│       └── main/
│           └── java/
│               └── com/musicfinder/server/
│                   ├── network/
│                   ├── handler/
│                   ├── service/
│                   ├── repository/
│                   ├── api/
│                   ├── model/
│                   ├── config/
│                   └── util/
│
├── shared/
│   ├── README.md
│   ├── pom.xml
│   ├── tools/
│   └── src/
│       └── main/
│           └── java/
│               └── com/musicfinder/shared/
│                   ├── dto/
│                   ├── request/
│                   ├── response/
│                   └── enums/
│
├── database/
│   ├── README.md
│   ├── music_db.sql
│  
│
├── docs/
│   ├── README.md
│   ├── erd.png
│   └── ZING_MP3_API.md
│
├── .gitignore
├── pom.xml
└── README.md

## Chức năng chính

- Đăng ký, đăng nhập và cập nhật thông tin người dùng.
- Tra cứu bài hát theo tên.
- Tra cứu ca sĩ Việt Nam.
- Hiển thị thông tin bài hát/ca sĩ và phát audio/video từ link hỗ trợ.
- Quản lý playlist cá nhân.
- Lưu và xem lịch sử tra cứu.
- Server xử lý đồng thời nhiều client.
- Cache kết quả để giảm số lần gọi API.
- Fuzzy Search để gợi ý khi người dùng nhập sai.
- Thống kê bài hát/ca sĩ được tra cứu nhiều.

## Công nghệ dự kiến

- Java 21
- JavaFX
- TCP Socket
- Maven
- PostGreSql
- JDBC
- Git & GitHub

## Phân công

| Thành viên         | MSSV       | Vai trò                         | Phụ trách chính                                                    |
|--------------------|------------|---------------------------------|--------------------------------------------------------------------|
| Nguyễn Văn Đông    | 3124410072 | Client Lead                     | JavaFX GUI, Socket Client, Media Player                            |
| Đỗ Đạt             | 3124410054 | Server Lead+Data                | TCP Server, Multi-thread, Authentication, Search/API, Fuzzy Search |
| Nguyễn Trường Hiệp | 3124410089 | Data & Integration Lead + Server| Database, Repository, Playlist/History, Cache/Statistics, Deploy   |

## Nguyên tắc kiến trúc

1. Client không truy cập database trực tiếp.
2. Client không gọi external API trực tiếp.
3. Business logic được xử lý ở Server.
4. Client và Server giao tiếp thông qua TCP protocol đã thống nhất.
5. DTO và protocol dùng chung đặt trong module `shared`.
6. Các thành viên phát triển trên branch riêng và merge qua Pull Request.

## Git workflow đề xuất

```text
main
├── feature/client-...
├── feature/server-...
└── feature/database-...
```



## Trạng thái

Hoàn thành.
