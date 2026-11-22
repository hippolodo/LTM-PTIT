# RandomConnect — Ứng dụng chat và gọi video 1–1

RandomConnect là đồ án môn **Lập trình mạng**, cho phép người dùng gặp gỡ ngẫu nhiên, trò chuyện bằng văn bản, gọi video/audio và giữ liên lạc qua bạn bè, tin nhắn riêng. Người dùng có thể tham gia bằng tài khoản hoặc dưới dạng khách (guest).

Dự án gồm frontend **Vue 3** và backend **Spring Boot**. WebSocket xử lý ghép đôi, tin nhắn trong phòng ngẫu nhiên và signaling; WebRTC truyền media giữa hai trình duyệt; MySQL lưu tài khoản và dữ liệu hội thoại; kho S3-compatible riêng tư lưu file và tin nhắn thoại.

## Mục lục

- [Tính năng](#tính-năng)
- [Công nghệ](#công-nghệ)
- [Kiến trúc và luồng hoạt động](#kiến-trúc-và-luồng-hoạt-động)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Yêu cầu môi trường](#yêu-cầu-môi-trường)
- [Cài đặt và chạy local](#cài-đặt-và-chạy-local)
- [Cấu hình môi trường](#cấu-hình-môi-trường)
- [Hướng dẫn sử dụng và demo](#hướng-dẫn-sử-dụng-và-demo)
- [API và WebSocket](#api-và-websocket)
- [Cơ sở dữ liệu](#cơ-sở-dữ-liệu)
- [Kiểm thử và build](#kiểm-thử-và-build)
- [Docker và triển khai](#docker-và-triển-khai)
- [Xử lý lỗi thường gặp](#xử-lý-lỗi-thường-gặp)
- [Giới hạn hiện tại](#giới-hạn-hiện-tại)

## Tính năng

### Tài khoản và phiên truy cập

- Tham gia bằng guest; đăng ký, đăng nhập bằng username hoặc email, đăng xuất.
- Xem hồ sơ và cập nhật tên hiển thị.
- Mật khẩu băm bằng BCrypt; access token JWT lưu trong bộ nhớ frontend.
- Refresh token dùng cookie HttpOnly, có cơ chế rotation và thu hồi phiên; các API auth thay đổi dữ liệu yêu cầu CSRF token.

### Ghép đôi và trò chuyện ngẫu nhiên

- Ghép ngẫu nhiên hai phiên đang chờ vào phòng 1–1.
- Xem trước camera, bật/tắt camera và microphone; gọi video/audio qua WebRTC.
- Gửi tin nhắn văn bản realtime qua WebSocket, tối đa **2.000 ký tự/tin**.
- **Next** để đổi đối tác, **Stop** để dừng; hiển thị trạng thái chờ, kết nối và mất kết nối.
- Tránh ghép lại ngay với đối tác vừa bị bỏ qua trong phiên hiện tại. Khi đối tác chủ động Next/Stop, phía còn lại tự tìm người mới nếu WebSocket còn mở.
- Gửi lời mời và phản hồi kết bạn ngay trong phòng giữa hai tài khoản.

### Bạn bè, chat riêng và gọi trực tiếp

- Tìm tài khoản theo username/tên hiển thị; quản lý lời mời đến, lời mời đã gửi và danh sách bạn bè.
- Chấp nhận, từ chối, hủy lời mời hoặc hủy kết bạn.
- Mở hội thoại riêng với tài khoản khác, kể cả khi chưa kết bạn; tải thêm tin cũ.
- Chat riêng qua REST, cập nhật tin mới bằng **polling khoảng 2 giây** khi mở hội thoại.
- Cửa sổ chat nổi hỗ trợ văn bản, file và tin nhắn thoại.
- Gọi video trực tiếp: mời gọi, nhận/từ chối, kết thúc; xử lý người nhận offline, bận hoặc không trả lời.

### File, tin nhắn thoại và lịch sử

- Chia sẻ PNG, JPEG, WEBP, PDF, TXT UTF-8 và DOCX; mặc định tối đa **10 MiB/file**.
- Ghi âm bằng microphone riêng, nghe lại trước khi gửi hoặc hủy; tối đa **120 giây**. Backend dùng `ffprobe` kiểm tra audio.
- Quota upload **50 MiB/người/hội thoại**; giới hạn **10 yêu cầu upload/phút/định danh**.
- File nằm trong bucket private; API kiểm tra thành viên rồi cấp URL tải có hạn **60 giây**.
- Hội thoại giữa hai tài khoản được lưu lịch sử, có phân trang. Mỗi người có thể ẩn riêng hội thoại đã kết thúc.
- Hội thoại có guest không xuất hiện trong lịch sử tài khoản, mặc định hết hạn sau **24 giờ kể từ lúc tạo** và được tác vụ nền dọn dẹp.
- Giao diện hỗ trợ chế độ sáng/tối. Ứng dụng không ghi lại nội dung cuộc gọi video/audio; tin nhắn thoại là bản ghi do người dùng chủ động tạo.

## Công nghệ

| Thành phần | Công nghệ |
| --- | --- |
| Frontend | Vue 3, Composition API, Vue Router 4, Pinia 3, Axios |
| Công cụ frontend | Vite 7, npm, Vitest 4, jsdom |
| Backend | Java 21, Spring Boot 3.5.16, Maven Wrapper (Maven 3.9.9) |
| Bảo mật | Spring Security, JWT HS256, BCrypt, CSRF, kiểm tra CORS/Origin |
| Realtime và media | Native WebSocket JSON, WebRTC, STUN/TURN |
| Dữ liệu | Spring Data JPA, MySQL, Flyway |
| File và audio | AWS SDK S3-compatible, Apache Tika, FFmpeg/ffprobe |
| Kiểm thử backend | Spring Boot Test, Spring Security Test, H2 ở chế độ MySQL |
| Cấu hình triển khai | Docker cho backend, cấu hình SPA rewrite cho Vercel |

## Kiến trúc và luồng hoạt động

```text
Trình duyệt A                         Trình duyệt B
 Vue / Pinia                           Vue / Pinia
      |                                     |
      +-------- REST / WebSocket -----------+
                        |
                 Spring Boot server
                 |               |
              MySQL        Bucket S3 private

Trình duyệt A <------ WebRTC ------> Trình duyệt B
                 P2P hoặc qua TURN
```

1. Frontend tạo guest hoặc đăng nhập, lấy thông tin định danh.
2. Frontend gọi `POST /api/socket-ticket`, dùng ticket ngắn hạn để mở WebSocket `/ws`.
3. Người dùng cấp quyền camera/mic và bắt đầu tìm đối tác. Backend chọn đối tác trong tập phiên chờ, tạo phòng và hội thoại trong DB.
4. Hai phía nhận `MATCH_FOUND`, trao đổi SDP/ICE qua WebSocket, thiết lập WebRTC. Media truyền trực tiếp hoặc qua TURN; backend Spring Boot không relay media.
5. Tin nhắn trong phòng đi qua WebSocket. File/voice upload qua REST; backend lưu metadata và phát thông báo tới phòng.
6. Chat riêng dùng REST/polling; cuộc gọi trực tiếp dùng nhóm sự kiện `CALL_*` để signaling qua WebSocket.

Hàng chờ, phòng và trạng thái cuộc gọi nằm trong RAM; tài khoản, phiên xác thực và nội dung hội thoại nằm trong MySQL.

## Cấu trúc thư mục

```text
.
├── README.md
├── source/
│   ├── client/
│   │   ├── src/
│   │   │   ├── components/       # Video, chat, file/voice, auth, cuộc gọi
│   │   │   ├── composables/      # Media, ghi âm, chat riêng, theme
│   │   │   ├── router/           # Route và kiểm tra đăng nhập
│   │   │   ├── services/         # REST, WebSocket, WebRTC, cấu hình ICE
│   │   │   ├── stores/           # Auth, phiên ghép đôi, cuộc gọi (Pinia)
│   │   │   ├── views/            # Home, phòng chat, bạn bè, lịch sử, hồ sơ
│   │   │   ├── App.vue
│   │   │   ├── main.js
│   │   │   └── style.css
│   │   ├── tests/components/     # Kiểm thử component; test khác đặt cạnh source
│   │   ├── .env.example
│   │   ├── package.json
│   │   ├── package-lock.json
│   │   ├── vite.config.js
│   │   └── vercel.json
│   └── server/
│       ├── src/main/java/com/randomconnect/
│       │   ├── auth/             # Định danh, token, phiên, rate limit
│       │   ├── config/           # Security, WebSocket, DB, phục hồi khởi động
│       │   ├── controller/       # REST API
│       │   ├── dto/              # Cấu trúc thông điệp
│       │   ├── entity/           # JPA entity
│       │   ├── model/            # Phiên và phòng trong RAM
│       │   ├── repository/       # Truy cập dữ liệu
│       │   ├── service/          # Ghép đôi, hội thoại, bạn bè, gọi, retention
│       │   ├── storage/          # S3, upload, kiểm tra file/audio
│       │   ├── util/             # Gửi thông điệp
│       │   └── websocket/        # Handshake và xử lý WebSocket
│       ├── src/main/resources/
│       │   ├── application.yml
│       │   ├── application-render.yml
│       │   └── db/migration/     # V1: tài khoản/chat; V2: kết nối xã hội
│       ├── src/test/             # Unit/integration test và cấu hình H2
│       ├── .mvn/wrapper/
│       ├── .env.example
│       ├── Dockerfile
│       ├── docker-entrypoint.sh
│       ├── mvnw / mvnw.cmd
│       └── pom.xml
└── statics/
    ├── diagram.png
    └── screenshots/             # Nơi bổ sung ảnh chụp ứng dụng
```

Các route frontend hiện có: `/`, `/friends`, `/history`, `/profile`. Ba route sau yêu cầu đăng nhập.

## Yêu cầu môi trường

- **JDK 21**, cấu hình `JAVA_HOME` và Java trong `PATH`.
- **Node.js >= 22.12.0** và npm.
- **MySQL hỗ trợ TLS**, database đã tạo và tài khoản có quyền thực hiện migration. Có thể dùng dịch vụ như Aiven hoặc MySQL tự quản lý được cấu hình chứng chỉ phù hợp.
- Kết nối mạng để tải Maven và npm dependencies ở lần cài đặt đầu.
- **Tùy chọn:** bucket S3-compatible private để gửi file/voice; `ffprobe` (thuộc FFmpeg) để xử lý tin nhắn thoại; TURN server cho các mạng hạn chế NAT.
- Docker nếu chạy backend bằng container. Không cần Maven cài riêng vì dự án có Maven Wrapper.

**Backend bắt buộc có DB hoạt động, `DB_PASSWORD` và `JWT_SECRET`.** Ngoài profile test, mã nguồn chỉ chấp nhận `DB_SSL_MODE=VERIFY_IDENTITY`; MySQL local chưa bật TLS sẽ không chạy được bằng cách đổi sang `DISABLED`.

## Cài đặt và chạy local

Các lệnh PowerShell bên dưới được chạy từ thư mục gốc dự án, trừ khi có ghi chú khác.

### 1. Kiểm tra công cụ

```powershell
java -version
node -v
npm -v
```

Kết quả cần Java 21 và Node đạt yêu cầu trên. Nếu máy có nhiều JDK, trỏ `JAVA_HOME` tới JDK 21 trước khi chạy Maven Wrapper.

### 2. Cấu hình backend

Chép file mẫu **nếu chưa có `.env`**, giữ cấu hình hiện tại nếu đã có:

```powershell
Copy-Item source/server/.env.example source/server/.env
```

Sửa `source/server/.env` theo database của bạn. Ví dụ tối thiểu (thay toàn bộ giá trị mẫu):

```dotenv
PORT=8081
ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
DB_HOST=mysql.example.com
DB_PORT=3306
DB_NAME=randomconnect
DB_USERNAME=randomconnect_user
DB_PASSWORD=your-database-password
DB_SSL_MODE=VERIFY_IDENTITY
JWT_SECRET=replace-with-a-random-secret-of-at-least-32-bytes
AUTH_COOKIE_SECURE=false
AUTH_COOKIE_SAME_SITE=Lax
STORAGE_PROVIDER=disabled
```

`DB_HOST` chỉ là hostname, không có `https://` hoặc cổng. File mẫu server chứa thông tin host/port của một môi trường Aiven; hãy thay bằng thông tin DB thực tế của bạn.

Sinh secret ngẫu nhiên bằng PowerShell rồi tự điền vào `JWT_SECRET`:

```powershell
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$rng.Dispose()
```

Backend đọc `.env` tại thư mục chạy và `./source/server/.env`; biến môi trường của tiến trình có độ ưu tiên cao hơn. Nên duy trì một file cấu hình local để tránh nhầm giá trị.

Nếu Java chưa tin cậy CA của database, nhập chứng chỉ CA của nhà cung cấp vào truststore riêng bằng `keytool`, rồi cấu hình:

```dotenv
DB_TRUSTSTORE_URL=file:///C:/path/to/db-truststore.p12
DB_TRUSTSTORE_PASSWORD=your-truststore-password
DB_TRUSTSTORE_TYPE=PKCS12
```

Nếu chứng chỉ đã được Java tin cậy, để trống hai biến URL/password như file mẫu. Flyway tự áp dụng migration khi khởi động; JPA kiểm tra schema bằng `ddl-auto=validate`.

### 3. Khởi động backend

```powershell
cd source/server
.\mvnw.cmd spring-boot:run
```

Linux/macOS dùng `./mvnw spring-boot:run` (cấp quyền thực thi bằng `chmod +x mvnw` nếu cần).

Trong terminal khác, kiểm tra:

```powershell
Invoke-RestMethod http://localhost:8081/api/health
```

Khi DB sẵn sàng, API trả HTTP 200:

```json
{"status":"UP","service":"randomconnect"}
```

### 4. Cấu hình và chạy frontend

Mở terminal mới tại thư mục gốc:

```powershell
cd source/client
npm ci
```

Tạo hoặc cập nhật `source/client/.env.local`:

```dotenv
VITE_API_BASE_URL=http://localhost:8081
VITE_WS_URL=ws://localhost:8081/ws
```

**File `.env.example` của client đang trỏ tới Render**, vì vậy cần đổi hai URL trên nếu sao chép file mẫu để chạy local. Nếu terminal đã đặt biến `VITE_*`, chúng có thể ghi đè file cấu hình.

```powershell
npm run dev
```

Mở **http://localhost:5173**. Vite dùng cổng cố định `5173` với `strictPort`; nếu cổng đang bận, cần dừng tiến trình chiếm cổng. Khởi động lại Vite sau khi đổi `.env.local`.

Với `STORAGE_PROVIDER=disabled`, có thể dùng auth, chat và WebRTC; gửi file/voice sẽ không hoạt động cho tới khi cấu hình storage.

## Cấu hình môi trường

File tham chiếu: [server/.env.example](source/server/.env.example) và [client/.env.example](source/client/.env.example).

### Backend

| Biến | Mặc định / ý nghĩa |
| --- | --- |
| `PORT` | `8081` local; profile `render` mặc định `10000` |
| `ALLOWED_ORIGINS` | Local cho phép `http://localhost:5173,http://127.0.0.1:5173`; bắt buộc khai báo ở profile `render` |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Địa chỉ, cổng và database MySQL; đặt rõ theo môi trường |
| `DB_USERNAME`, `DB_PASSWORD` | Tài khoản DB; password không được rỗng |
| `DB_SSL_MODE` | Bắt buộc `VERIFY_IDENTITY` ngoài profile test |
| `DB_POOL_SIZE` | `3` kết nối tối đa |
| `DB_TRUSTSTORE_URL`, `DB_TRUSTSTORE_PASSWORD`, `DB_TRUSTSTORE_TYPE` | Truststore riêng nếu cần; loại mặc định `PKCS12` |
| `DB_TRUSTSTORE_BASE64_PATH` | Đường dẫn file chứa truststore PKCS12 dạng Base64, do Docker entrypoint xử lý |
| `JWT_SECRET` | Bắt buộc, secret ngẫu nhiên ít nhất 32 byte UTF-8 |
| `ACCESS_TOKEN_SECONDS` | `600` giây |
| `AUTH_COOKIE_SECURE`, `AUTH_COOKIE_SAME_SITE` | Local: `false`, `Lax`; profile `render` dùng `true`, `None` |
| `SESSION_TIMEOUT_MS` | `45000` ms; thời gian phiên WebSocket không có hoạt động trước khi bị loại |
| `GUEST_TTL_HOURS` | `24` giờ |
| `TEMPORARY_CHAT_HOURS` | `24` giờ cho hội thoại có guest |
| `RETENTION_SCAN_MS` | `60000` ms giữa các lần dọn dữ liệu hết hạn |
| `STORAGE_PROVIDER` | `disabled` hoặc `s3` |
| `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET` | Endpoint, region (mặc định `auto`) và bucket private |
| `STORAGE_ACCESS_KEY_ID`, `STORAGE_SECRET_ACCESS_KEY` | Khóa truy cập kho lưu trữ |
| `STORAGE_ALLOW_HTTP` | `false`; storage mặc định yêu cầu HTTPS |
| `UPLOAD_MAX_BYTES` | `10485760` byte = 10 MiB; multipart request còn có giới hạn tổng 11 MB trong YAML |
| `AUDIO_MAX_SECONDS` | `120` giây ở backend; frontend hiện cũng giới hạn 120 giây |
| `FFPROBE_PATH` | `ffprobe`; có thể đặt đường dẫn đầy đủ tới executable |
| `STUN_URLS` | `stun:stun.l.google.com:19302`; nhiều URL phân cách dấu phẩy |
| `TURN_URLS`, `TURN_SHARED_SECRET` | TURN dùng shared-secret; có URL thì phải có secret tương ứng |

Để bật file/voice, đặt `STORAGE_PROVIDER=s3`, điền đủ endpoint, bucket, region và access key/secret; bảo đảm bucket private và tài khoản storage có quyền đọc/ghi/xóa object. Cài FFmpeg và kiểm tra `ffprobe -version` trước khi thử voice. Dockerfile đã cài FFmpeg trong runtime.

TURN phải được cấu hình tương thích shared-secret, ví dụ Coturn. Backend cấp credential tạm có hạn 600 giây; không dùng mật khẩu TURN tĩnh thay cho shared secret.

### Frontend

| Biến | Ý nghĩa |
| --- | --- |
| `VITE_API_BASE_URL` | REST base URL; fallback trong source là `http://localhost:8081` |
| `VITE_WS_URL` | WebSocket endpoint; fallback `ws://localhost:8081/ws` |
| `VITE_ICE_SERVERS` | Mảng JSON ghi đè ICE servers do backend cấp |
| `VITE_ICE_CONFIG_URL` | Endpoint trả `{ "iceServers": [...] }`; có độ ưu tiên cao nhất, gọi kèm credentials |

Ưu tiên ICE: `VITE_ICE_CONFIG_URL` → `VITE_ICE_SERVERS` → cấu hình backend → STUN fallback. Thông thường để hai biến ICE không được đặt để dùng cấu hình backend.

Các biến `VITE_*` được đưa vào bundle công khai lúc build. Không đặt JWT secret, DB password, khóa S3 hoặc TURN shared secret vào frontend. `.env`, truststore và khóa riêng đã được khai báo ignore trong Git.

## Hướng dẫn sử dụng và demo

1. Chạy backend và frontend; xác nhận health báo `UP`.
2. Mở hai trình duyệt hoặc hai hồ sơ trình duyệt độc lập để có hai danh tính. Hai tab cùng hồ sơ có thể dùng chung refresh cookie.
3. Chọn guest hoặc đăng ký/đăng nhập. Muốn thử lịch sử và bạn bè, dùng hai tài khoản khác nhau.
4. Cho phép camera/mic, bấm bắt đầu ở cả hai phía, chờ được ghép rồi thử video, audio và tin nhắn văn bản.
5. Thử bật/tắt mic/camera, Next và Stop. Dùng ít nhất ba phiên nếu muốn kiểm tra đổi sang người khác: hai phiên vừa Next có thể tiếp tục chờ do cơ chế tránh ghép lại.
6. Khi đã cấu hình storage, thử gửi ảnh/tài liệu và ghi âm → dừng → nghe lại → gửi hoặc hủy.
7. Với hai tài khoản, thử lời mời kết bạn trong phòng hoặc tìm ở trang Bạn bè; mở chat riêng và gọi trực tiếp khi người nhận đang online, không ở hàng chờ/phòng/cuộc gọi khác.
8. Kết thúc cuộc trò chuyện giữa hai tài khoản, mở Lịch sử để xem lại và thử ẩn ở một phía.

Camera/microphone cần secure context: dùng `localhost` khi phát triển hoặc HTTPS khi truy cập từ thiết bị khác. Mở bằng IP LAN qua HTTP có thể không xin được quyền media. Dùng tai nghe khi demo nhiều phiên trên cùng máy để tránh vọng âm.

## API và WebSocket

### REST API chính

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| GET | `/api/health` | Kiểm tra dịch vụ và kết nối DB |
| GET | `/api/auth/csrf` | Lấy CSRF token và tên header |
| POST | `/api/auth/guest` | Tạo định danh khách |
| POST | `/api/auth/register` | Đăng ký |
| POST | `/api/auth/login` | Đăng nhập |
| POST | `/api/auth/refresh` | Làm mới access token, xoay refresh token |
| POST | `/api/auth/logout` | Đăng xuất, thu hồi phiên |
| GET / PATCH | `/api/users/me` | Đọc / cập nhật hồ sơ |
| POST | `/api/socket-ticket` | Lấy ticket mở WebSocket |
| GET | `/api/conversations` | Lịch sử, tham số `page`, `size` |
| GET | `/api/conversations/{id}` | Chi tiết hội thoại |
| GET | `/api/conversations/{id}/messages` | Tin nhắn phân trang |
| DELETE | `/api/conversations/{id}` | Ẩn hội thoại đã kết thúc ở phía người gọi |
| POST | `/api/conversations/{id}/attachments` | Upload multipart: `file`, `clientMessageId`, `voice` |
| GET | `/api/attachments/{id}/download-url` | Lấy URL tải file có thời hạn |
| GET | `/api/social/users?q=...` | Tìm tài khoản (2–80 ký tự, tối đa 20 kết quả) |
| GET | `/api/social` | Bạn bè, lời mời và danh sách chat riêng |
| POST / PATCH / DELETE | `/api/social/friends/{userId}` | Gửi / phản hồi / xóa quan hệ bạn bè; PATCH nhận `{ "accept": true/false }` |
| POST | `/api/social/direct/{userId}` | Tạo hoặc mở lại hội thoại riêng |
| GET / POST | `/api/social/direct/{id}/messages` | Đọc / gửi tin riêng; GET hỗ trợ cursor `before`, `after` |

Các API nghiệp vụ yêu cầu định danh và kiểm tra quyền. Tính năng social/hồ sơ/lịch sử yêu cầu tài khoản. Frontend tự gắn thông tin xác thực và CSRF qua [api.js](source/client/src/services/api.js).

### WebSocket

Endpoint frontend sử dụng: `/ws?ticket=<ticket>`. Thông điệp JSON có dạng:

```json
{
  "type": "CHAT_MESSAGE",
  "requestId": "request-id",
  "roomId": "room-id",
  "payload": {
    "text": "Xin chào!",
    "clientMessageId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

Các nhóm sự kiện chính:

- Phiên/ghép đôi: `SESSION_READY`, `FIND_PARTNER`, `WAITING`, `MATCH_FOUND`, `NEXT_PARTNER`, `LEAVE_ROOM`, `ROOM_CLOSED`, `PARTNER_LEFT`.
- Tin nhắn: `CHAT_MESSAGE`, `CHAT_FILE`, `CHAT_AUDIO`.
- Signaling phòng ngẫu nhiên: `WEBRTC_OFFER`, `WEBRTC_ANSWER`, `ICE_CANDIDATE`.
- Gọi trực tiếp: `CALL_REGISTER`, `CALL_INVITE`, `CALL_INCOMING`, `CALL_RINGING`, `CALL_ACCEPT`, `CALL_ACCEPTED`, `CALL_DECLINE`, `CALL_END`, `CALL_ENDED`, `CALL_OFFER`, `CALL_ANSWER`, `CALL_ICE`, `CALL_ERROR`.

Chi tiết xử lý nằm trong [MatchmakingService.java](source/server/src/main/java/com/randomconnect/service/MatchmakingService.java), [DirectCalls.java](source/server/src/main/java/com/randomconnect/service/DirectCalls.java) và [socketClient.js](source/client/src/services/socketClient.js).

## Cơ sở dữ liệu

Schema được quản lý bằng hai migration trong [db/migration](source/server/src/main/resources/db/migration):

| Bảng | Dữ liệu |
| --- | --- |
| `users` | Tài khoản, mật khẩu băm, hồ sơ |
| `guest_sessions` | Định danh khách, hash token, hạn dùng |
| `auth_sessions` | Phiên đăng nhập, hash refresh token, trạng thái thu hồi |
| `conversations` | Loại hội thoại, trạng thái, chính sách lưu và hạn dùng |
| `conversation_participants` | Thành viên, thời điểm tham gia/rời/ẩn |
| `messages` | Nội dung, loại tin và `client_message_id` chống ghi trùng khi gửi lại |
| `message_attachments` | Metadata file/audio, storage key và thời lượng |
| `social_connections` | Quan hệ bạn bè và hội thoại riêng của một cặp tài khoản |

`V1` tạo các bảng tài khoản/hội thoại/tin nhắn; `V2` thêm quan hệ xã hội. Binary file không lưu trong DB. Tác vụ retention dọn hội thoại tạm hết hạn, hội thoại đã bị tất cả thành viên ẩn và phiên hết hạn. Khi backend khởi động lại, phòng ngẫu nhiên đang hoạt động được đánh dấu kết thúc; hội thoại `DIRECT` được giữ lại.

## Kiểm thử và build

### Backend

Tại `source/server`:

```powershell
.\mvnw.cmd verify
```

Các test tích hợp dùng profile test với H2 ở chế độ MySQL; các test upload sử dụng storage giả lập. Bộ test bao gồm auth/CSRF, history, social, matchmaking, WebSocket, gọi trực tiếp, ICE, upload và cấu hình Render.

Build tạo executable JAR:

```text
source/server/target/randomconnect-0.0.1-SNAPSHOT.jar
```

Chạy JAR từ `source/server` với `.env` đã cấu hình:

```powershell
java -jar target/randomconnect-0.0.1-SNAPSHOT.jar
```

### Frontend

Tại `source/client`:

```powershell
npm test
npm run build
npm run preview
```

`npm test` chạy Vitest một lần. Build tạo `source/client/dist`; preview dùng để xem bản build trên máy. Các test component, store và service sử dụng jsdom hoặc giả lập WebSocket/WebRTC/media, cần bổ sung kiểm tra bằng trình duyệt và thiết bị thật cho video/audio xuyên mạng.

Các lệnh trên là hướng dẫn tái chạy, không phải tuyên bố bộ test đã được chạy trong lần cập nhật README này.

## Docker và triển khai

### Backend Docker

Từ thư mục gốc:

```powershell
docker build -t randomconnect-backend ./source/server
```

Dockerfile build bằng Java 21, chạy `verify`, cài FFmpeg trong runtime và chạy bằng user không phải root. Image mặc định kích hoạt profile `render`.

Ví dụ chạy local, dùng cấu hình DB/JWT trong `.env` đã chuẩn bị:

```powershell
docker run --rm --name randomconnect-backend --env-file source/server/.env -e PORT=10000 -e SPRING_PROFILES_ACTIVE=default -p 8081:10000 randomconnect-backend
```

Ví dụ trên dùng profile mặc định cho HTTP local để giữ cookie `Secure=false`, `SameSite=Lax`. Khi chạy container, đường dẫn truststore trên Windows host không tự tồn tại trong container; cần mount file và đặt URL phù hợp hoặc dùng cơ chế file Base64 của entrypoint. DB chạy trên host cũng cần hostname có thể truy cập từ container và khớp chứng chỉ TLS.

### Backend trên Render

Cấu hình theo các file Docker hiện có:

| Thiết lập | Giá trị |
| --- | --- |
| Runtime | Docker |
| Root Directory | `source/server` |
| Dockerfile Path | `./Dockerfile` |
| Docker Build Context | `.` |
| Health Check Path | `/api/health` |
| Số instance | `1` |

Đặt DB/TLS, `JWT_SECRET`, `ALLOWED_ORIGINS` và các biến storage/TURN cần dùng trên môi trường triển khai. Giữ profile `render`, để nền tảng cấp `PORT`. Origin phải là địa chỉ frontend cụ thể, không wildcard, không path hoặc dấu `/` cuối.

### Frontend trên Vercel

- Root Directory: `source/client`.
- Install Command: `npm ci`.
- Build Command: `npm run build`.
- Output Directory: `dist`.
- Đặt `VITE_API_BASE_URL=https://<backend-domain>` và `VITE_WS_URL=wss://<backend-domain>/ws` trước khi build.
- [vercel.json](source/client/vercel.json) đã cấu hình rewrite về `index.html` cho Vue Router.

Build/deploy lại frontend sau khi đổi `VITE_*`; thêm đúng domain frontend vào allowlist backend. Hai domain khác site có thể bị trình duyệt chặn refresh cookie bên thứ ba; khi triển khai cần kiểm tra thực tế, ưu tiên frontend/backend dùng các subdomain cùng site. Các mục này mô tả cấu hình trong repository, không xác nhận dịch vụ public đang hoạt động.

## Xử lý lỗi thường gặp

| Hiện tượng | Kiểm tra / cách xử lý |
| --- | --- |
| Backend không khởi động | Kiểm tra Java 21, `DB_PASSWORD`, độ dài `JWT_SECRET`, DB và lỗi đầu tiên trong log |
| Lỗi TLS/hostname MySQL | Dùng hostname đúng với chứng chỉ, `VERIFY_IDENTITY`, CA/truststore phù hợp và quyền đọc file |
| Health trả 503 | Backend không xác thực được kết nối DB; kiểm tra mạng, thông tin đăng nhập và giới hạn kết nối |
| Local vẫn gọi Render | Sửa `.env.local` client; kiểm tra biến môi trường `VITE_*` trong terminal và restart Vite |
| CORS hoặc WebSocket bị từ chối | Kiểm tra `ALLOWED_ORIGINS` khớp cả scheme, hostname và port của trang đang mở |
| Không mở được camera/mic | Cấp lại quyền trong trình duyệt; dùng localhost/HTTPS; kiểm tra thiết bị đang bị ứng dụng khác chiếm |
| Ghép được nhưng không có video/audio | Kiểm tra track, quyền media và ICE; cần TURN khi hai phía không kết nối P2P được |
| Next rồi chờ mãi khi chỉ có hai người | Hai đối tác vừa bị bỏ qua được tránh ghép lại; dùng phiên thứ ba để demo |
| Upload/download báo 503 | Kiểm tra provider `s3`, endpoint, bucket và quyền storage; `disabled` không hỗ trợ file/voice |
| Không gửi được tin nhắn thoại | Kiểm tra storage, `ffprobe`, định dạng audio và giới hạn 120 giây/10 MiB |
| Cuộc gọi riêng báo bận/offline | Người nhận cần mở ứng dụng, có phiên gọi sẵn sàng và không tham gia ghép đôi/cuộc gọi khác |
| Đăng nhập không duy trì trên production | Kiểm tra HTTPS, cookie Secure/SameSite và chính sách cookie bên thứ ba của trình duyệt |


