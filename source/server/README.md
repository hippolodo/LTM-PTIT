# RandomConnect server

Java21, Spring Boot3.5.16, Spring Security, JPA/MySQL, Flyway, native WebSocket, S3-compatible SDK và ffprobe. Cổng local8081. Queue/room RAM, một instance; user/history/attachment metadata trong DB.

**Cần cấu hình DB TLS và JWT_SECRET trước khi chạy**. `.env.example` là tham khảo, Chép thành .env để backend tự nạp khi chạy từ root hoặc source/server. Xem [hướng dẫn Aiven/Render/local](../../docs/DEPLOYMENT_GUIDE.md).

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
# Đặt DB_*, JWT_SECRET và truststore theo Deployment Guide.
.\mvnw.cmd spring-boot:run
```

Build/test dùng profile test H2 và key chỉ cho test, không đụng production:

```powershell
.\mvnw.cmd verify
```

GET /api/health kiểm tra DB; /ws nhận ticket ngắn hạn từ POST /api/socket-ticket. [Auth](../../docs/AUTHENTICATION.md), [history/API/events](../../docs/CHAT_HISTORY.md), [upload/voice](../../docs/FILE_AUDIO_SHARING.md), [schema](../../docs/DATABASE_DESIGN.md).

Docker context source/server, Dockerfile ở module. Image kích hoạt render, ALLOWED_ORIGINS bắt buộc, Secure/None cookies, bind PORT mặc định10000, non-root và runtime ffprobe. Docker entrypoint hỗ trợ DB_TRUSTSTORE_BASE64_PATH cho Render text secret file. STORAGE_PROVIDER=disabled nếu chưa có bucket: text/video vẫn hoạt động; file/voice503. Không lưu file lâu dài trên disk Render.
