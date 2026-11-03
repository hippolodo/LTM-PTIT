# RandomConnect server

JDK 21, Spring Boot 3.5.16, Maven Wrapper 3.3.4 / Maven 3.9.9. Không database. Một instance giữ sessions/queue/rooms trong RAM.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
.\mvnw.cmd spring-boot:run
```

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/randomconnect-0.0.1-SNAPSHOT.jar
```

macOS/Linux: `sh ./mvnw test`, `sh ./mvnw spring-boot:run`; hoặc cấp executable rồi chạy `./mvnw`.

Health `GET http://localhost:8081/api/health` trả `{status:"UP",service:"randomconnect"}`. WebSocket `/ws`; [protocol](../../docs/PROTOCOL.md).

Environment: PORT=8081; ALLOWED_ORIGINS là danh sách origin phân cách dấu phẩy; SESSION_TIMEOUT_MS=45000; STUN_URLS; TURN_URLS; TURN_SHARED_SECRET. `.env.example` chỉ là mẫu; đặt biến trong shell trước khi chạy. Chỉ giữ TURN secret ở server/Coturn. Có TURN_URLS mà thiếu secret thì server từ chối khởi động. Credential ngắn hạn 600 giây được ký HMAC-SHA1, cấp mới mỗi MATCH_FOUND.

MatchmakingService giữ state lock chung cho queue/room, outbox có thứ tự. Dispatcher gửi ngoài state lock; mỗi socket được ConcurrentWebSocketSessionDecorator giới hạn 2 giây/256 KiB buffer. RoomService kiểm tra membership; SignalingService kiểm tra schema SDP/ICE và role offer/answer. Guest identity lấy từ socket, không tin senderId. Không log nội dung chat/SDP/ICE.

App envelope tối đa 64 KiB ký tự; container nhận đến 128 KiB để trả ERROR cho input quá cỡ vừa phải, input lớn hơn giới hạn container bị đóng socket. Heartbeat client gửi PING mỗi 15 giây; server hết hạn sau 45 giây im lặng, scan mỗi 5 giây. Các giới hạn rate ghi trong protocol. Origin allowlist không thay thế chống abuse cho public server.

Tests: matching/concurrency/lifecycle, relay/membership, stale room, rate/timeout, TURN HMAC, REST health, TCP WebSocket integration. Xem [kết quả](../../docs/TESTING.md).

Cổng mặc định là **8081**; không cần đặt biến PORT. Nếu đổi cổng, cập nhật VITE_API_BASE_URL/VITE_WS_URL tương ứng.
