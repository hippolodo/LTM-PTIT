# HTTPS, NAT và TURN

## Local và LAN

Localhost là secure context cho getUserMedia. HTTP qua IP LAN thường không được phép truy cập camera/mic. Với hai thiết bị, dùng domain/certificate HTTPS hợp lệ được cả hai tin cậy; không tắt kiểm tra TLS. Frontend phải trỏ API tới HTTPS và WebSocket tới WSS. Thêm chính xác origin frontend vào ALLOWED_ORIGINS ở backend.

Có thể đặt frontend build `source/client/dist` sau reverse proxy có SPA fallback. Proxy `/api/*` và `/ws` tới localhost:8081, giữ HTTP/1.1 WebSocket Upgrade. Ví dụ Caddy với tên miền bạn quản lý:

```caddyfile
chat.example.com {
    handle /api/* {
        reverse_proxy 127.0.0.1:8081
    }
    handle /ws {
        reverse_proxy 127.0.0.1:8081
    }
    handle {
        root * /srv/randomconnect/dist
        try_files {path} /index.html
        file_server
    }
}
```

Trước `npm run build`, đặt VITE_API_BASE_URL=https://chat.example.com và VITE_WS_URL=wss://chat.example.com/ws. Backend ALLOWED_ORIGINS=https://chat.example.com. Caddy/domain/certificate là hạ tầng bên ngoài, chưa chạy trong phiên này.

## Coturn với credential ngắn hạn

Cài Coturn trên host có public IP, tên miền và firewall phù hợp. Coturn cần `use-auth-secret`, `static-auth-secret` lấy từ secret riêng, `realm`, `external-ip` đúng topology. Giới hạn port relay (ví dụ 49160–49200 UDP), `listening-port=3478`, `tls-listening-port=5349`, chứng chỉ/key cho TURN TLS. Chỉ cấp cert hợp lệ; không bỏ xác thực hay certificate verification. Mở 3478 UDP/TCP, 5349 TCP và dải relay UDP trên firewall/NAT theo cấu hình thực. Đặt quota allocation/bandwidth, cấm loopback/multicast peer và giới hạn private peer ranges theo topology.

Tạo một secret ngẫu nhiên đủ mạnh, đặt cùng giá trị ở Coturn và environment backend; giữ file cấu hình chứa secret ngoài Git. Backend:

```powershell
$env:TURN_URLS = 'turn:turn.example.com:3478?transport=udp,turns:turn.example.com:5349?transport=tcp'
$env:TURN_SHARED_SECRET = '<secret riêng khớp Coturn>'
$env:ALLOWED_ORIGINS = 'https://chat.example.com'
.\mvnw.cmd spring-boot:run
```

Đây là mẫu cấu hình, hostname/secret phải thay bằng hạ tầng thật. Không đưa secret vào biến VITE_. Server ký username `<expiry>:<guestId>` bằng HMAC-SHA1 và gửi credential có hạn 600 giây qua WebSocket cho từng guest, cấp mới lúc MATCH_FOUND. Credential tạm thời có thể hiện trong DevTools vì trình duyệt cần nó; shared secret luôn ở server. Demo dài hơn thời hạn nên Next/Stop rồi ghép phiên mới; chưa có tự động refresh TURN credential trong cuộc gọi đang chạy.

Để thử relay, tạm dùng `iceTransportPolicy: 'relay'` trong cấu hình PeerConnection ở môi trường thử nghiệm và kiểm tra candidate pair trong browser WebRTC diagnostics, sau đó trả lại mặc định. Chỉ ghi passed khi hai thiết bị ở hai mạng thực sự nghe/nhìn nhau. STUN-only không bảo đảm qua symmetric NAT, mạng doanh nghiệp hoặc firewall chặn UDP. Mã nguồn đã hỗ trợ cấu hình; chưa cài Coturn hoặc xác minh cross-network ở phiên này.

## Giới hạn vận hành

Một instance RAM; HTTPS/TURN không tự tạo moderation, authentication hoặc bảo vệ DoS. Chỉ demo nội bộ. Chưa có Docker vì không cần cho luồng local và chưa kiểm chứng Docker ở môi trường này.
