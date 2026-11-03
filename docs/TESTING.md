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
