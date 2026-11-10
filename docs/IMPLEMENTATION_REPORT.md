# Báo cáo nâng cấp RandomConnect — 2026-11-07

## Backend

Đã thêm JPA/MySQL/Flyway và migration 7 bảng; pool3, UTC, TLS xác minh hostname, validate schema và health DB. Auth BCrypt + JWT + hashed rotating refresh session; CSRF cookie, CORS exact origins, guest TTL, WS ticket một lần và thu hồi socket khi logout. ConversationService persist text/attachment, dedupe clientMessageId, participant ACL, receipt, history phân trang và hide riêng. Matching/outbox/signaling hiện tại giữ lại; tích hợp transaction trước khi phát event.

StorageService S3-compatible có signed GET60 giây, bucket private; upload kiểm tra MIME/signature/name/size/quota, giới hạn song song, revalidate room sau PUT và xóa object bù khi Next. Voice xác minh audio-only và duration bằng ffprobe. Retention dọn expired data/files; startup recovery đóng room cũ sau restart. Docker runtime non-root có ffprobe và entrypoint giải mã truststore từ Render secret file base64.

## Frontend

Thêm AuthPanel, auth store/credentials memory-only và refresh interceptor; router guard, ProfileView, HistoryView. Màn chat có thông báo retention, trạng thái tin nhắn, AttachmentComposer/View và VoiceComposer. useVoiceRecorder chọn codec hỗ trợ, xin mic chủ động, tạo preview và dọn stream riêng khi hủy/Next. Giao diện giữ phong cách/responsive hiện tại, không thêm UI framework lớn. Stream cuộc gọi không dùng để ghi âm.

## Phụ thuộc triển khai

Aiven đã kiểm tra kết nối TLS thành công với CA được cung cấp; log người dùng xác nhận V1 tạo schema thành công trên MySQL8.4. Chưa có Object Storage credential nên chưa test file production. Không có Docker Engine đang chạy để build/run image. Chưa xác minh hai thiết bị thật, cookie cross-site, codec audio và video call đang gửi attachment. Agent chưa commit/push/deploy; V1 trên Aiven được chạy bởi backend local của người dùng theo log cung cấp. H2 MySQL mode không thay thế kiểm thử MySQL staging; URL signed vẫn dùng được đến khi hết60 giây. Reconnect tạo phiên ghép mới, không resume media cũ. Chỉ hỗ trợ một backend instance.

Sơ đồ và bảng: [Database](DATABASE_DESIGN.md). API và phiên: [Auth](AUTHENTICATION.md). History/events: [Chat History](CHAT_HISTORY.md). File/voice/storage: [File Audio](FILE_AUDIO_SHARING.md). Toàn bộ environment/lệnh chạy/TLS/Render/Vercel: [Deployment Guide](DEPLOYMENT_GUIDE.md). Kết quả thực tế: [Testing](TESTING.md).

---

## Phụ lục lịch sử MVP trước nâng cấp

> Tài liệu MVP trước nâng cấp. Với database/auth/history/file/voice, xem [Database](DATABASE_DESIGN.md), [Auth](AUTHENTICATION.md), [History và events hiện hành](CHAT_HISTORY.md), [Storage](FILE_AUDIO_SHARING.md), [Deployment](DEPLOYMENT_GUIDE.md). Các quy tắc matching/signaling giữ lại trừ phần được tài liệu mới cập nhật.

# Báo cáo triển khai theo phase

Tất cả file là file mới vì workspace ban đầu trống; chưa có Git repository nên không có commit/push hay thay đổi cũ cần ghi đè. Không có INSTRUCTION.md để sửa; không tạo file giả thay mẫu giảng viên. Không bịa thông tin thành viên.

| Phase | File / chức năng | Xác minh thực tế / blocker |
|---|---|---|
| 0 Audit & design | docs/ARCHITECTURE.md, PROTOCOL.md, TESTING.md | Workspace trống; có thiết kế và acceptance criteria. Thiếu repo/mẫu giảng viên được ghi rõ. |
| 1 Scaffold & health | server/pom.xml, wrapper, application, HealthController, client/Vite/Vue | Health integration 1 test passed; client build passed. |
| 2 Socket & match | ChatWebSocketHandler, WebSocketConfig, MatchmakingService, RoomService, models, socketClient và store | 8 backend tests passed (7 matchmaking + health), gồm 80 client; frontend build passed. |
| 3 Text & lifecycle | CHAT_MESSAGE, Next/Stop/disconnect, ChatBox/Control UI | 13 backend tests passed; frontend build passed. |
| 4 WebRTC | SignalingService, webrtcClient, useMedia, VideoPlayer, views | 21 backend tests và 14 frontend tests passed, build passed ở checkpoint. Mã media thật đã nối; exit criterion nhìn/nghe trên browser **chưa xác nhận**. |
| 5 Reliability | validation, role/membership, heartbeat, rate limits, room generation, TCP integration, TURN HMAC | Cuối cùng 26 backend + 16 frontend tests passed, build/JAR passed, npm audit 0. WebSocket TCP signaling/lifecycle passed; camera/audio vẫn cần manual. |
| 6 Packaging & docs | 3 READMEs, architecture/protocol/testing/deployment/report, diagram.png, env examples, npm lockfile | Sơ đồ PNG đã mở kiểm tra; live health và chat smoke passed trên 8081. 8080 có PID 4368. Chưa có screenshots thật, Coturn/HTTPS thực hoặc Docker. |

