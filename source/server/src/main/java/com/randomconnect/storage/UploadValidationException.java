package com.randomconnect.storage;

/** Safe validation reasons; never includes uploaded contents, process output or filesystem paths. */
public class UploadValidationException extends IllegalArgumentException {
 public UploadValidationException(String reason) { super(reason); }
 public String userMessage() {
  return switch (getMessage()) {
   case "Invalid file name or size" -> "Tên tệp không hợp lệ, tệp rỗng hoặc vượt quá dung lượng cho phép.";
   case "Unsupported audio format" -> "Định dạng ghi âm chưa được hỗ trợ. Hãy ghi lại bằng WebM, Ogg, MP4 hoặc WAV.";
   case "Voice message is too long" -> "Ghi âm vượt quá thời lượng cho phép.";
   case "Voice must contain audio only" -> "Tin nhắn thoại chỉ được chứa âm thanh, không được chứa video.";
   case "No audio track" -> "Tệp ghi âm không có luồng âm thanh.";
   case "Audio has no valid duration" -> "Không xác định được thời lượng ghi âm. Hãy ghi lại và thử gửi lại.";
   case "Invalid audio", "Invalid or unsupported audio" -> "Tệp ghi âm không hợp lệ hoặc không đọc được. Hãy ghi lại và thử gửi lại.";
   case "Audio metadata too large" -> "Tệp ghi âm có dữ liệu quá phức tạp. Hãy ghi lại một đoạn ngắn hơn.";
   case "File extension does not match content", "Declared MIME does not match content" -> "Định dạng nội dung không khớp với tên hoặc loại tệp được gửi.";
   default -> "Nội dung tệp không hợp lệ hoặc định dạng chưa được hỗ trợ.";
  };
 }
}
