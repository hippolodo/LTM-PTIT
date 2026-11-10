# Hội thoại, quyền riêng tư và realtime

| Cặp ghép | Chính sách |
|---|---|
| User ↔ User | Lưu DB để hai tài khoản xem lại; giữ đến khi cả hai ẩn lịch sử |
| User ↔ Guest | Tạm, mặc định 24 giờ tính từ lúc ghép; không đưa vào lịch sử tài khoản |
| Guest ↔ Guest | Tạm, cùng TTL; không có lịch sử lâu dài |

UI thông báo trước và trong hội thoại; MATCH_FOUND kèm persistentHistory, expiresAt, retentionNotice. Không tự chuyển lịch sử guest sang user. TTL chỉ là quyền truy cập của ứng dụng; người nhận vẫn có thể chụp/lưu nội dung đã nhận. Xóa vật lý phụ thuộc DB/storage sẵn sàng. Khách hết token không thể khôi phục bằng ID tự khai báo.

| Method | Endpoint | Ghi chú |
|---|---|---|
| GET | /api/conversations?page=0&size=20 | Chỉ user, permanent đã tham gia và chưa ẩn |
| GET | /api/conversations/{id} | Thành viên, chưa hết hạn/chưa ẩn |
| GET | /api/conversations/{id}/messages?page=0&size=20 | Cùng ACL; mới nhất trước, UI đảo thành thứ tự thời gian |
| DELETE | /api/conversations/{id} | User ẩn riêng hội thoại permanent đã CLOSED; không ảnh hưởng quyền bên kia |

Không có quyền dựa vào việc đoán UUID. Conversation không tồn tại, người ngoài, đã ẩn hoặc hết hạn đều trả 404. Xóa khi ACTIVE trả 409 để tránh vừa gửi vừa ẩn. Khi cả hai ẩn, cleanup xóa dữ liệu/file; khi chỉ một bên ẩn, bên kia vẫn đọc được. Không phải chức năng xóa toàn bộ dữ liệu theo yêu cầu pháp lý.

Envelope native WebSocket giữ `{type,requestId?,roomId?,payload?}`. Các event cũ SESSION_READY/FIND_PARTNER/WAITING/MATCH_FOUND/NEXT_PARTNER/LEAVE_ROOM/PARTNER_LEFT/ROOM_CLOSED/PING/PONG/ERROR và WEBRTC_OFFER/WEBRTC_ANSWER/ICE_CANDIDATE được giữ.

| Event | Chiều và payload |
|---|---|
| CHAT_MESSAGE | Client → server: text, clientMessageId UUID; server lưu trước rồi echo hai bên |
| CHAT_MESSAGE | Server → client: messageId,clientMessageId,senderId,text,messageType,sentAt,status |
| CHAT_FILE | Server → client: message DTO + attachment metadata; IMAGE hoặc FILE |
| CHAT_AUDIO | Server → client: message DTO + attachment metadata/thời lượng |
| MESSAGE_DELIVERED / MESSAGE_READ | Người nhận → server: messageId, roomId envelope |
| MESSAGE_STATUS | Server → hai thành viên: messageId,status; READ không bị hạ về DELIVERED |

Không có CHAT_HISTORY WS riêng: đồng bộ bằng REST có phân trang. clientMessageId unique trong conversation+sender, retry cùng ID không tạo thêm bản ghi; server chỉ echo lại cho sender khi trùng. UI chỉ hiện một messageId và hiển thị SENT/DELIVERED/READ. READ hiện dựa vào tab visible lúc nhận, không khẳng định con người thực sự đã đọc. Upload HTTP và WS echo có thể tới khác thứ tự nên client dedupe theo messageId.

Next kết thúc conversation cũ trước khi tìm mới, kiểm tra stale room, giải phóng peer cũ. Stop đóng socket/media. Reconnect bắt đầu phiên ghép mới, không tiếp tục room WebRTC cũ; tài khoản có thể đọc hội thoại cũ qua trang lịch sử. Reload tài khoản dùng cookie refresh khôi phục user rồi lấy history. Queue/room/outbox vẫn nằm RAM: chỉ **một backend instance**; restart đánh dấu ACTIVE cũ CLOSED qua startup recovery. Chưa hỗ trợ nhiều replica hoặc resume media sau restart. Nội dung text/file/audio được lưu không phải E2EE; video/audio cuộc gọi không được backend ghi lại.

Tests tự động: permanent/mixed retention, phân trang, chống duplicate, ACL, receipt, hide riêng, matching/concurrency/Next/Stop/stale room và TCP signaling hồi quy. Hai thiết bị thật và TURN vẫn phải kiểm thử thủ công.