## Lệnh và kiểm chứng

README mô tả Java 21 + `mvnw.cmd spring-boot:run`, `npm ci`, `npm run dev`, test/build. Lệnh backend mặc định đã thử và báo cổng 8080 bận; chạy lại với PORT=8081 thành công. Frontend 5173 đã trả HTML 200. Native WebSocket live smoke xác nhận session, matching, chat và Next/PARTNER_LEFT trên 8081. Kết quả chi tiết và checklist còn lại ở TESTING.md.

Có `source/client/.env.local` **chỉ trên máy này**, trỏ localhost:8081; file được ignore, không chứa secret. Cache `.tools/`, log/script tạm `.tmp/`, node_modules, target và dist được ignore. Ở lần triển khai ban đầu chưa thực hiện publish, commit hoặc push.

## Giới hạn cần bàn giao

Một instance RAM; không lưu lịch sử chat/media, không đăng nhập, không moderation. Cấp TURN credential đã triển khai và có unit test nhưng chưa cấu hình Coturn host thật. Credential 10 phút chưa được tự refresh trong cuộc gọi dài; ghép lại để lấy credential mới. Browser host không có sẵn; native Computer Use dừng vì URL Chrome không xác định chắc chắn. Không tiếp tục thao tác UI và không khẳng định browser/video E2E passed.

## File bàn giao

```text
README.md
.gitignore
docs/ARCHITECTURE.md
docs/DEPLOYMENT.md
docs/PROTOCOL.md
docs/TESTING.md
source/.gitignore
source/client/.env.example
source/client/README.md
source/client/index.html
source/client/package-lock.json
source/client/package.json
source/client/src/App.vue
source/client/src/components/ChatBox.test.js
source/client/src/components/ChatBox.vue
source/client/src/components/ConnectionStatus.vue
source/client/src/components/ControlBar.vue
source/client/src/components/VideoPlayer.vue
source/client/src/composables/useMedia.js
source/client/src/composables/useMedia.test.js
source/client/src/main.js
source/client/src/router/index.js
source/client/src/services/api.js
source/client/src/services/iceConfig.js
source/client/src/services/socketClient.js
source/client/src/services/socketClient.test.js
source/client/src/services/webrtcClient.js
source/client/src/services/webrtcClient.test.js
source/client/src/stores/sessionStore.js
source/client/src/stores/sessionStore.test.js
source/client/src/style.css
source/client/src/views/ChatRoomView.vue
source/client/src/views/HomeView.vue
source/client/vite.config.js
source/server/.env.example
source/server/.mvn/wrapper/maven-wrapper.properties
source/server/README.md
source/server/mvnw
source/server/mvnw.cmd
source/server/pom.xml
source/server/src/main/java/com/randomconnect/RandomConnectApplication.java
source/server/src/main/java/com/randomconnect/config/WebSocketConfig.java
source/server/src/main/java/com/randomconnect/controller/HealthController.java
source/server/src/main/java/com/randomconnect/dto/SocketMessage.java
source/server/src/main/java/com/randomconnect/model/ChatRoom.java
source/server/src/main/java/com/randomconnect/model/GuestSession.java
source/server/src/main/java/com/randomconnect/service/IceConfigService.java
source/server/src/main/java/com/randomconnect/service/MatchmakingService.java
source/server/src/main/java/com/randomconnect/service/RoomService.java
source/server/src/main/java/com/randomconnect/service/SignalingService.java
source/server/src/main/java/com/randomconnect/util/MessageSender.java
source/server/src/main/java/com/randomconnect/websocket/ChatWebSocketHandler.java
source/server/src/main/resources/application.yml
source/server/src/test/java/com/randomconnect/HealthTest.java
source/server/src/test/java/com/randomconnect/IceConfigTest.java
source/server/src/test/java/com/randomconnect/MatchmakingTest.java
source/server/src/test/java/com/randomconnect/SocketIntegrationTest.java
statics/diagram.png
statics/screenshots/README.md
docs/IMPLEMENTATION_REPORT.md
```

## Cập nhật cổng

Theo yêu cầu người dùng, backend mặc định chuyển sang 8081. Frontend REST/WebSocket defaults, env examples, README và reverse proxy examples đã đồng bộ sang 8081. Không cần biến PORT để chạy cổng này. Các ghi nhận thử cổng 8080 ở trên là kết quả lịch sử trước khi thay đổi.

## Bàn giao GitHub

Theo yêu cầu tiếp theo của người dùng, dự án được khởi tạo Git trên nhánh `main` và đưa lên https://github.com/hippolodo/L-p-Tr-nh-M-ng. Repository remote chưa có nhánh tại thời điểm kiểm tra. Chỉ đưa mã nguồn, lockfile, Maven Wrapper, tài liệu, env examples và sơ đồ; loại trừ `.env.local`, cache, dependency, logs và build output.
