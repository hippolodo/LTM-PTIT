HỌC VIỆN CÔNG NGHỆ BƯU CHÍNH VIỄN THÔNG
KHOA CÔNG NGHỆ THÔNG TIN 1

# BÁO CÁO TIẾN ĐỘ BÀI TẬP LỚN
## Môn Lập trình mạng

RANDOMCONNECT
Hệ thống trò chuyện và gọi video trực tuyến ngẫu nhiên thời gian thực

Công nghệ trọng tâm: WebSocket và WebRTC

Giảng viên hướng dẫn: [Họ và tên giảng viên]
Lớp học phần: [Mã lớp học phần]
Nhóm thực hiện: [Số nhóm]

Thành viên 1: [Họ tên – Mã sinh viên]
Thành viên 2: [Họ tên – Mã sinh viên]
Thành viên 3: [Họ tên – Mã sinh viên]

Ngày báo cáo: 11 tháng 11 năm 2026

Mã nguồn: source/server và source/client
Mốc mã nguồn đối chiếu: commit 0647445

Hà Nội, tháng 11 năm 2026

<!-- PAGE -->
# 1 Tổng quan và đánh giá tiến độ
## 1.1 Mục đích báo cáo
Báo cáo trình bày kết quả xây dựng RandomConnect đến ngày 11/11/2026, gồm phạm vi chức năng, kiến trúc, phân công công việc, kết quả kiểm thử và các vấn đề cần hoàn thiện. Nhóm đã triển khai phần lớn chức năng cốt lõi ở cả backend và frontend. Công việc hiện tại tập trung vào xác minh hoạt động trên môi trường triển khai, xử lý lỗi gửi tin nhắn thoại và kiểm tra độ ổn định kết nối cơ sở dữ liệu.

RandomConnect cho phép hai người dùng được ghép ngẫu nhiên để nhắn tin, gửi tệp và gọi video. Hệ thống hỗ trợ truy cập dưới danh tính khách hoặc tài khoản đăng ký. Với tài khoản, người dùng có thể tìm kiếm, kết bạn, trò chuyện riêng và thực hiện cuộc gọi trực tiếp. Đề tài vận dụng lập trình mạng thông qua kết nối WebSocket lâu dài, trao đổi báo hiệu WebRTC, xử lý bất đồng bộ và đồng bộ trạng thái nhiều phiên.

## 1.2 Phạm vi và mức độ hoàn thành
Trạng thái chung của đề tài là đã triển khai các chức năng chính và đang kiểm thử tích hợp trên môi trường thực tế. Đánh giá này phân biệt việc có mã nguồn, việc vượt qua kiểm thử tự động và việc đã được xác minh trên hệ thống triển khai. Chức năng vượt qua test với dữ liệu giả lập chưa được xem là đã nghiệm thu trên thiết bị thật.

Các nhóm chức năng đã triển khai gồm xác thực, ghép cặp, quản lý phòng, chat thời gian thực, lịch sử hội thoại, kết bạn, chat riêng, báo hiệu cuộc gọi, upload và ghi âm. Hệ thống đã được triển khai theo phản ánh của nhóm; tuy nhiên, gửi audio trên bản deploy vẫn xuất hiện lỗi. Backend cũng đã ghi nhận tình trạng chờ kết nối DB và lỗi đọc phản hồi từ MySQL.

## 1.3 Cấu trúc báo cáo
Trang 3 trình bày yêu cầu và tiêu chí đánh giá. Trang 4 mô tả kiến trúc và công nghệ. Trang 5 tập trung vào backend và giao thức. Trang 6 trình bày frontend và truyền thông đa phương tiện. Trang 7 mô tả dữ liệu và bảo mật. Trang 8 tổng hợp bằng chứng kiểm thử. Trang 9 phân tích khó khăn và rủi ro. Trang 10 trình bày phân công, kế hoạch và điều kiện hoàn thành.

Nhóm ưu tiên hoàn thiện luồng sử dụng xuyên suốt: đăng nhập hoặc vào dưới danh tính khách, ghép cặp, trao đổi tin nhắn, thực hiện cuộc gọi, gửi tệp và kết thúc phiên đúng trạng thái. Chưa đưa ra tỷ lệ hoàn thành định lượng khi chưa có trọng số và checklist nghiệm thu được thống nhất.

