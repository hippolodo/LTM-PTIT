# Sửa nhận diện ghi âm WebM — 2026-11-10

Đã tái hiện lỗi `Unsupported audio format` với WebM/Opus streaming do ffmpeg tạo (không có duration ở container): tika-core nhận diện container cha Matroska, validator cũ từ chối. Sau sửa, fallback chỉ áp dụng cho voice `.webm`, vẫn dùng ffprobe xác minh audio-only và thời lượng.

- Maven verify: **74 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS.
- BrowserVoiceTest: WebM/Opus streaming hợp lệ được chấp nhận; WebM có video bị từ chối. Hai test cần ffmpeg và ffprobe, có thể skip trong build stage không cài các executable này.
- UploadErrorResponseTest: HTTP 400 trả `INVALID_ATTACHMENT` và lỗi audio cụ thể, không gộp thành thông báo sai về kích thước.
- Chưa kiểm chứng lại trên Render hoặc với file microphone thật của người dùng. Cần push code mới và deploy lại backend để áp dụng.

---

# Kiểm thử file/ghi âm trong chat riêng — 2026-11-10

- Backend Maven verify: **71 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Thêm 5 tests DirectUploadIntegrationTest: multipart HTTP không cần socket, partner/outsider/guest ACL, retry khi chạm quota, xóa object bù nếu hội thoại đóng sau PUT, quota với hai upload đồng thời, voice WAV qua ffprobe và download cho partner.
- Frontend: **35 tests / 10 files passed**; Vite production build thành công. Thêm kiểm thử AttachmentComposer về endpoint DIRECT, retry ID/draft và abort/stale response khi đổi hội thoại; kiểm thử useDirectChat về upload xen kẽ polling không trùng/bỏ sót tin.
- Storage test là in-memory, database là H2; chưa xác minh upload/download R2 thật hoặc thao tác mic/player trong trình duyệt ở lượt này. Không thêm migration hay sửa cấu hình secret.
- Hướng dẫn: [File và ghi âm](FILE_AUDIO_SHARING.md), [Bạn bè và chat riêng](FRIENDS_AND_DIRECT_CHAT.md).

---

# Kiểm thử gọi video trực tiếp — 2026-11-09

- Backend Maven verify: **66 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Thêm 6 tests DirectCallsTest và 1 DirectCallSocketTest qua WebSocket TCP có ticket thật.
- Frontend: **32 tests / 9 files passed**; Vite production build thành công. Thêm 5 tests callStore cho consent, cancel/late permission, early ICE/stale call, offline/busy/timeout/disconnect, hủy ngay sau khi gửi accept.
- Các test xác nhận signaling và lifecycle, không xác nhận hình/tiếng thực tế trên hai thiết bị hoặc TURN khác mạng. Chưa chạy Docker runtime hay deploy cloud trong lượt này.
- Không thêm migration cho video call. Xem [hướng dẫn gọi trực tiếp](FRIENDS_AND_DIRECT_CHAT.md).

---

# Kiểm thử bạn bè và chat riêng — 2026-11-09

Lượt kiểm tra sau khi thêm tìm tài khoản, lời mời kết bạn và chat riêng:

- Backend Maven verify: **59 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Gồm 6 tests mới trong SocialIntegrationTest; hai test ffprobe chạy đạt khi thực thi ngoài sandbox.
- Frontend: **27 tests / 8 files passed**, gồm 4 tests useDirectChat về retry ID/draft, stale response, polling xen kẽ POST và cursor tải tin cũ.
- Vite production build thành công. Migration V1 + V2 chạy trong H2 test profile; không chạy migration vào MySQL production.
- Chưa kiểm chứng giao diện trên hai trình duyệt thật hoặc migration V2 trên MySQL thật. Hướng dẫn và giới hạn: [Bạn bè và chat riêng](FRIENDS_AND_DIRECT_CHAT.md).

---

# Kiểm thử bản nâng cấp — 2026-11-07

Kết quả thực tế ở workspace Windows, JDK21. Backend dùng test profile H2 MySQL mode; Object Storage fake chỉ trong test. Test tự động không kết nối hoặc migration Aiven. Sau đó đã kiểm tra SELECT1 qua TLS với Aiven thật; log người dùng gửi xác nhận startup local áp dụng V1 thành công. Lượt verify cuối BUILD SUCCESS, **53 tests, 0 failures, 0 errors, 0 skipped**. Frontend **23 tests / 7 files passed**, production build thành công; npm audit **0 vulnerabilities** tại thời điểm chạy. `git diff --check` và kiểm tra cú pháp shell entrypoint đều đạt.

