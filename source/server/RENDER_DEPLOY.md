# Triển khai RandomConnect Backend lên Render

## 1. Backend thực tế và phạm vi thay đổi

- Java **21**, Spring Boot **3.5.16**, Maven Wrapper **3.3.4** tải Maven **3.9.9** theo `.mvn/wrapper/maven-wrapper.properties`.
- REST `/api/health`; native WebSocket JSON `/ws`, không dùng STOMP. Chat và WebRTC SDP/ICE dùng cùng WebSocket. Media đi P2P hoặc TURN, không đi qua Spring Boot.
- State session/queue/room nằm trong RAM của **một instance**. Không database, JPA hoặc JDBC; không cần thêm database khi deploy. Restart/redeploy làm mất phiên và room.
- Maven tạo executable JAR `target/randomconnect-0.0.1-SNAPSHOT.jar`; JAR `.original` không phải artifact để chạy.
- Local vẫn dùng `application.yml`, cổng **8081**. Image Docker kích hoạt `application-render.yml`, bind **0.0.0.0**, đọc biến **PORT**, fallback **10000**. Không đổi endpoint, envelope, matchmaking, chat hay signaling.
- REST CORS và WebSocket Origin dùng chung `ALLOWED_ORIGINS`: trim danh sách, bỏ mục trống/trùng, chỉ nhận origin HTTP(S) cụ thể, không wildcard/path/trailing slash. Profile render bắt buộc khai báo biến này; không có origin localhost mặc định trong production.
- Frontend không cần sửa source: đã đọc VITE_API_BASE_URL/VITE_WS_URL.

## 2. Sửa lỗi không tìm thấy Dockerfile

File đã thêm ở **`source/server/Dockerfile`**, không phải repository root. Trên Render chọn **New → Web Service**, kết nối repository GitHub `https://github.com/hippolodo/LTM-PTIT` và cấu hình **đồng bộ cả ba đường dẫn**:

| Trường Render | Giá trị |
|---|---|
| Service type | Web Service |
| Language / Runtime | Docker |
| Branch | main |
| Root Directory | `source/server` |
| Dockerfile Path | `./Dockerfile` |
| Docker Build Context Directory | `.` |
| Docker Command | Để trống, dùng ENTRYPOINT trong image |
| Health Check Path | `/api/health` |
| Instance | Free nếu chỉ demo; giữ **1 instance** |
| Build Command / Start Command | Không nhập lệnh Maven/Java riêng cho Docker runtime |

Nếu UI không hiện Docker Build Context Directory, kiểm tra trong Settings → Build & Deploy; khi Root Directory là source/server, context mặc định cũng phải là thư mục này. Không nhập `source/server/Dockerfile` lần nữa khi đã có Root Directory=source/server, vì có thể thành `source/server/source/server/Dockerfile`.

