# Tiến độ theo thành phần

Cập nhật đến ngày 11/11/2026.

| **Thành phần** | **Phụ trách** | **Trạng thái** | **Ghi chú** |
|---|---|---|---|
| Cấu trúc dự án và cơ sở dữ liệu | TV1 | Đã triển khai | Có 8 bảng nghiệp vụ; đang kiểm tra độ ổn định kết nối DB |
| Kết nối và trao đổi dữ liệu thời gian thực | TV1 | Đã triển khai | Kiểm tra dữ liệu và phát hiện mất kết nối |
| Ghép cặp ngẫu nhiên và quản lý phòng chat | TV1 | Đã triển khai | Hỗ trợ tìm đối tác, Next và Stop |
| Đăng ký, đăng nhập và quản lý phiên | TV1 | Đã triển khai | Bảo vệ tài khoản và kiểm tra quyền truy cập |
| Kết bạn, hội thoại riêng và lịch sử trò chuyện | TV1 | Đã triển khai | Lưu và tải lịch sử theo quyền người dùng |
| Điều phối cuộc gọi trực tiếp | TV1 | Đã triển khai | Nhận, từ chối, kết thúc; chờ trả lời tối đa 30 giây |
| Giao diện và quản lý trạng thái ứng dụng | TV2 | Đã triển khai | Hỗ trợ chế độ sáng và tối |
| Hiển thị tin nhắn, chat riêng và danh sách bạn bè | TV2 | Đã triển khai | Hạn chế hiển thị trùng; có trạng thái nhận và đọc |
| Kết nối âm thanh và video giữa hai trình duyệt | TV3 | Đang kiểm thử | Cần xác minh thêm trên thiết bị và mạng thực tế |
| Hỗ trợ cuộc gọi giữa các mạng khác nhau | TV3 | Đã tích hợp | Cần kiểm tra trường hợp phải chuyển tiếp qua TURN |
| Gửi, nhận và lưu trữ tệp, ghi âm | TV3 | Đang hoàn thiện | Local gửi audio được; bản deploy còn lỗi |
| Ghi âm và nghe thử trước khi gửi | TV3 | Đã triển khai | Ghi âm tối đa 120 giây, dùng luồng riêng với cuộc gọi |
| Tích hợp và kiểm thử toàn hệ thống | Cả nhóm | Đang thực hiện | Đã có kết quả test tự động; cần kiểm tra đầy đủ trên bản deploy |
| Vận hành nhiều máy chủ, quét virus và mã hóa đầu cuối cho văn bản | Cả nhóm | Chưa thực hiện | Hướng phát triển |
| Sơ đồ kiến trúc, ảnh giao diện và tài liệu hướng dẫn | Cả nhóm | Đang hoàn thiện | Bổ sung minh chứng và rà soát báo cáo |

**Quy ước:** “Đã triển khai” thể hiện chức năng đã được xây dựng; chỉ chuyển sang “Hoàn thành” khi đã kiểm thử đạt các tiêu chí nghiệm thu tương ứng.

**Phân công:** TV1 — Nguyễn Văn Hiệp (Backend); TV2 — Frontend; TV3 — WebRTC, tệp và ghi âm.
