package com.randomconnect;

import com.randomconnect.controller.ApiErrors;
import com.randomconnect.storage.UploadValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UploadErrorResponseTest {
 @RestController static class InvalidVoice {
  @GetMapping("/invalid-voice") Object invalid(){throw new UploadValidationException("Unsupported audio format");}
 }
 @Test void returnsSpecificSafeAudioErrorInsteadOfGenericSizeError()throws Exception {
  MockMvcBuilders.standaloneSetup(new InvalidVoice()).setControllerAdvice(new ApiErrors()).build()
   .perform(get("/invalid-voice")).andExpect(status().isBadRequest())
   .andExpect(jsonPath("code").value("INVALID_ATTACHMENT"))
   .andExpect(jsonPath("message").value("Định dạng ghi âm chưa được hỗ trợ. Hãy ghi lại bằng WebM, Ogg, MP4 hoặc WAV."));
 }
}
