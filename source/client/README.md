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

Camera/mic chỉ xin sau nút Enable/Start video. Nếu bị từ chối, cấp lại quyền thủ công hoặc chọn Chỉ chat chữ. Local video muted; remote video có nút phát nếu autoplay bị chặn. Dùng tai nghe khi demo hai tab. Next đóng peer và xóa ICE/remote stream, giữ local track cùng trạng thái enabled; Stop đóng WebSocket và stop local tracks. Stop đóng socket để xử lý cả race khi đang Waiting mà vừa được ghép. Kết nối lại là một guest mới và không tự vào queue.

`useMedia.js` hủy stream đến muộn sau Stop; `webrtcClient.js` tuần tự hóa SDP/ICE và bỏ callback của generation cũ. Chỉ PeerConnection `connected` mới chuyển UI sang CONNECTED. Chat dùng interpolation, không dùng v-html.

Tests dùng fake WebSocket/PeerConnection/media hoặc DOM jsdom; **không phải browser media E2E**. Hướng dẫn demo và kết quả: [TESTING](../../docs/TESTING.md).