<!-- PAGE -->
# 2 Yêu cầu và phạm vi thực hiện
## 2.1 Yêu cầu chức năng
Người dùng khách được cấp danh tính tạm để tham gia hệ thống. Người dùng đăng ký có thể đăng nhập, cập nhật tên hiển thị và sử dụng các chức năng gắn với tài khoản. Hệ thống cần xác định đúng danh tính trong cả REST API và WebSocket, đồng thời từ chối thao tác khi phiên không còn hợp lệ.

Với ghép cặp ngẫu nhiên, người dùng gửi yêu cầu tìm đối tác và được đưa vào hàng đợi. Server lựa chọn một người đủ điều kiện, tạo phòng hai thành viên và thông báo vai trò khởi tạo kết nối. Người dùng có thể chuyển sang đối tác tiếp theo bằng Next hoặc kết thúc tìm kiếm bằng Stop. Khi mất kết nối, đối tác còn lại phải nhận được trạng thái phù hợp, tránh tiếp tục thao tác trên phòng đã đóng.

Trong hội thoại, hai bên có thể trao đổi văn bản, gửi tệp được hỗ trợ và tin nhắn thoại. Tin nhắn sử dụng mã phía client để hạn chế lưu trùng khi gửi lại. Người dùng có tài khoản được xem lịch sử thuộc quyền truy cập của mình. Hội thoại có khách được quản lý theo chính sách lưu tạm, không tự chuyển thành lịch sử tài khoản sau khi đăng ký.

Chức năng mở rộng gồm tìm tài khoản, gửi và xử lý lời mời kết bạn, nhắn tin riêng, mời gọi, nhận cuộc gọi, từ chối và kết thúc cuộc gọi. Việc truy cập tin nhắn, tệp hoặc cuộc gọi phải được kiểm tra theo thành viên và quan hệ liên quan.

## 2.2 Yêu cầu phi chức năng
Hệ thống cần duy trì kết nối thời gian thực, xử lý đúng các sự kiện đến gần nhau và giải phóng camera, microphone khi người dùng kết thúc thao tác. Dữ liệu đầu vào cần được giới hạn độ dài, kích thước và tần suất. Tệp upload phải được xác minh theo nội dung thay vì chỉ tin tên tệp hoặc MIME do trình duyệt cung cấp.

Khả năng triển khai cần bảo đảm cấu hình riêng giữa local và cloud, bảo vệ thông tin xác thực, hỗ trợ HTTPS/WSS và kết nối DB có TLS. Các thông số thời gian chờ, pool kết nối và dung lượng phải phù hợp tài nguyên dịch vụ. Chưa đặt mục tiêu đáp ứng số lượng người dùng đồng thời cụ thể khi chưa có phép đo tải tương ứng.

## 2.3 Tiêu chí nghiệm thu giai đoạn
Một luồng được xem là hoàn thành khi thao tác thành công từ giao diện đến backend, dữ liệu đúng, phía đối tác nhận đúng trạng thái và trường hợp lỗi được xử lý rõ ràng. Với media, cần kiểm tra hình và tiếng trên hai thiết bị, bao gồm trường hợp phải qua TURN. Với audio, cần xác nhận ghi, nghe thử, gửi, nhận và phát lại trên bản deploy. Với DB, cần xác nhận khởi động, truy vấn và phục hồi sau lỗi mạng mà không gây lỗi dây chuyền kéo dài.

<!-- PAGE -->
# 3 Kiến trúc và công nghệ
## 3.1 Các thành phần hệ thống
Frontend được xây dựng bằng Vue 3, sử dụng Vite để phát triển và tạo bản build. Pinia quản lý trạng thái xác thực, phòng chat và cuộc gọi. Các thành phần giao diện gọi service hoặc composable để tách thao tác mạng, truy cập thiết bị và điều phối trạng thái khỏi phần hiển thị.

Backend sử dụng Java 21 và Spring Boot 3.5.16. REST API phục vụ xác thực, hồ sơ, lịch sử, quan hệ bạn bè, upload và cấp vé WebSocket. Spring WebSocket tiếp nhận kết nối lâu dài, xử lý sự kiện ghép cặp, tin nhắn và báo hiệu. Spring Security cùng OAuth2 Resource Server và JwtEncoder/JwtDecoder đảm nhiệm các phần liên quan JWT; báo cáo không sử dụng tên JJWT cho cấu hình hiện tại.

