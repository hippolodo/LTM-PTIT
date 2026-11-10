# Triển khai bản nâng cấp

Code đã chuẩn bị tại workspace, **chưa commit/push/deploy bởi agent**. Ngày 2026-11-07 đã kiểm tra TLS/đăng nhập Aiven thành công với CA người dùng cung cấp; log khởi động local do người dùng gửi xác nhận Flyway áp dụng V1 thành công trên MySQL8.4 và backend chạy8081. File source/server/.env và truststore trong source/server/secrets đã được Git ignore. Không đưa frontend mới lên trước backend có `/api/auth/*`. Object Storage và Docker runtime vẫn chưa kiểm chứng; kiểm thử tự động dùng H2 MySQL mode + storage giả lập.

**Trước khi push nếu repository đã bật Auto Deploy:** cấu hình DB_PASSWORD, JWT_SECRET, ALLOWED_ORIGINS và truststore trên Render trước; Git không đưa cấu hình riêng trên máy lên server. Đặt STORAGE_PROVIDER=disabled nếu chưa có bucket (file/voice sẽ503). Push GitHub có thể kích hoạt Render/Vercel tự triển khai theo cài đặt của bạn; tài liệu này không xác nhận cấu hình dashboard đã hoàn tất.

## Dùng kho chứng chỉ Java, không cấu hình CA riêng

Mặc định DB_TRUSTSTORE_URL để trống và fallbackToSystemTrustStore=true: Connector/J dùng kho chứng chỉ tin cậy của Java. Giữ DB_SSL_MODE=VERIFY_IDENTITY. Không cần đặt DB_TRUSTSTORE_PASSWORD/TYPE/BASE64_PATH nếu không dùng truststore riêng. Nếu Render đã đặt DB_TRUSTSTORE_URL hoặc DB_TRUSTSTORE_BASE64_PATH cũ, bỏ các biến đó rồi khởi động lại. Cách này chỉ kết nối được nếu Java tin chứng chỉ server; nếu lỗi PKIX thì phải bổ sung CA theo phần bên dưới, không bỏ xác minh TLS.

Local: copy source/server/.env.example sang source/server/.env rồi điền DB_PASSWORD và JWT_SECRET. File .env đã được Git ignore. Backend đọc file này khi chạy từ root dự án hoặc source/server; .env.example chỉ là mẫu và không được nạp. Java properties dùng ký tự backslash để escape; nếu secret có backslash hãy dùng biến môi trường thay cho file properties.

Nguồn: [Connector/J fallbackToSystemTrustStore](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html).

## 1. Aiven và TLS (truststore riêng nếu cần)

Hostname user xác nhận: `mysql-13b3d5c1-viethoan04082005-0cdd.b.aivencloud.com`, port 22489, DB defaultdb, user avnadmin. Đối chiếu lại Console nếu service thay đổi. Password chỉ đặt trong environment/secret manager.

Tải CA certificate từ Aiven Console, tạo truststore cục bộ bằng JDK 21. PowerShell từ root dự án (CA đặt trong thư mục riêng không commit):

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$secret=Read-Host 'Mật khẩu truststore mới' -AsSecureString
$env:DB_TRUSTSTORE_PASSWORD=[System.Net.NetworkCredential]::new('', $secret).Password
& "$env:JAVA_HOME\bin\keytool.exe" -importcert -alias aiven-ca -file .\secrets\ca.pem -keystore .\secrets\aiven-truststore.p12 -storetype PKCS12 -storepass:env DB_TRUSTSTORE_PASSWORD
```

Kiểm tra fingerprint CA bằng Console trước khi chấp nhận import. `secrets/`, *.p12 và *.pem đã được ignore. Không dùng trustAll/disable SSL. `DB_SSL_MODE=VERIFY_IDENTITY` xác minh cả chain CA và hostname; REQUIRED chỉ mã hóa nên không đáp ứng yêu cầu xác thực chứng chỉ của task. Dùng hostname thật, không thay bằng IP. Java có thể dùng truststore hệ thống nếu CA phù hợp, nhưng hướng dẫn này dùng CA Aiven rõ ràng.

Nguồn: [Aiven Java connection](https://aiven.io/docs/products/mysql/howto/connect-with-java), [Connector/J server authentication](https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html), [Connector/J security properties](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html).

## 2. Render backend

Web Service hiện có `https://ltm-ptit.onrender.com`, runtime Docker. Root Directory `source/server`, Dockerfile Path `./Dockerfile`, Build Context `.`. Image tự chạy profile render và bind 0.0.0.0:${PORT:10000}; Render cấp PORT. Health path `/api/health`. Chỉ chạy một instance.

