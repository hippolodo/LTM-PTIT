# Bạn bè và chat riêng

## Cách dùng

1. Chạy lại backend để Flyway áp dụng **V2__social_connections.sql**. Không sửa hoặc chạy lại V1 bằng tay.
2. Đăng nhập hai tài khoản ở hai trình duyệt hoặc một cửa sổ thường và một cửa sổ ẩn danh.
3. Mở **Bạn bè & chat riêng** trên thanh điều hướng (`/friends`).
4. Tìm theo username hoặc tên hiển thị, ít nhất 2 ký tự. Kết quả tối đa 20 người, không gồm chính mình.
5. Chọn **Kết bạn**. Bên nhận mở cùng trang, chọn **Chấp nhận** hoặc **Từ chối**. Người gửi có thể hủy lời mời; hai bên có thể hủy kết bạn.
6. Chọn **Nhắn tin** từ kết quả tìm kiếm, danh sách bạn hoặc cuộc trò chuyện riêng. Không bắt buộc kết bạn trước khi nhắn tin.
7. Khi đang ghép ngẫu nhiên giữa hai tài khoản, có thêm nút **Kết bạn với người này**. Nút này không đóng phòng.
8. Mở chat riêng và chọn **Gọi video**. Người nhận phải đang mở ứng dụng và đăng nhập; thông báo cuộc gọi đến xuất hiện trên mọi trang. Chọn **Chấp nhận** để mở camera/mic hoặc **Từ chối**.
9. Trong cuộc gọi có nút bật/tắt mic, camera và kết thúc. Điều hướng giữa các trang không làm mất cuộc gọi riêng; reload/đóng tab/logout/mất kết nối sẽ kết thúc cuộc gọi.
10. Dưới khung tin nhắn riêng có **Đính kèm** để chọn/gửi file và **Ghi tin nhắn thoại** để ghi, nghe lại, gửi hoặc hủy. Người nhận xem được khi mở lại hội thoại, kể cả lúc gửi họ chưa online.

## Chính sách

- Guest không được tìm tài khoản, kết bạn hoặc dùng chat riêng.
- Chat riêng hỗ trợ **tin nhắn chữ, file, ghi âm và gọi video/audio WebRTC**. Tin nhắn và metadata attachment lưu DB; binary file/ghi âm lưu private storage (R2/S3). Có thể gửi khi bên kia chưa mở trang. Khi đăng nhập lại, bên kia thấy thread trong danh sách cuộc trò chuyện riêng. Media cuộc gọi không được ghi/lưu vào lịch sử.
- Một cặp tài khoản dùng chung một hội thoại DIRECT; mở nhiều lần hoặc mở đồng thời từ hai phía không tạo phòng mới.
- Kết bạn là quan hệ cần sự đồng ý của bên nhận. Hai lời mời ngược chiều không tự chấp nhận.
- Hủy kết bạn không xóa lịch sử và không chặn nhắn tin. Chưa có tính năng chặn/báo cáo.
- Chat riêng được lưu cho cả hai, kể cả trước khi kết bạn. Chính sách lưu chat ngẫu nhiên hiện tại giữ nguyên.
- DIRECT luôn mở để nhắn tiếp, không bị startup recovery đóng như phòng RANDOM; chưa có thao tác ẩn/xóa DIRECT.
- Chat riêng cập nhật bằng REST polling khoảng 2 giây; danh sách bạn/lời mời cập nhật khoảng 5 giây. Không có online presence, push notification hoặc delivered/read receipt cho chat riêng.
- File và tin nhắn ghi âm dùng cùng API upload/download của phòng ngẫu nhiên, kiểm tra quyền thành viên DIRECT thay cho room socket. Giới hạn 10 MiB/file, ghi âm 120 giây và 50 MiB/người/hội thoại; quota DIRECT là tổng tích lũy trong hội thoại đó. Backend cần cấu hình private storage và ffprobe để gửi ghi âm. Chi tiết: [File và ghi âm](FILE_AUDIO_SHARING.md). Gọi video trực tiếp dùng signaling CALL_* riêng trên WebSocket, không tạo/đóng conversation DIRECT.
- Đi từ màn hình ghép ngẫu nhiên sang trang khác vẫn kết thúc phiên ghép theo hành vi hiện tại. Có thể gửi lời mời ngay trong phòng mà không điều hướng.

## API

Mọi endpoint yêu cầu access JWT của tài khoản; Guest trả 403. Search không trả email hoặc dữ liệu phiên.

| Method | Endpoint | Chức năng |
|---|---|---|
| GET | `/api/social/users?q=...` | Tìm username/displayName, 2–80 ký tự, tối đa 20 kết quả |
| GET | `/api/social` | Friends, incoming, outgoing, threads của người gọi |
| POST | `/api/social/friends/{userId}` | Gửi lời mời, idempotent nếu đã pending/friends |
| PATCH | `/api/social/friends/{userId}` | Bên nhận phản hồi `{ "accept": true/false }` |
| DELETE | `/api/social/friends/{userId}` | Hủy lời mời/từ chối/hủy kết bạn |
| POST | `/api/social/direct/{userId}` | Mở hoặc tạo thread, trả id và peer |
| GET | `/api/social/direct/{id}/messages` | 50 tin mới nhất, mới trước |
| GET | `/api/social/direct/{id}/messages?after={messageId}` | Tối đa 50 tin sau cursor, cũ trước |
| GET | `/api/social/direct/{id}/messages?before={messageId}` | Tối đa 50 tin trước cursor, mới trước |
| POST | `/api/social/direct/{id}/messages` | `{ "text": "...", "clientMessageId": "UUID" }`, 1–2000 ký tự |