| Hạng mục | Kiểm chứng thực tế |
|---|---|
| AuthIntegrationTest | 6 tests: register, đúng/sai password, duplicate username/email, hash và DTO không lộ secret, refresh rotation/reuse, logout/session revocation, JWT hết hạn, guest ACL |
| CookieCsrfIntegrationTest | 1 test qua HTTP TCP với cookie jar thật: GET csrf đặt HttpOnly cookie, POST thiếu header403, có cookie/header200. Tách context khỏi MockMvc csrf() vì postprocessor thay repository trong test |
| HistoryIntegrationTest | 4 tests: user-user persistence, dedupe/paging/IDOR/receipt/hide, guest/mixed TTL, one-use ticket, constraint participant XOR |
| UploadIntegrationTest | 5 tests: upload/dedupe, download member/outsider, Next trong PUT dọn object, TTL cleanup, quota và upload outsider, voice WAV với ffprobe thật và download partner |
| UploadSecurityTest | 5 tests: UTF8 TXT/name traversal, fake MIME/oversize, executable, voice quá dài, ffprobe WAV duration thật |
| Hồi quy hiện có | 32 tests: matching/concurrency, signaling/room lifecycle, TCP WebSocket, origins, limits/timeout, ICE/TURN, health, Render PORT/profile |
| Frontend | 23 tests: 16 hồi quy +4 voice MIME/cleanup/late permission/limit/denied +3 auth restore/login/logout network failure |
| Build | JAR Spring Boot và Vite dist tạo thành công |
| Dependency frontend | npm audit:0 vulnerabilities; không phải chứng nhận audit bảo mật toàn hệ thống |
| Docker | CLI có sẵn nhưng pipe dockerDesktopLinuxEngine không tồn tại: Engine chưa chạy. Chưa build/run image. Entrypoint `bash -n` đạt; .dockerignore đã bao gồm script |
| Aiven/MySQL thật | SELECT1 với TLS VERIFY_IDENTITY đạt; log startup người dùng xác nhận V1 áp dụng thành công trên MySQL8.4. Test hồi quy vẫn dùng H2 |
| S3/R2 thật | Chưa kiểm chứng vì thiếu bucket/credential; storage test nằm RAM |
| Media browser/thiết bị thật | Chưa xác minh camera, mic, audio codec/player, WebRTC + file/voice cùng lúc, cookie third-party và hai mạng khác nhau |

Lệnh từ root: `source/server/mvnw.cmd -f source/server/pom.xml verify`. Trên máy workspace dùng thêm MAVEN_USER_HOME và maven.repo.local trong .tools để dùng cache được phép; không cần commit cache. Frontend trong source/client: `npm test`, `npm run build`, `npm audit`. Test ffprobe dùng assumption khi executable không có, vì vậy Docker build stage có thể skip hai test probe; lượt Windows bàn giao không skip test nào. Runtime Docker đã cài ffprobe nhưng cần kiểm tra image thực tế.

## Checklist staging và browser còn phải chạy

- Aiven: backup/schema review, VERIFY_IDENTITY với CA thật, migration V1 trên staging, restart không chạy lại V1; thử CA/hostname sai phải thất bại; health503 khi mất DB.
- Browser: guest vào ngay; hai tài khoản register/login/logout/refresh/reload; thử trình duyệt chặn cookie third-party và cấu hình custom domain cùng site.
- Ghép User/User, User/Guest, Guest/Guest: text hai chiều, privacy banner đúng, không lẫn history sau Next; user reload còn history, guest không xuất hiện trong account history và không chuyển lịch sử sau register.
- Bucket private: gửi ảnh/PDF/TXT/DOCX thật, preview/download, retry cùng ID, người ngoài404, URL hết hạn sau60 giây, object cleanup TTL và lifecycle dự phòng.
- Voice: record/stop/listen/send/receive/cancel trên Chrome/Safari/Firefox hỗ trợ thực tế, denied/unsupported, quá120 giây; không mất mic cuộc gọi; playback/seek sau URL hết hạn và khác codec.
- WebRTC: video/audio hai thiết bị, text/file/voice trong lúc gọi, toggle mic/camera, Next/Stop/rời trang dọn tài nguyên, reconnect không double socket; hai mạng qua TURN.
- Render: memory RSS/heap khi có upload đồng thời và nhiều WS, cold start/sleep/restart đóng phòng đúng; không scale nhiều replica; không log secret/chat/SDP.

Không đánh đồng fake PeerConnection hoặc TCP signaling test với video/audio E2E thành công. Không có kết quả upload production hoặc load test production. Migration Aiven được xác nhận theo log startup người dùng, không phải một lượt migration mới do agent chạy.

---

## Phụ lục kết quả MVP trước nâng cấp (lịch sử)

# Kiểm thử RandomConnect

Ngày thực hiện: **2026-11-03**, múi giờ Asia/Saigon. JDK 21, Node 22.20.0, npm 10.9.3. Tests tự động và live smoke đã chạy thật; kiểm thử camera/audio trên browser chưa hoàn tất.

## Kết quả thực tế