| Biến Render | Giá trị |
|---|---|
| DB_HOST | hostname Aiven ở trên |
| DB_PORT / DB_NAME / DB_USERNAME | 22489 / defaultdb / avnadmin |
| DB_PASSWORD | secret Aiven |
| DB_SSL_MODE | VERIFY_IDENTITY |
| DB_POOL_SIZE | 3 (minimum idle 1) |
| DB_TRUSTSTORE_URL | file:/etc/secrets/aiven-truststore.p12 |
| DB_TRUSTSTORE_PASSWORD / DB_TRUSTSTORE_TYPE | secret đã đặt / PKCS12 |
| JWT_SECRET | chuỗi ngẫu nhiên ít nhất 32 byte, chỉ backend |
| ACCESS_TOKEN_SECONDS | 600 |
| ALLOWED_ORIGINS | https://ltm-ptit.vercel.app,http://localhost:5173 |
| GUEST_TTL_HOURS / TEMPORARY_CHAT_HOURS | 24 / 24 |
| STORAGE_PROVIDER | disabled tạm hoặc s3 sau khi cấu hình bucket private |
| STORAGE_ENDPOINT / REGION / BUCKET / ACCESS_KEY_ID / SECRET_ACCESS_KEY | dùng đầy đủ tên STORAGE_ từ .env.example và tài liệu file/audio |
| UPLOAD_MAX_BYTES / AUDIO_MAX_SECONDS | 10485760 / 120 |
| STUN_URLS / TURN_URLS / TURN_SHARED_SECRET | giữ cấu hình TURN thực tế; thiếu TURN nhiều mạng không gọi được |

