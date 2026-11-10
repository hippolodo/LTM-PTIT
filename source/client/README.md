# RandomConnect client

Vue 3 Composition API, Pinia, Vue Router, Axios cho REST, native WebSocket và RTCPeerConnection. Node 22.12+.

```powershell
npm ci
npm run dev
npm test
npm run build
```

Vite dùng cổng 5173 cố định. Backend phải chạy ở 8081; bấm trạng thái server ở đầu trang để kiểm tra lại health. Mở http://localhost:5173 ở hai tab độc lập.

Chép `.env.example` thành `.env.local` nếu cần đổi `VITE_API_BASE_URL` / `VITE_WS_URL`. Backend mặc định cấp ICE servers qua SESSION_READY và MATCH_FOUND; `VITE_ICE_SERVERS` ghi đè chúng nếu được đặt. `VITE_ICE_CONFIG_URL` tùy chọn gọi endpoint bảo mật của bạn với credentials để lấy ICE config, có độ ưu tiên cao nhất. Không đặt TURN shared secret hay mật khẩu dài hạn trong frontend.

Camera/mic chỉ xin sau nút Enable/Start video. Ghép người lạ yêu cầu mở được camera/mic; nếu bị từ chối, cấp lại quyền trong trình duyệt rồi thử lại. Đã bỏ chế độ Chỉ chat chữ; tin nhắn chữ trong phòng video vẫn được hỗ trợ. Local video muted; remote video có nút phát nếu autoplay bị chặn. Dùng tai nghe khi demo hai tab. Next đóng peer và xóa ICE/remote stream, giữ local track cùng trạng thái enabled; Stop đóng WebSocket và stop local tracks. Stop đóng socket để xử lý cả race khi đang Waiting mà vừa được ghép. Kết nối lại dùng định danh còn hiệu lực và không tự vào queue; không resume room cũ.

`useMedia.js` hủy stream đến muộn sau Stop; `webrtcClient.js` tuần tự hóa SDP/ICE và bỏ callback của generation cũ. Chỉ PeerConnection `connected` mới chuyển UI sang CONNECTED. Chat dùng interpolation, không dùng v-html.

Tests dùng fake WebSocket/PeerConnection/media hoặc DOM jsdom; **không phải browser media E2E**. Hướng dẫn demo và kết quả: [TESTING](../../docs/TESTING.md).

## Bản nâng cấp

AuthPanel hỗ trợ guest/login/register; history/profile chỉ cho tài khoản. Access và guest token chỉ ở bộ nhớ; refresh cookie HttpOnly. AttachmentComposer/VoiceComposer upload qua REST, AttachmentView lấy URL ngắn hạn. useVoiceRecorder lấy mic riêng và dọn stream khi hủy/đổi room. Không lưu refresh trong localStorage.

Frontend mới phải kết nối backend mới có API auth. Production Vercel dùng VITE_API_BASE_URL=https://ltm-ptit.onrender.com và VITE_WS_URL=wss://ltm-ptit.onrender.com/ws; local ghi đè sang8081. Không có secret trong VITE_. Xem [deployment](../../docs/DEPLOYMENT_GUIDE.md), [auth](../../docs/AUTHENTICATION.md) và [file/voice](../../docs/FILE_AUDIO_SHARING.md).
