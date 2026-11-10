package com.randomconnect.controller;
import com.randomconnect.auth.Actor;
import com.randomconnect.storage.UploadService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController
public class AttachmentController {
 private final UploadService uploads;public AttachmentController(UploadService u){uploads=u;}
 @PostMapping(value="/api/conversations/{id}/attachments",consumes="multipart/form-data") Object upload(@AuthenticationPrincipal Actor actor,@PathVariable String id,@RequestPart("file") MultipartFile file,@RequestParam String clientMessageId,@RequestParam(defaultValue="false") boolean voice)throws java.io.IOException{return uploads.upload(actor,id,file,clientMessageId,voice);}
 @GetMapping("/api/attachments/{id}/download-url") Object download(@AuthenticationPrincipal Actor actor,@PathVariable String id){return uploads.download(actor,id);}
}