Cách tương đương nếu bạn cố ý để Root Directory trống: Dockerfile Path=`./source/server/Dockerfile`, Docker Build Context Directory=`./source/server`. Không trộn hai cách. Đường dẫn Dockerfile và context được Render tính tương đối với Root Directory. [Render monorepo](https://render.com/docs/monorepo-support), [Docker](https://render.com/docs/docker).

Kiểm tra trên GitHub nhánh main đã có file này trước khi redeploy. Chỉ sửa file trên máy nhưng chưa push thì Render vẫn build commit cũ và báo không tìm thấy Dockerfile.

## 3. Dockerfile và bộ nhớ

Build stage dùng `eclipse-temurin:21-jdk-jammy`. Copy POM và Maven Wrapper trước, chạy `dependency:go-offline` để cache dependency; chỉ sau đó copy src và chạy **`./mvnw -B -ntp verify`**. Tests thất bại sẽ chặn build. Wrapper được cấp executable và chuẩn hóa CRLF để dùng được với checkout Windows. Không dùng Maven cài trên host.

Runtime stage dùng `eclipse-temurin:21-jre-jammy`, chỉ copy JAR đã build, chạy bằng user `app` UID/GID 10001. Không có Maven, source, target của host hay frontend trong runtime image. Docker context chỉ cần source/server; `.dockerignore` chỉ đưa POM, wrapper và src vào context, loại file local/secrets/generated output. Không có build ARG chứa secret.

`JAVA_TOOL_OPTIONS` mặc định giới hạn heap tối đa 50% bộ nhớ container (~256 MiB với giới hạn 512 MiB), metaspace 128 MiB, code cache 48 MiB, direct memory 32 MiB, thread stack 512 KiB; thoát khi OOM để nền tảng khởi động lại. Profile render giới hạn Tomcat 40 request threads, min-spare=4. Đây là ngân sách khởi điểm cho demo, không phải bảo đảm chịu tải hoặc tổng RSS luôn dưới 512 MiB. JVM và thư viện vẫn cần native memory ngoài các giới hạn này; theo dõi Metrics/Logs khi tăng client. Build stage có JDK/test JVM riêng, khác runtime 512 MiB.

`EXPOSE 10000` là metadata; cổng thực do Spring đọc PORT khi container chạy. Render yêu cầu listen 0.0.0.0 và mặc định dùng PORT=10000. TLS được Render terminate ở edge; Java nhận HTTP/WS bên trong, browser dùng HTTPS/WSS bên ngoài. Không tự tạo certificate trong container. [Render Web Services](https://render.com/docs/web-services).

## 4. Environment Variables trên Render

| Biến | Giá trị / yêu cầu |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `render`; image đã có mặc định, giữ giá trị này nếu nhập lại trên Dashboard |
| `ALLOWED_ORIGINS` | **Bắt buộc**: origin frontend thật, ví dụ `https://your-app.vercel.app` |
| `PORT` | Để Render cấp, thường `10000`; không cần hardcode 8081 |
| `SESSION_TIMEOUT_MS` | Tùy chọn, mặc định `45000` |
| `STUN_URLS` | Tùy chọn, mặc định `stun:stun.l.google.com:19302`; danh sách phân cách dấu phẩy |
| `TURN_URLS` | Tùy chọn nếu đã có TURN: `turn:turn.example.com:3478?transport=udp,turns:turn.example.com:5349?transport=tcp` |
| `TURN_SHARED_SECRET` | Chỉ đặt nếu có TURN_URLS; phải khớp Coturn; đánh dấu secret trên Dashboard, không commit |
| `JAVA_TOOL_OPTIONS` | Tùy chọn; image đã có ngân sách JVM, chỉ ghi đè khi đã hiểu và giữ các giới hạn cần thiết |

Ví dụ ALLOWED_ORIGINS với hai frontend cụ thể:

```text
https://your-app.vercel.app,https://your-preview.vercel.app
```

Không dùng `*`, `https://*.vercel.app`, URL có `/` cuối hoặc `/chat`. Các URL trong tài liệu là mẫu, phải thay bằng domain thật. Nếu chỉ demo từ frontend localhost, có thể thêm `http://localhost:5173` một cách rõ ràng vào allowlist, rồi bỏ khi không cần. Không đặt localhost vào API/WebSocket URL của bản Vercel production. Spring không tự đọc `.env`; cấu hình Dashboard là biến môi trường của tiến trình.

TURN_SHARED_SECRET chỉ ở backend và Coturn. Backend cấp credential tạm 600 giây qua WebSocket và cấp mới khi MATCH_FOUND. Không đưa shared secret vào biến VITE_, image, Docker ARG hoặc Git. Có TURN_URLS mà không có secret sẽ làm backend từ chối khởi động.

## 5. Commit và push (người dùng thực hiện khi sẵn sàng)

Trong lần cấu hình Docker này, Codex **không tự commit hoặc push**. Từ repository root, xem diff rồi chạy:

```powershell
git status --short
git diff

git add README.md docs/DEPLOYMENT.md source/server/Dockerfile source/server/.dockerignore source/server/RENDER_DEPLOY.md source/server/README.md source/server/.env.example source/server/src/main/resources/application-render.yml source/server/src/main/java/com/randomconnect/config/WebSocketConfig.java source/server/src/test/java/com/randomconnect/RenderProfileIntegrationTest.java source/server/src/test/java/com/randomconnect/WebSocketConfigTest.java
git commit -m "Configure Docker and Render deployment for backend"
git remote -v
```

Remote local lúc kiểm tra vẫn mang tên URL repository cũ, trong khi URL LTM-PTIT đã trả cùng commit HEAD. Nếu repository chính thức đã đổi tên, có thể cập nhật remote trước khi push:

```powershell
git remote set-url origin https://github.com/hippolodo/LTM-PTIT.git
git push origin main
```

Không push `.env.local`, token, TURN secret, `.tools/`, `.tmp/`, node_modules hoặc target. File Dockerfile phải thực sự có trên đúng repo/branch Render đang kết nối.

## 6. Deploy / redeploy / Logs

1. Chọn service Docker với đường dẫn ở mục 2, đặt ALLOWED_ORIGINS rồi Create Web Service.
2. Service đang có: sửa Root Directory, Dockerfile Path, Docker Build Context; Save, rồi **Manual Deploy → Deploy latest commit** sau khi push.
3. Nếu nghi ngờ cache cũ: dùng **Clear build cache & deploy**. Không cần xóa cache cho mỗi lần deploy; image có lớp dependency cache.
4. Xem Deploys/Logs: phải thấy tải Java 21, Maven build/test success, rồi Spring báo profile render và Tomcat chạy trên PORT được cấp.
5. Nếu crash, đọc lỗi đầu tiên trước BUILD FAILURE. OOM: xem giới hạn RAM/JVM; thiếu origin: sửa ALLOWED_ORIGINS; missing Dockerfile: kiểm tra commit và cả ba đường dẫn.

Kiểm tra từ PowerShell (thay domain thật):

```powershell
Invoke-RestMethod https://your-backend.onrender.com/api/health
```

Kết quả: `status=UP`, `service=randomconnect`. Đường dẫn `/` có thể trả 404 vì backend không phục vụ trang frontend; điều đó không có nghĩa health hỏng.

## 7. Vue 3 trên Vercel → Render

Vercel project Root Directory=`source/client`, framework Vite, Build Command=`npm run build`, Output Directory=`dist`, install theo lockfile (`npm ci`). Trong Settings → Environment Variables, đặt cho đúng môi trường Production/Preview:

```dotenv
VITE_API_BASE_URL=https://your-backend.onrender.com
VITE_WS_URL=wss://your-backend.onrender.com/ws
```

Redeploy **frontend** sau khi đổi biến VITE_ vì chúng được đưa vào bundle khi build. Backend ALLOWED_ORIGINS phải chứa domain frontend đang mở trong thanh địa chỉ. Domain Vercel preview thay đổi thì cần thêm đúng origin hoặc dùng domain preview ổn định; không mở wildcard cho mọi Vercel app.

Giữ VITE_ICE_SERVERS và VITE_ICE_CONFIG_URL **không đặt** nếu muốn dùng ICE servers/credential TURN do backend cấp. Không ghi đè bằng STUN-only rồi kỳ vọng TURN từ backend vẫn được dùng. Không sửa mã frontend trong thay đổi này vì các biến trên đã được hỗ trợ.

Mở frontend HTTPS trên hai browser/thiết bị, cấp quyền media rồi Start; DevTools Network → WS phải thấy `/ws` status **101** và SESSION_READY. Chỉ dùng **wss://** cho Render public, không dùng ws:// rồi chờ redirect. [Render WebSockets](https://render.com/docs/websocket), [Vite trên Vercel](https://vercel.com/docs/frameworks/frontend/vite).

## 8. Kiểm tra local và Docker

Local thường vẫn giữ cổng 8081:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
cd source/server
.\mvnw.cmd verify
```

Docker build từ repository root:

```powershell
docker build -t randomconnect-backend:local ./source/server
docker run --rm --name randomconnect-demo --memory=512m -p 8082:10000 -e PORT=10000 -e ALLOWED_ORIGINS=http://localhost:5173 randomconnect-backend:local
```

Terminal khác:

```powershell
Invoke-RestMethod http://localhost:8082/api/health
```

Muốn dùng frontend local với container: đặt VITE_API_BASE_URL=http://localhost:8082, VITE_WS_URL=ws://localhost:8082/ws trong `.env.local`, restart Vite. Sau khi thử xong trả lại cấu hình local trước đó. Các lệnh trong mục này là cách tái chạy; xem mục kết quả bên dưới để biết những gì thực sự đã được chạy ở phiên này.

## 9. Render Free và WebRTC

Free service có thể spin down sau **15 phút không có inbound traffic**; HTTP và WebSocket messages đều tính là traffic. Lần wake-up có thể khoảng một phút. Client hiện tại health timeout 5 giây và heartbeat 45–60 giây nên lần đầu có thể báo lỗi: mở health endpoint, đợi server sẵn sàng rồi nhấn **Kết nối lại** / Start. Không gọi đó là lỗi Docker nếu instance đang cold start. Không bổ sung cron ping để né spin-down. [Giới hạn Free](https://render.com/docs/free).

Redeploy/restart/maintenance có thể đóng WebSocket. Client reconnect thủ công thành guest mới, không tự phục hồi room. Giữ **1 instance** vì sessions/queue/rooms chưa có shared state; scale nhiều instance có thể khiến hai người không ghép được.

Render Web Service này chỉ xử lý HTTP/WebSocket signaling; **không phải TURN server**, không thay thế hạ tầng relay UDP/TCP. Muốn demo qua hai mạng/NAT hạn chế, dùng Coturn hoặc TURN provider riêng, đặt TURN_URLS/TURN_SHARED_SECRET đúng cơ chế shared-secret hiện có. Nếu provider chỉ cấp username/password tĩnh khác cơ chế này, không điền mật khẩu đó vào TURN_SHARED_SECRET và kỳ vọng tương thích. HTTPS/WSS không tự bảo đảm video/audio xuyên NAT. STUN-only chỉ đủ trong một số mạng.

Credential TURN hiện có hạn 10 phút và chưa tự refresh trong cuộc gọi dài; ghép lại để lấy credential mới. Vẫn cần người dùng xác minh remote video/audio thật qua hai mạng. Chỉ demo với người tham gia đồng ý; chưa có moderation cho phát hành công khai.

## 10. Kết quả kiểm tra phiên cấu hình này

Thực hiện ngày **2026-11-04**, múi giờ Asia/Saigon:

| Kiểm tra | Kết quả thực tế |
|---|---|
| Maven Wrapper / Java 21 `verify` | **BUILD SUCCESS, 32 tests**, 0 failures/errors/skipped; JAR đã repackage |
| Các test cũ | 26 tests health, matchmaking, lifecycle, chat/signaling TCP WebSocket và TURN tiếp tục đạt |
| Test mới | 4 integration tests profile render (bind config, health/CORS và WebSocket origin), 2 tests kiểm tra danh sách origin |
| JAR chạy thật với profile render và PORT động | Health HTTP 200, JSON giữ nguyên; origin tin cậy được phản hồi đúng, origin lạ HTTP 403 |
| Thiếu ALLOWED_ORIGINS ở profile render | Tiến trình từ chối khởi động, lỗi nhắc đúng biến bắt buộc |
| Ngân sách JVM | JAR chạy với MaxRAM=512m và các cờ JVM như image; đây **không phải** kiểm thử cgroup Docker 512 MiB hoặc load test |
| Docker image build / container run | **Chưa xác minh**: Docker CLI 29.2.1 có sẵn, nhưng engine khởi động rồi trả HTTP 500 ở API `/info` và `/_ping`; build dừng trước khi thực thi Dockerfile. Docker Desktop do phiên này khởi động đã được dừng lại |
| Render/Vercel thực, TLS/WSS public, media qua Internet | **Chưa deploy/kiểm chứng**; cần cấu hình tài khoản và domain thực theo các mục trên |
| Git / frontend | Codex không tự commit/push; không sửa source frontend, POM, Java/Spring Boot version hoặc logic nghiệp vụ |

Lần Maven đầu tiên vẫn 32 tests đạt nhưng Surefire cảnh báo chậm thoát fork JVM khi Docker Desktop đang khởi động lỗi. Test HTTP client mới đã được cleanup; sau khi dừng Docker và chạy lại với logging gọn, `verify` hoàn tất khoảng 25 giây, không còn cảnh báo đó. Không đổi timeout để che lỗi và không bỏ qua tests.

Các log/script kiểm tra nằm trong `.tmp/` bị Git ignore. Các tiến trình JAR smoke test đã được dừng; không chiếm cổng backend của người dùng. Dockerfile chỉ đọc file bên trong source/server, không copy artifact từ cache hoặc target của máy Windows.

File mới: Dockerfile, .dockerignore, RENDER_DEPLOY.md, application-render.yml, RenderProfileIntegrationTest.java, WebSocketConfigTest.java. File cập nhật: WebSocketConfig.java, .env.example, README server; README root và docs/DEPLOYMENT.md chỉ được sửa để liên kết hướng dẫn Docker mới, bỏ mô tả cũ rằng chưa có cấu hình Docker.

Trong lúc kiểm tra, Git HEAD đã chuyển sang `926de24` (`add dockerfile`) bởi thao tác bên ngoài phiên Codex. Phần cấu hình/test đã có trong commit đó; ba file tài liệu README.md, docs/DEPLOYMENT.md và RENDER_DEPLOY.md còn cập nhật kết quả cuối chưa commit tại lúc bàn giao. Giữ nguyên commit/thay đổi đó, không reset hay ghi đè lịch sử.