Message cursor phải thuộc đúng hội thoại. Không gửi cả before và after. Response đọc tin gồm `items` và `hasMore`; hasMore khi đủ 50 phần tử là dấu hiệu có thể còn trang, trang kế tiếp có thể rỗng.

## Dữ liệu và đồng thời

V2 thêm `social_connections`: một hàng cho cặp user theo thứ tự UUID, trạng thái NONE/PENDING/ACCEPTED, requester_id và conversation_id tùy chọn. Có FK, unique pair, unique conversation, CHECK trạng thái và định danh requester. Không thay đổi V1.

Mutation khóa hàng tài khoản có UUID nhỏ hơn trước khi tạo/đổi pair để tuần tự hóa hai chiều. DIRECT dùng conversations/participants/messages hiện có; gửi text dùng transaction READ_COMMITTED và cùng khóa cặp để chống race khi retry. Quyền thành viên và loại DIRECT được kiểm tra trước khi đọc/gửi. Client giữ nội dung khi gửi lỗi, dùng lại clientMessageId khi thử lại cùng nội dung và loại phản hồi cũ sau khi chuyển hội thoại.

Frontend giữ cursor theo dữ liệu GET từ server, không nhảy cursor theo response gửi của chính mình; nhờ đó không bỏ qua tin đối tác đến xen giữa. Cursor sắp xếp bằng created_at + id để xử lý timestamp bằng nhau.

## Kiểm thử

Backend `SocialIntegrationTest` kiểm tra tìm kiếm/không lộ email, guest/anonymous ACL, lifecycle lời mời, quyền chấp nhận, direct không cần kết bạn, retry/dedupe, history/recovery, IDOR, room RANDOM bị từ chối, cursor và mở đồng thời.

Frontend `useDirectChat.test.js` kiểm tra giữ draft/retry ID, bỏ response cũ, dọn timer, không bỏ tin đối tác khi POST và polling xen kẽ, tải tin cũ/dedupe.

Tests dùng H2 ở test profile. Chưa xác minh migration V2 trên MySQL thật hoặc hai trình duyệt E2E trong phiên phát triển này.

## Gọi video trực tiếp

Không cần migration hoặc biến môi trường mới. Hai tài khoản có thể gọi từ hội thoại DIRECT, kể cả chưa kết bạn; người nhận phải chấp nhận mới gửi media. Cả hai cần HTTPS hoặc localhost, quyền camera/mic và mạng phù hợp. Qua hai mạng/NAT hạn chế cần cấu hình TURN hiện có; Render không phải TURN server.

App mở một socket đã xác thực riêng cho nhận cuộc gọi, đăng ký CALL_REGISTER sau SESSION_READY. Socket này dùng ticket một lần, heartbeat và cơ chế thu hồi phiên hiện có. Khi mất socket, cuộc gọi kết thúc và client thử kết nối lại sau 3 giây, không tự khôi phục cuộc gọi cũ. Guest không đăng ký gọi được. Một user chỉ có một cuộc gọi hoặc phòng/queue ngẫu nhiên tại một thời điểm, tính trên mọi tab/socket. Nếu mở nhiều tab, cuộc gọi đến được gửi đến tab đăng ký gần nhất còn kết nối.

| Client event | Ý nghĩa |
|---|---|
| CALL_REGISTER | Đăng ký socket nhận cuộc gọi, server trả CALL_READY |
| CALL_INVITE | roomId là UUID cuộc gọi, payload.conversationId là DIRECT đã tham gia |
| CALL_ACCEPT / CALL_DECLINE | Bên nhận chấp nhận hoặc từ chối khi đang đổ chuông |
| CALL_END | Một trong hai bên kết thúc hoặc hủy cuộc gọi |
| CALL_OFFER / CALL_ANSWER / CALL_ICE | SDP/ICE như signaling ngẫu nhiên, nhưng được kiểm tra quyền theo cuộc gọi |

Server gửi CALL_RINGING cho bên gọi, CALL_INCOMING cho bên nhận (peer public DTO + conversationId), CALL_ACCEPTED với initiator/iceServers cho hai bên, CALL_ENDED với reason hoặc CALL_ERROR với code. Chỉ socket được chọn mới có quyền chấp nhận; tab khác cùng tài khoản cũng không thể thay thế socket đó. Chỉ chuyển tiếp SDP/ICE sau khi chấp nhận, offer chỉ từ caller, answer chỉ từ callee. Không tin peer ID do client tự khai báo: tra peer từ thành viên DIRECT.

Đổ chuông tối đa 30 giây (job scan mỗi 5 giây); frontend có timeout dự phòng 35 giây. Busy/offline/decline/no-answer/disconnect đều được thông báo. Camera/mic chỉ mở khi caller chủ động gọi hoặc callee chủ động chấp nhận; cancel/timeout/hangup/signout giải phóng cả tracks đến muộn sau hộp thoại xin quyền. ICE từ server được cấp mới khi chấp nhận cuộc gọi; các VITE ICE overrides vẫn được hỗ trợ. Chưa tự refresh TURN trong cuộc gọi dài.

DirectCallsTest kiểm tra state machine, consent, role/IDOR/other-tab, busy, offline, timeout và disconnect. DirectCallSocketTest dùng hai WebSocket TCP có ticket thật để xác minh đổ chuông → accept → SDP/ICE → hangup và hội thoại vẫn nhắn được. callStore.test.js kiểm tra consent, permission đến muộn, ICE đến sớm, stale call, timeout, disconnect và cancel ngay sau khi gửi accept. Các test này không chứng minh video/audio thật qua hai thiết bị hoặc TURN đã chạy thành công.
