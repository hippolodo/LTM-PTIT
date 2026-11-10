# File và tin nhắn thoại

File được gửi bằng multipart REST, không đưa binary vào WebSocket. POST `/api/conversations/{id}/attachments` với `file`, `clientMessageId` UUID, `voice=false|true`; yêu cầu header auth và đang ở room đó. Response là message DTO; server phát CHAT_FILE/CHAT_AUDIO cho cả hai. Retry giữ clientMessageId trả lại message đã có. GET `/api/attachments/{id}/download-url` kiểm tra thành viên/quyền/TTL rồi trả `{url,expiresIn:60}`. URL là bearer capability: ai có URL có thể dùng trong 60 giây, kể cả sau khi người dùng ẩn lịch sử. UI có nút lấy lại URL nếu đã hết hạn.

PNG/JPEG/WEBP, PDF, UTF-8 TXT, DOCX; default 10 MiB/file, 50 MiB/người/cuộc hội thoại, 10 upload/phút/định danh; chỉ 2 upload xử lý đồng thời/instance. `UPLOAD_MAX_BYTES` cấu hình giới hạn validator + multipart; max-request-size mặc định 11MB, khi tăng file limit cần tăng `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` tương ứng. Frontend chủ động giới hạn 10 MiB mặc định, muốn thay đổi phải đồng bộ UI.

Tika đọc signature nội dung; kiểm tra extension/MIME khai báo khớp; chặn tên chứa slash/backslash/.. hoặc control character. TXT phải UTF-8 không có binary control; PDF có EOF; DOCX kiểm tra ZIP, entry bắt buộc, chặn macro/path traversal, tối đa 1000 entries/32MiB giải nén. Đây là validation định dạng cơ bản, **không phải antivirus hoặc bảo đảm tài liệu không độc hại**. SVG/HTML/executable không được hỗ trợ. File document tải dưới dạng attachment; không thực thi trên server. Không trả storage key/secret trong DTO.

## Object Storage

`StorageService` có implementation S3-compatible (AWS SDK). Mặc định STORAGE_PROVIDER=disabled; text/video vẫn chạy nhưng gửi/tải file trả 503. Cần tự tạo bucket private, chưa tích hợp bucket thật vì chưa có credential. Cấu hình Render:

```dotenv
STORAGE_PROVIDER=s3
STORAGE_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
STORAGE_REGION=auto
STORAGE_BUCKET=<PRIVATE_BUCKET>
STORAGE_ACCESS_KEY_ID=<SERVER_ONLY>
STORAGE_SECRET_ACCESS_KEY=<SERVER_ONLY>
STORAGE_ALLOW_HTTP=false
```

S3 dùng endpoint và region tương ứng dịch vụ. Account/key chỉ được quyền read/write/delete bucket cần dùng. Không bật public access/r2.dev. Prefix `temporary/<conversation>/...` hoặc `persistent/<conversation>/...`; key UUID ngẫu nhiên không lấy từ filename.

Bucket CORS nếu trình duyệt/player yêu cầu: origin chính xác https://ltm-ptit.vercel.app và localhost khi test; methods GET/HEAD; allowed Range/Content-Type; expose Content-Length,Content-Range,Accept-Ranges. Upload server-to-server nên không mở PUT cho browser. Kiểm thử range request cho seek audio với provider thực tế. Signed GET sống 60 giây; thời lượng voice tối đa 120 giây, đang phát có thể phải làm mới URL khi phát lại/seek sau expiry.

Cleanup định kỳ xóa object trước metadata. Đặt thêm lifecycle cho prefix temporary/ làm phương án dự phòng (ví dụ sau 2 ngày, không sớm hơn TTL ứng dụng). Multipart/incomplete upload lifecycle có thể bật theo dịch vụ. DB và storage không có distributed transaction: process chết sau PUT trước commit có thể để orphan, đặc biệt prefix persistent/. Cần đối soát object key với metadata và xóa orphan có grace period; hiện chưa có job đối soát tự động. Không xóa cả bucket. Không dùng disk Render để lưu file lâu dài; ffprobe chỉ dùng temporary file và luôn dọn.

## Voice

Nút ghi âm chỉ gọi getUserMedia sau thao tác. MediaRecorder chọn MIME browser hỗ trợ (WebM/Opus, Ogg/Opus, MP4), tên file theo định dạng thực tế; fallback dùng MIME recorder trả về. Stream audio riêng với WebRTC; stop/cancel/Next/unmount dừng **stream ghi âm riêng**, không dừng track cuộc gọi. Có đồng hồ, nghe lại, gửi/hủy và lỗi từ chối quyền/không hỗ trợ. Không ghi âm cuộc gọi.

Giới hạn mặc định 120 giây/10 MiB cả frontend; backend không tin thời lượng client, dùng ffprobe kiểm tra chỉ stream audio và thời lượng từ container/packet, timeout 8 giây. Thiếu ffprobe trả 503. Docker runtime đã cài ffmpeg/ffprobe; local Windows cài ffmpeg và bảo đảm ffprobe trong PATH hoặc đặt FFPROBE_PATH. AUDIO_MAX_SECONDS chỉnh backend cần đồng bộ UI nếu đổi default. Không có transcoding; browser người nhận vẫn cần codec tương thích với browser người gửi.

Tests: MIME giả, file quá lớn/executable/path traversal, quota/ACL theo service, upload hợp lệ/dedupe/IDOR, Next trong khi PUT dọn orphan, cleanup hết TTL, audio quá dài và WAV thực với ffprobe. Frontend có test codec, track cleanup, cancel khi permission đang chờ, tự stop 120 giây, denied/unsupported. Chưa xác minh microphone/player/song song WebRTC trên thiết bị thật hoặc upload thật lên R2/S3.