MySQL lưu tài khoản, phiên, hội thoại, thành viên, tin nhắn, metadata tệp và quan hệ xã hội. JPA cung cấp tầng truy cập dữ liệu, Flyway quản lý phiên bản schema và HikariCP quản lý pool kết nối. Các tệp nhị phân được lưu qua giao diện tương thích S3; cấu hình dự án hỗ trợ bucket riêng tư trên Cloudflare R2.

## 3.2 Phân chia giao thức
REST hoạt động theo mô hình yêu cầu và phản hồi, phù hợp các thao tác truy vấn, xác thực và truyền multipart. WebSocket cung cấp kênh hai chiều để server chủ động gửi sự kiện. WebRTC truyền âm thanh và hình ảnh giữa các trình duyệt; backend chuyển tiếp SDP và ICE để hai bên thiết lập phiên truyền thông.

STUN hỗ trợ phát hiện địa chỉ và tìm đường kết nối. Khi đường trực tiếp không khả dụng, TURN có thể chuyển tiếp media. Vì vậy, media không đi qua backend ứng dụng, nhưng vẫn có thể đi qua máy chủ TURN. Đây là điểm cần phân biệt khi đánh giá kiến trúc, độ trễ và chi phí băng thông.

## 3.3 Luồng kết nối tổng quát
Trình duyệt lấy danh tính và thông tin xác thực qua REST, xin vé ngắn hạn rồi mở WebSocket. Khi ghép cặp thành công, server tạo phòng và chỉ định bên khởi tạo. Hai trình duyệt trao đổi OFFER, ANSWER và ICE_CANDIDATE thông qua server, sau đó thiết lập media bằng WebRTC. Tin nhắn và tệp vẫn sử dụng các luồng ứng dụng riêng tương ứng.

## 3.4 Giới hạn kiến trúc hiện tại
Hàng đợi, phòng và trạng thái cuộc gọi đang nằm trong RAM của một backend instance. Khởi động lại làm mất trạng thái hoạt động, dù dữ liệu đã lưu DB vẫn còn theo chính sách. Chưa thể tăng nhiều replica độc lập mà vẫn bảo đảm ghép cặp và chuyển tiếp chính xác. Redis hoặc một tầng điều phối dùng chung được xem là hướng phát triển sau khi hoàn thiện bản một instance.

<!-- PAGE -->
# 4 Tiến độ backend và giao thức thời gian thực
## 4.1 WebSocket và quản lý phiên
Giao thức ứng dụng dùng thông điệp JSON với các trường type, requestId, roomId và payload. Handler kiểm tra cấu trúc và độ dài trường, giới hạn thông điệp ở 64 KiB. Server chỉ xử lý các sự kiện hợp lệ trong ngữ cảnh hiện tại; lỗi được trả lại để client cập nhật giao diện thay vì tiếp tục thao tác trên trạng thái không còn tồn tại.

Heartbeat giúp phát hiện phiên không còn hoạt động. Client gửi PING định kỳ khoảng 15 giây, server sử dụng ngưỡng không hoạt động mặc định 45 giây. Cơ chế này cần được kiểm tra khi mất mạng, đóng tab hoặc cloud khởi động lại để xác nhận phòng và hàng đợi được dọn đúng.

## 4.2 Ghép cặp và điều khiển đồng thời
MatchmakingService quản lý danh sách phiên, hàng đợi chờ và trạng thái phòng bằng khóa dùng chung. Các ứng viên không hợp lệ được loại bỏ trước khi chọn đối tác ngẫu nhiên. Khi nhận Next hoặc Stop, server đóng phòng hiện tại, thông báo cho đối tác và đưa người yêu cầu về trạng thái tương ứng.

Thông điệp gửi ra được đưa vào hàng đợi trong bộ nhớ rồi điều phối gửi ngoài khóa trạng thái. Đây là cơ chế outbox nội bộ, không phải hàng đợi bền vững trong DB. Mỗi phiên dùng ConcurrentWebSocketSessionDecorator với ngưỡng gửi 2.000 ms và bộ đệm 256 KiB nhằm kiểm soát việc gửi đồng thời và client chậm. Những cơ chế này giảm ảnh hưởng của thao tác gửi socket, nhưng không thay thế đo tải và kiểm tra các truy vấn DB trong đường xử lý.