Render secret file nhận text: tạo base64 PKCS12 trên máy local, không commit:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path (Get-Location) 'secrets/aiven-truststore.p12'))) | Set-Content -NoNewline secrets/aiven-truststore.p12.b64
```

Trong Render tạo Secret File tên `aiven-truststore.p12.b64`, dán nội dung file base64; đặt `DB_TRUSTSTORE_BASE64_PATH=/etc/secrets/aiven-truststore.p12.b64`. Docker entrypoint tự giải mã vào thư mục temporary chỉ user app đọc và tự đặt DB_TRUSTSTORE_URL. DB_TRUSTSTORE_PASSWORD vẫn phải đúng. Không đặt base64 vào Git/Dockerfile: nó vẫn là vật liệu cấu hình bí mật. Nếu nền tảng mount binary trực tiếp, bỏ DB_TRUSTSTORE_BASE64_PATH và dùng DB_TRUSTSTORE_URL như bảng trên. Script chỉ decode khi biến được đặt; local Maven không chạy script này.

JWT secret ví dụ tạo cục bộ (không gửi lại trong chat):

```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
```

Trước migration: backup DB, xác nhận bảng hiện có, kiểm thử V1 trên staging. Flyway không tự baseline database không rỗng; nếu đã có bảng, dừng để kiểm tra và lập migration phù hợp. Không set ddl-auto=create/create-drop, không tự enable Flyway clean. Khởi động sẽ migrate V1 trên DB chưa có schema, nên chỉ deploy khi đã review. Rollback code không tự rollback schema; giữ backup và migration mới tương thích.

Docker build chạy Maven verify. Runtime non-root, có ffprobe; JVM heap 40%, metaspace 160MiB, code cache 32MiB, direct 16MiB, stack 512KiB; Tomcat max24, upload song song2, Hikari3. Chưa đo memory dưới tải thật Render Free, cần theo dõi RSS/OOM và giảm tải hoặc nâng gói. Restart/sleep đóng room RAM; dữ liệu account vẫn trong DB. Render không phải TURN server.

## 3. Vercel frontend

Root `source/client`, build `npm run build`, output `dist`. Chỉ dùng một biến WS:

```dotenv
VITE_API_BASE_URL=https://ltm-ptit.onrender.com
VITE_WS_URL=wss://ltm-ptit.onrender.com/ws
```

Không đặt DB/JWT/storage/TURN shared secret vào VITE_. Giữ VITE_ICE_CONFIG_URL trống để dùng ICE backend trừ khi có endpoint cấp credential ngắn hạn thật. Thay env phải redeploy Vercel vì Vite nhúng vào bundle. SPA rewrite trong source/client/vercel.json đã có, đưa /history và /profile về index.html khi tải trực tiếp. Xem [hướng dẫn Vite SPA của Vercel](https://vercel.com/docs/frameworks/frontend/vite). Kiểm tra cookie third-party trên browser: khác site Render/Vercel có thể bị chặn; dùng custom domain cùng site cho phiên đăng nhập ổn định. Profile render luôn cookie Secure/None.

## 4. Chạy local Windows

Backend tự đọc .env tại root hoặc source/server; biến môi trường có độ ưu tiên cao hơn. Từ root, sau khi có CA/truststore và mật khẩu:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:DB_HOST='mysql-13b3d5c1-viethoan04082005-0cdd.b.aivencloud.com'
$env:DB_PORT='22489'
$env:DB_NAME='defaultdb'
$env:DB_USERNAME='avnadmin'
$secret=Read-Host 'Aiven password' -AsSecureString
$env:DB_PASSWORD=[System.Net.NetworkCredential]::new('', $secret).Password
$env:DB_SSL_MODE='VERIFY_IDENTITY'
$env:DB_TRUSTSTORE_URL='file:/' + ((Join-Path (Get-Location) 'secrets/aiven-truststore.p12') -replace '\\','/')
# DB_TRUSTSTORE_PASSWORD lấy từ bước tạo truststore hoặc nhập lại Read-Host.
$env:JWT_SECRET=[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:ALLOWED_ORIGINS='http://localhost:5173,http://127.0.0.1:5173'
$env:STORAGE_PROVIDER='disabled'
cd source/server
.\mvnw.cmd spring-boot:run
```

Mỗi lần random JWT_SECRET mới sẽ làm JWT cũ mất hiệu lực; production phải giữ secret ổn định. Không trỏ bản dev vào DB production nếu không muốn migration/cleanup ảnh hưởng dữ liệu thật: ưu tiên Aiven staging riêng. ffprobe cần cho voice; storage disabled trả lỗi cấu hình khi gửi file.

Terminal thứ hai, từ root, chọn backend local bằng environment (ghi đè .env.local đang trỏ Render):

```powershell
cd source/client
$env:VITE_API_BASE_URL='http://localhost:8081'
$env:VITE_WS_URL='ws://localhost:8081/ws'
npm ci
npm run dev
```

Nếu npm ci báo EPERM esbuild.exe: dừng Vite đang chạy và đóng tiến trình esbuild của dự án rồi chạy lại; không xóa/move thư mục dự án. Mở http://localhost:5173. Local backend vẫn cổng8081. Backend Render cũ chưa có API auth sẽ không chạy được frontend mới cho đến khi người dùng triển khai backend.

## 5. Kiểm chứng trước khi mở dùng

Chạy `source/server/mvnw.cmd -f source/server/pom.xml verify`; frontend `npm test`, `npm run build`. Tests dùng test profile H2, không cần secret production. Xem kết quả thực tế trong TESTING.md. Test Docker cần Docker Engine đang hoạt động.

Sau khi cấu hình dịch vụ ngoài và triển khai thủ công: health UP; MySQL log kết nối thành công mà không log password; login/refresh/logout trong browser thật; hai tài khoản chat rồi reload history; khách ghép với user không có history; upload/download private và người ngoài bị chặn; gửi/hủy/nghe voice khi video call vẫn chạy; Next/Stop; hai mạng khác nhau qua TURN; expiry/cleanup staging. Không chỉ dựa vào screenshot WebSocket OPEN để kết luận media thành công.