| Hạng mục | Kết quả |
|---|---|
| Maven Wrapper + `mvnw.cmd test` / `package` | **26 tests**, 0 lỗi, 0 skipped; BUILD SUCCESS; JAR đã tạo |
| Matchmaking / room / relay / concurrency | 20 tests, gồm 80 client đồng thời và simultaneous Next |
| REST health | 1 integration test, HTTP 200 / JSON hợp lệ |
| WebSocket TCP qua Java HttpClient | 3 integration tests: lifecycle và signaling, malformed/oversized, origin bị từ chối |
| TURN credentials | 2 tests: chữ ký HMAC, hạn 600 giây, thiếu secret bị từ chối |
| `npm test` | **16 tests / 5 files passed** |
| `npm run build` | Vite production build thành công |
| `npm audit` | **0 vulnerabilities** tại thời điểm kiểm tra |
| `npm run dev` cổng 5173 | Khởi động được; HTTP 200 chứa trang RandomConnect |
| Lần thử ban đầu với 8080 (trước khi đổi mặc định) | **Bị chặn bởi PID 4368 đang dùng cổng**, không dừng tiến trình đó |
| Backend với `PORT=8081` | Maven `spring-boot:run` thành công; `/api/health` HTTP 200 |
| Live smoke 8081 | Hai native WebSocket: SESSION_READY, A WAITING, cùng room, CHAT_MESSAGE, Next: ROOM_CLOSED + WAITING, đối tác PARTNER_LEFT |
| Sơ đồ PNG | Đã tạo và mở kiểm tra trực quan, 1400×760 |
| Browser UI / camera / microphone / media E2E | **Chưa xác minh**; Computer Use dừng vì không xác định URL Chrome đủ chắc chắn |
| Hai thiết bị / khác mạng / Coturn | **Chưa xác minh**; cần hạ tầng thật và người dùng thử thủ công |

Các test PeerConnection dùng fake media/peer; kiểm thử TCP chỉ xác nhận signaling được relay, không phát video/audio thật. Không đánh đồng các kết quả này với video E2E passed.

## Lệnh tái chạy

Từ `source/server`, đặt JAVA_HOME tới JDK 21:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Từ `source/client`:

```powershell
npm ci
npm test
npm run build
npm audit
```

Trong sandbox phiên này, Maven cache/repository được đặt dưới `.tools/` trong workspace; đây chỉ là vị trí cache kiểm thử, không phải dependency của source. Build Vite cần chạy ngoài sandbox vì esbuild bị chặn đọc thư mục cha. Dependency được tải từ npm registry và Maven Central. Chạy trên máy người dùng không cần các tham số cache đặc biệt này.

## Manual E2E bắt buộc còn lại

1. Đọc README, chạy backend và frontend. Backend mặc định dùng 8081; REST/WebSocket frontend cũng mặc định trỏ tới 8081.
2. Mở Chrome A/B ở hai profile/tab độc lập. Nhấn bật camera & microphone, cấp quyền thủ công. Xác nhận local preview và chỉ báo thiết bị của browser.
3. A Start → WAITING; B Start → cùng roomId, một initiator. Kiểm tra remote video thật, audio thật. Dùng tai nghe để tránh vọng.
4. Gửi chữ hai chiều, mỗi tin đúng một lần; thử chuỗi `<img src=x onerror=alert(1)>` phải hiển thị nguyên văn.
5. Tắt/bật mic và camera: bên kia phải thấy/nghe thay đổi; không tự bật lại khi Next.
6. A Next → B PARTNER_LEFT và A WAITING; mở C → A/C ghép; B không tự vào queue.
7. Stop → Idle và thiết bị được giải phóng; không tự ghép. Đóng tab C → A được thông báo. Lặp simultaneous Next, refresh, server restart, mất mạng.
8. Thử từ chối quyền, không có thiết bị, Stop khi hộp xin quyền đang chờ. Cấp quyền đến muộn sau Stop không được để camera chạy ngầm. Text-only chỉ bắt đầu sau lựa chọn rõ ràng của người dùng.
9. Thử chặn autoplay: có nút phát âm thanh/video. Thử ICE fail: báo lỗi, đóng peer, vẫn cho Next/Stop.
10. Thử mobile width, đổi route/rời trang, tab đóng và các chỉ báo thiết bị thật sự tắt khi kết thúc.
11. Thiết lập HTTPS/WSS và Coturn theo DEPLOYMENT.md, thử hai mạng khác nhau. Ghi nhận candidate pair relay và chất lượng nghe/nhìn; không báo passed nếu mới chạy STUN-only hoặc unit tests.

## Checklist nghiệm thu

- [x] Source client/server, wrapper, env examples và tài liệu.
- [x] Build, unit/integration, live WebSocket chat, bảo vệ membership/origin và cleanup logic.
- [x] Mã WebRTC thật + signaling + queue ICE + track toggles + cleanup.
- [x] Dùng cổng mặc định **8081** theo yêu cầu cập nhật; live health/chat đã kiểm tra trên 8081.
- [ ] Người dùng xác minh camera/microphone/remote media qua hai browser/thiết bị.
- [ ] HTTPS/TURN qua hai mạng, ảnh chụp màn hình thật.
- [ ] Nhóm điền họ tên/MSSV/email và đối chiếu mẫu README/INSTRUCTION giảng viên chưa có.