## 4.3 Báo hiệu và cuộc gọi trực tiếp
SignalingService kiểm tra quyền thành viên phòng trước khi chuyển tiếp OFFER, ANSWER và ICE_CANDIDATE. Vai trò khởi tạo được xác định nhằm hạn chế hai bên cùng phát OFFER trong luồng ghép cặp thông thường. Server không xử lý hay lưu nội dung video của cuộc gọi.

DirectCalls triển khai các bước đăng ký nhận cuộc gọi, mời gọi, chấp nhận, từ chối và kết thúc. Cuộc gọi không được trả lời sẽ hết thời gian đổ chuông sau khoảng 30 giây. Các nhánh offline, bận, hủy và mất kết nối cần được phản ánh thống nhất ở cả hai client.

## 4.4 Đánh giá hiện tại
Các thành phần backend chính đã có mã nguồn và kiểm thử tương ứng. Công việc còn lại tập trung vào xác minh hoạt động với DB và storage thật, theo dõi thời gian giữ kết nối và kiểm tra độ ổn định trên dịch vụ triển khai. Chưa đủ bằng chứng để khẳng định hệ thống đáp ứng tải lớn hoặc vận hành ổn định dài hạn.

<!-- PAGE -->
# 5 Tiến độ frontend và chức năng media
## 5.1 Giao diện và quản lý trạng thái
Frontend đã triển khai các màn hình phục vụ ghép cặp, trò chuyện, cuộc gọi, danh sách bạn bè và chat riêng. Giao diện hỗ trợ chế độ sáng và tối. Các thành phần như MessageComposer, VideoPlayer và DirectCallPanel tách phần nhập tin, phát media và điều khiển cuộc gọi để dễ bảo trì.

Các store auth, session và call quản lý trạng thái theo từng phạm vi. Khi đổi hội thoại hoặc kết thúc phiên, client cần hủy thao tác cũ và bỏ qua phản hồi đến muộn. Tin nhắn nhận lại từ nhiều nguồn được hợp nhất theo mã định danh để tránh hiển thị trùng. Việc khôi phục đăng nhập thực hiện qua refresh cookie, trong khi access token được giữ trong RAM.

## 5.2 Thiết lập kết nối WebRTC
Client sử dụng RTCPeerConnection để trao đổi media và phối hợp với socketClient cho báo hiệu. ICE candidate đến trước khi mô tả từ xa được thiết lập sẽ được đưa vào hàng đợi pending. Các thao tác báo hiệu được tuần tự hóa qua chuỗi Promise, đồng thời kiểm tra ngữ cảnh để tránh cập nhật nhầm cuộc gọi đã bị hủy hoặc thay thế.

Người dùng có thể bật, tắt camera và microphone theo khả năng giao diện. Kết thúc cuộc gọi, rời trang hoặc đổi đối tác cần dừng track, đóng peer connection và dọn listener. Kiểm thử tự động giúp kiểm tra nhánh xử lý, nhưng vẫn cần kiểm tra thiết bị thật để xác nhận hình, tiếng, quyền trình duyệt và khả năng phục hồi khi mạng thay đổi.

## 5.3 Gửi tệp và tin nhắn thoại
MessageComposer gửi multipart chứa file, clientMessageId và cờ voice tới API đính kèm. Bộ ghi âm sử dụng MediaStream riêng, không lấy trực tiếp track của cuộc gọi. Client ưu tiên định dạng trình duyệt hỗ trợ như WebM/Opus, Ogg/Opus hoặc MP4; giới hạn ghi âm tối đa 120 giây và 10 MiB.

Backend xác minh nội dung bằng Apache Tika, kiểm tra phần mở rộng và MIME, sau đó dùng ffprobe xác định audio-only và thời lượng. Metadata lưu DB, nội dung nhị phân lưu object storage. Client xin URL tải có thời hạn để phát hoặc tải tệp theo quyền thành viên.

## 5.4 Tình trạng cần hoàn thiện
Theo kết quả sử dụng hiện tại, gửi audio ở local hoạt động, còn bản deploy báo “Invalid input or file size”. Nhóm chưa xác định được nguyên nhân gốc. Cần đối chiếu request multipart, mã phản hồi, bản backend đang phục vụ và log lỗi tại thời điểm gửi. Không kết luận lỗi do dung lượng hoặc ffprobe chỉ từ thông báo chung. Sau sửa cần thử đoạn ngắn, đoạn dài và phát lại từ phía người nhận.

