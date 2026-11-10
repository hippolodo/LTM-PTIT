# RandomConnect — Lập trình mạng

Ứng dụng chat ngẫu nhiên 1–1: guest hoặc tài khoản, text realtime, WebRTC video/audio, lịch sử cho hai tài khoản, gửi file và tin nhắn thoại. Vue 3/Vite/Pinia + Java 21/Spring Boot 3.5.16 + MySQL/Flyway + private S3-compatible storage.

## Thông tin nhóm (03 thành viên)

| Thành viên | Họ tên / MSSV / Email | Trách nhiệm |
|---|---|---|
| 1 | TODO / TODO / TODO | Backend, queue/room, relay và concurrency |
| 2 | TODO / TODO / TODO | Frontend, chat, trạng thái và UX |
| 3 | TODO / TODO / TODO | WebRTC, TURN và tích hợp |

**Ghi chú mẫu giảng viên:** thư mục ban đầu trống, chưa có Git repository, `INSTRUCTION.md` hay README mẫu. Các thư mục được tạo theo đặc tả; chưa thể xác nhận bố cục khớp mẫu giảng viên. Không tạo hoặc sửa `INSTRUCTION.md`. Nhóm cần bổ sung thông tin thật và lịch sử đóng góp thật.

## Chức năng và chính sách

- Giữ matching/Next/Stop/reconnect, preview camera/mic, WebRTC signaling và TURN ngắn hạn.
- Đăng ký/đăng nhập, BCrypt, access JWT trong RAM, refresh cookie HttpOnly có rotation/revocation; profile display name.
- User ↔ User lưu history có phân trang; mỗi người ẩn riêng sau khi kết thúc. Hội thoại có guest hết hạn mặc định 24 giờ và không xuất hiện trong history tài khoản.
- File PNG/JPEG/WEBP/PDF/TXT/DOCX tối đa 10 MiB; metadata trong DB, binary trong bucket private, URL tải có hạn và kiểm tra thành viên.
- Voice chủ động ghi âm bằng stream riêng, nghe lại/gửi/hủy, tối đa120 giây; backend ffprobe xác minh. Không ghi lại cuộc gọi.

## Chạy code

Cần JDK21, Node22.12+, Aiven credential; CA ri?ng ch? c?n n?u Java ch?a tin ch?ng ch? server; ffprobe và bucket private nếu dùng file/voice. **Backend mới bắt buộc DB và JWT_SECRET**; không còn chạy chỉ bằng Maven với cấu hình trống. Backend tự đọc file .env tại root hoặc source/server; biến môi trường có độ ưu tiên cao hơn. Xem các lệnh PowerShell đầy đủ ở [hướng dẫn triển khai](docs/DEPLOYMENT_GUIDE.md), cấu hình mẫu [server/.env.example](source/server/.env.example).

Sau khi đặt các biến backend:

```powershell
cd source/server
.\mvnw.cmd spring-boot:run
```

Terminal khác từ root:

```powershell
cd source/client
$env:VITE_API_BASE_URL='http://localhost:8081'
$env:VITE_WS_URL='ws://localhost:8081/ws'
npm ci
npm run dev
```

Mở http://localhost:5173. Backend local8081; image Render dùng PORT do nền tảng cấp. Environment ở terminal ghi đè .env.local đang trỏ Render. Frontend mới cần backend mới có API auth; không deploy riêng frontend trước.

## Tài liệu bàn giao

- [Bạn bè, tìm tài khoản và chat riêng](docs/FRIENDS_AND_DIRECT_CHAT.md)
- [Thiết kế Database và sơ đồ quan hệ](docs/DATABASE_DESIGN.md)
- [Authentication, API auth, cookie/CSRF](docs/AUTHENTICATION.md)
- [Chat history, privacy, REST/WS events](docs/CHAT_HISTORY.md)
- [File/audio, storage, quota, giới hạn](docs/FILE_AUDIO_SHARING.md)
- [Aiven TLS, Render/Vercel, environment và lệnh chạy](docs/DEPLOYMENT_GUIDE.md)
- [Kết quả kiểm thử và checklist E2E](docs/TESTING.md)
- [WebRTC/NAT/TURN](docs/DEPLOYMENT.md)

## Kiểm thử và giới hạn

Backend: `source/server/mvnw.cmd -f source/server/pom.xml verify`. Frontend trong source/client: `npm test`, `npm run build`, `npm audit`. Tests dùng H2 MySQL mode và storage giả lập, không cần secret production. Xem TESTING.md để biết số lượng và những phần chưa xác minh.

Matching/room vẫn ở RAM và chỉ hỗ trợ một backend instance; restart đóng room, tài khoản còn history trong DB. STUN-only không qua mọi NAT, cần TURN cho mạng hạn chế. Aiven đã kết nối TLS và log người dùng xác nhận migration V1 thành công. Chưa kiểm chứng bucket thật, Docker runtime hoặc video/voice trên hai thiết bị thật. Render/Vercel khác site có thể bị trình duyệt chặn cookie; ưu tiên custom domain cùng site. Quota/rate limit là mức demo, chưa thay thế moderation, antivirus, abuse protection hoặc load test production.

Agent chưa commit/push/deploy. Người dùng đã chạy backend local và log xác nhận Flyway áp dụng V1 trên Aiven. Trước khi push vào repo bật Auto Deploy, cấu hình secret và truststore trên Render theo Deployment Guide. Không gửi password/key qua chat hoặc đưa chúng vào VITE_.
