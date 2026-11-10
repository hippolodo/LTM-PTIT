# Authentication và phiên

Tài khoản: username 3–32 ký tự chữ/số/underscore, email unique, password 8–72 ký tự và tối đa 72 byte UTF-8. Hash BCrypt cost 12. API chỉ trả UserView, không trả password_hash. PATCH profile hiện chỉ sửa displayName (1–80 ký tự).

Access JWT HS256 (issuer randomconnect, sub user UUID, sid auth session) sống mặc định 600 giây. JWT_SECRET bắt buộc tối thiểu 32 byte ngẫu nhiên. Frontend giữ access token trong bộ nhớ, mất khi reload; không ghi localStorage. Mỗi REST và sự kiện WS kiểm tra phiên/định danh còn hiệu lực trong DB. Socket đã xác thực tiếp tục dựa trên auth session, không phụ thuộc hạn JWT dùng để lấy ticket ban đầu.

Refresh: cookie `rc_refresh`, HttpOnly, path `/api/auth`, hạn tối đa 30 ngày không gia hạn mốc expiry khi rotate. DB chỉ giữ SHA-256 của token ngẫu nhiên. Refresh khóa bản ghi, xoay token; dùng lại token cũ thu hồi cả session và đóng socket liên quan. Logout thu hồi session hiện tại, xóa cookie; access JWT của session đó bị từ chối ngay. Không có chức năng logout toàn bộ thiết bị. Nhiều tab refresh đồng thời cùng cookie có thể bị phát hiện reuse; single-flight hiện áp dụng trong từng tab. Khi xảy ra, đăng nhập lại.

Guest chọn nút riêng, nhận opaque token ngẫu nhiên 32 byte; DB chỉ giữ hash, mặc định hết hạn 24 giờ. Token guest cũng chỉ giữ trong RAM. Reload cần tạo guest mới; không tự liên kết dữ liệu cũ khi đăng ký. last_active_at hiện khởi tạo khi tạo guest, không dùng để kéo dài TTL.

| Method | Endpoint | Quyền |
|---|---|---|
| GET | /api/auth/csrf | Public, trả token và headerName, đặt cookie CSRF HttpOnly |
| POST | /api/auth/guest | Public + CSRF |
| POST | /api/auth/register | Public + CSRF; username,email,password,displayName tùy chọn |
| POST | /api/auth/login | Public + CSRF; login (username/email),password |
| POST | /api/auth/refresh | Refresh cookie + CSRF |
| POST | /api/auth/logout | Cookie + CSRF |
| GET/PATCH | /api/users/me | Bearer access JWT, chỉ tài khoản |
| POST | /api/socket-ticket | Bearer JWT hoặc Guest token |

Client gửi `Authorization: Bearer <access>` hoặc `Authorization: Guest <token>` cho API bảo vệ. Không gửi các header này cho `/api/auth/**`. Unsafe auth request gửi cookie + header CSRF lấy từ GET csrf (vớiCredentials). Các API còn lại dùng header auth, không dùng cookie làm principal. CSRF bắt buộc cho cả login/register/guest để ngăn login CSRF. Rate limit auth 40 request/phút/IP trong một instance; đây là giới hạn demo, không thay thế WAF.

WS dùng ticket một lần, sống 30 giây: POST socket-ticket rồi mở `/ws?ticket=...`. Không đặt JWT/refresh token vào query. Giữ tương thích client cũ không có ticket bằng một guest DB mới; không thể giả user bằng senderId. Ticket sai/hết hạn bị từ chối. Tránh log query ticket tại proxy; token ngắn hạn vẫn là credential. Identity lấy từ handshake, không tin senderId trong payload.

Render profile: Secure + SameSite=None cho cookie refresh và CSRF, exact CORS origins + credentials. Local HTTP: Secure=false, SameSite=Lax. **Render.com và vercel.app khác site**: trình duyệt chặn third-party cookie có thể làm login/refresh thất bại dù CORS đúng. Cần cho phép cookie khi demo hoặc cấu hình hai custom domain cùng site (ví dụ app.example.com/api.example.com). Không hạ HttpOnly hay đưa refresh token vào JS để lách giới hạn này.

Tests xác nhận đăng ký/login đúng sai, trùng username/email, JWT hết hạn, refresh rotation/reuse, logout thu hồi, CSRF cookie/header thật và từ chối guest đọc profile. Hết hạn JWT test ký token với exp quá khứ; không cần chờ 10 phút.