<!-- PAGE -->
# 6 Dữ liệu và bảo mật
## 6.1 Cấu trúc cơ sở dữ liệu
Schema hiện được quản lý qua hai migration V1 và V2, gồm tổng cộng 8 bảng. V1 tạo users, guest_sessions, conversations, conversation_participants, messages, message_attachments và auth_sessions. V2 bổ sung social_connections để quản lý quan hệ xã hội và liên kết hội thoại trực tiếp. Con số này thay thế mô tả 7 bảng trong bản báo cáo trước.

Tài khoản và danh tính khách được tách riêng. Hội thoại có danh sách thành viên để kiểm tra quyền truy cập. Tin nhắn liên kết với người gửi thông qua thành viên hội thoại; tệp đính kèm lưu metadata và khóa đối tượng. Các ràng buộc khóa ngoại, duy nhất và kiểm tra dữ liệu hỗ trợ duy trì tính nhất quán bên cạnh kiểm tra tại service.

## 6.2 Chính sách lịch sử và dữ liệu tạm
Hội thoại giữa hai tài khoản được đánh dấu lưu lịch sử. Người dùng có thể ẩn lịch sử theo quyền của mình sau khi hội thoại kết thúc; thao tác này cần phân biệt với xóa toàn bộ dữ liệu của cả hai bên. Hội thoại có khách sử dụng thời hạn lưu tạm mặc định 24 giờ và không xuất hiện trong lịch sử tài khoản.

RetentionService xử lý dữ liệu hết hạn theo lịch. Việc dọn dữ liệu phải phối hợp xóa metadata và object storage; cần kiểm tra trường hợp xóa object thất bại để tránh dữ liệu mồ côi. Lifecycle của bucket có thể được dùng làm lớp hỗ trợ, nhưng không thay thế kiểm tra quyền truy cập tại ứng dụng.

## 6.3 Xác thực và bảo vệ API
Mật khẩu được băm bằng BCrypt. Access JWT có thời hạn mặc định 10 phút. Refresh token lưu dưới dạng băm và được xoay vòng; khi phát hiện token bị dùng lại, hệ thống thu hồi phiên liên quan. Refresh cookie dùng HttpOnly; môi trường triển khai cấu hình Secure và SameSite phù hợp luồng frontend/backend.

WebSocket sử dụng vé dùng một lần với thời hạn khoảng 30 giây. Các API thay đổi dữ liệu có cơ chế CSRF và kiểm tra danh tính. Giới hạn tần suất được áp dụng cho các thao tác quan trọng. Cấu hình CORS, origin WebSocket và cookie cần được kiểm tra cùng domain triển khai thực tế.

## 6.4 Bảo vệ tệp và giới hạn hiện tại
Bucket được thiết kế ở chế độ riêng tư; URL tải có thời hạn mặc định 60 giây chỉ được cấp sau khi xác minh quyền thành viên. Việc kiểm tra định dạng và DOCX giúp giảm đầu vào không hợp lệ, nhưng chưa có hệ thống quét mã độc chuyên dụng. Tin nhắn văn bản chưa được mã hóa đầu cuối. WebRTC bảo vệ media trên đường truyền bằng cơ chế của giao thức, không đồng nghĩa toàn bộ dữ liệu ứng dụng đã có E2EE.

<!-- PAGE -->
# 7 Kết quả kiểm thử và phạm vi xác minh
## 7.1 Kết quả đã được ghi nhận
Tài liệu docs/TESTING.md ghi nhận lượt Maven verify ngày 10/11/2026 đạt 74 test, không thất bại, không lỗi và không bỏ qua. Lượt kiểm thử frontend gần nhất được ghi nhận đạt 35 test trên 10 tệp; production build của Vite thành công. Đây là kết quả lịch sử đã ghi trong dự án, không phải kết quả chạy lại tại thời điểm soạn báo cáo này.

Backend sử dụng H2 ở test profile và storage giả lập cho nhiều bài kiểm tra tích hợp. Các test bao phủ xác thực, vòng đời phòng, ghép cặp, báo hiệu, lịch sử, kiểm soát truy cập, quota, upload, cuộc gọi trực tiếp và phản hồi lỗi. Các test này hỗ trợ phát hiện hồi quy logic nhưng không xác nhận đầy đủ hành vi của MySQL và R2 trên cloud.

## 7.2 Kiểm tra media và upload
BrowserVoiceTest đã kiểm tra WebM/Opus dạng streaming và trường hợp tệp WebM có video. Bản sửa cho phép nhận diện container Matroska phù hợp với voice .webm, sau đó vẫn kiểm tra audio-only và thời lượng bằng ffprobe. UploadErrorResponseTest kiểm tra trả thông báo riêng cho lỗi đính kèm thay vì gộp mọi lỗi audio thành lỗi kích thước.

Một số test media chỉ chạy khi có ffmpeg và ffprobe. Trong Docker, build stage và runtime có môi trường khác nhau; runtime cài ffmpeg không bảo đảm test trong build stage đã chạy đủ. Khi xem kết quả build cần kiểm tra cả số test bị skip. Các test PeerConnection giả lập chỉ xác nhận điều phối và báo hiệu, không chứng minh truyền hình và tiếng thành công.

## 7.3 Bằng chứng triển khai hiện có
Log sử dụng đã xác nhận backend local khởi động và tiếp nhận request. Đồng thời, hệ thống ghi nhận hết kết nối chờ trong pool và lỗi đọc phản hồi từ MySQL ở một lần khởi động sau đó. Bản deploy đã được thực hiện theo thông tin nhóm cung cấp, nhưng lỗi gửi audio vẫn tồn tại. Do đó chưa đánh dấu nghiệm thu toàn bộ E2E trên môi trường triển khai.

## 7.4 Các kiểm tra cần bổ sung
Cần kiểm tra hai người dùng trên hai trình duyệt hoặc thiết bị, bao gồm đăng nhập, ghép cặp, text hai chiều, Next, Stop và khôi phục sau mất mạng. Với tệp và audio cần dùng bucket thật, thử người ngoài truy cập, URL hết hạn, retry và cleanup. Với cuộc gọi cần xác minh cả kết nối trực tiếp và relay TURN, đồng thời thử gửi tin, tệp và ghi âm trong lúc gọi.

Kết quả npm audit bằng không chỉ có giá trị tại thời điểm và bộ dependency đã kiểm tra. Nhóm sẽ đính kèm log, commit và môi trường cho lượt nghiệm thu cuối thay vì sử dụng tuyên bố chung “mọi kịch bản đều đạt”.

<!-- PAGE -->
# 8 Khó khăn và rủi ro hiện tại
## 8.1 Timeout kết nối cơ sở dữ liệu
Log ngày 11/11/2026 cho thấy HikariPool có total=3, active=3, idle=0; các request chờ khoảng 5 giây rồi timeout. Đây là tình trạng không có kết nối trống trong pool tại thời điểm đó. Ba kết nối DB không tương ứng ba người dùng, vì nhiều request có thể lần lượt dùng chung kết nối sau khi được trả về pool.

Tăng DB_POOL_SIZE có thể giảm chờ khi có nhiều thao tác đồng thời, nhưng cần nằm trong giới hạn dịch vụ MySQL và tổng số backend instance. Giá trị 10 có thể được thử sau khi đối chiếu tài nguyên. Đây không phải bằng chứng đã xử lý nguyên nhân gốc nếu query, khóa hoặc transaction vẫn giữ kết nối lâu.

Một log khác báo Communications link failure và SocketTimeoutException: Read timed out trong lúc mở kết nối phục vụ Flyway. Lỗi này cần được phân biệt với hết pool: backend không nhận được phản hồi DB đúng hạn. Các khả năng cần kiểm tra gồm đường mạng, trạng thái dịch vụ, endpoint, giới hạn truy cập và tải DB. Chưa kết luận nguyên nhân là mạng hoặc IP filtering khi chưa có bằng chứng trực tiếp.

## 8.2 Audio khác biệt giữa local và deploy
Cùng chức năng gửi audio có thể chịu ảnh hưởng bởi phiên bản backend, codec trình duyệt, multipart, giới hạn request, tài nguyên runtime và công cụ probe. Thông báo “Invalid input or file size” là phản hồi tổng quát, chưa chỉ ra lớp xử lý gây lỗi. Hướng xử lý là tái hiện với bản ghi ngắn, lưu mã phản hồi và đối chiếu exception backend cùng thời điểm.

Sau khi xác định lỗi, nhóm cần kiểm thử lại cả chat ngẫu nhiên và chat riêng; thử nghe từ người gửi, phía người nhận và sau khi tải lại lịch sử. Không bỏ qua kiểm định file chỉ để làm upload thành công. Log phục vụ chẩn đoán cần tránh ghi token, mật khẩu hoặc nội dung nhạy cảm.

## 8.3 Rủi ro vận hành và giới hạn phạm vi
Backend một instance có thể mất trạng thái phòng khi restart hoặc cold start. TURN có thể làm tăng băng thông và độ trễ; chưa có số đo tải thực tế. Cookie giữa hai site có thể bị trình duyệt hạn chế, khiến xác thực khác nhau giữa local và deploy. Nhóm cần chuẩn bị cấu hình và kịch bản demo đã thử trước.

Quét virus, E2EE cho văn bản và mở rộng nhiều server nằm ngoài phần hoàn thiện ưu tiên hiện tại. Nhóm tập trung ổn định luồng cốt lõi, ghi nhận rõ các trường hợp chưa xác minh và không cam kết quy mô phục vụ khi chưa thực hiện đo tải.

<!-- PAGE -->
# 9 Phân công và kế hoạch hoàn thiện
## 9.1 Phân công dự kiến theo vai trò
Thành viên 1 phụ trách lõi backend, hàng đợi và phòng, xác thực, dữ liệu và điều khiển đồng thời. Trong giai đoạn tiếp theo, thành viên này theo dõi pool DB, truy vấn và transaction, kiểm tra khởi động với MySQL, đồng thời chuẩn bị số liệu độ trễ và lỗi khi chạy thử đồng thời.

Thành viên 2 phụ trách giao diện Vue, các store, đồng bộ tin nhắn và phản hồi lỗi. Công việc tiếp theo gồm kiểm tra endpoint mà bản frontend deploy đang sử dụng, lấy thông tin request upload, rà soát trạng thái chờ và retry, bổ sung ảnh giao diện và hoàn thiện phần mô tả thao tác người dùng.

Thành viên 3 phụ trách WebRTC, ICE/TURN, ghi âm, kiểm định tệp và tích hợp. Công việc ưu tiên là tái hiện lỗi audio deploy, xác minh ffprobe và codec thực tế, thử hai thiết bị khác mạng và thu thập bằng chứng đường truyền relay. Tên, mã sinh viên và đóng góp thực tế sẽ được nhóm điền trước khi nộp.

## 9.2 Thứ tự công việc giai đoạn tiếp theo
Ưu tiên 1 là xử lý các lỗi cản trở demo: audio deploy và kết nối DB. Mỗi lỗi cần có bước tái hiện, nguyên nhân, thay đổi xử lý và kết quả kiểm tra lại. Ưu tiên 2 là kiểm tra toàn bộ luồng trên hai thiết bị, gồm tài khoản, ghép cặp, chat riêng, tệp và cuộc gọi. Ưu tiên 3 là chạy lại bộ test, build và ghi nhận kết quả theo commit cuối.

Sau khi luồng chính ổn định, nhóm thực hiện phép thử tải trong phạm vi tài nguyên cho phép, ghi số phiên, số request, độ trễ và lỗi. Nhóm bổ sung sơ đồ kiến trúc, ảnh giao diện, thông tin triển khai và kịch bản demo. Thời điểm hoàn thành từng việc được thống nhất theo hạn nộp của học phần; báo cáo này không tự đặt ngày bàn giao chưa được nhóm xác nhận.

## 9.3 Điều kiện chuyển sang báo cáo hoàn thành
Hệ thống cần vượt qua luồng demo trên bản deploy, gửi và phát audio hai chiều, truy cập DB ổn định trong thời gian thử và kết thúc cuộc gọi đúng trạng thái. Các ca chưa kiểm tra phải được liệt kê rõ. Báo cáo cuối cần có danh sách thành viên, nhánh và commit, log test, ảnh minh chứng và kết quả thực nghiệm tương ứng; thống kê Git phải lấy từ lịch sử thật.

## 9.4 Tài liệu đối chiếu
Các tài liệu nội bộ gồm README.md, docs/TESTING.md, docs/DEPLOYMENT_GUIDE.md, docs/AUTHENTICATION.md, docs/CHAT_HISTORY.md, docs/FILE_AUDIO_SHARING.md, docs/FRIENDS_AND_DIRECT_CHAT.md và docs/DATABASE_DESIGN.md. Mã nguồn đối chiếu nằm tại source/server và source/client; schema tại db/migration của backend. Nhóm tiếp tục hoàn thiện khả năng vận hành thực tế trước khi nghiệm thu đề tài.
