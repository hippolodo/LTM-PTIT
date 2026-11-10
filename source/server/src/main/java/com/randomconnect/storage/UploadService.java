package com.randomconnect.storage;
import com.randomconnect.auth.*;
import com.randomconnect.persistence.*;
import com.randomconnect.service.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.Semaphore;
@Service
public class UploadService {
 private final StorageService storage;private final UploadValidator validator;private final ConversationService chats;private final MatchmakingService engine;private final ConversationRepository conversations;private final AttachmentRepository attachments;private final MessageRepository messages;private final RequestLimits limits;private final Semaphore uploads=new Semaphore(2);
 private final SocialService social;
 public UploadService(StorageService s,UploadValidator v,ConversationService c,MatchmakingService e,ConversationRepository r,AttachmentRepository a,MessageRepository m,RequestLimits l,SocialService social){storage=s;validator=v;chats=c;engine=e;conversations=r;attachments=a;messages=m;limits=l;this.social=social;}
 public Map<String,Object> upload(Actor actor,String room,MultipartFile file,String clientId,boolean voice)throws java.io.IOException {
  UUID.fromString(clientId);limits.take("upload:"+actor.kind()+actor.id(),10,60000);
  boolean direct="DIRECT".equals(conversations.findById(room).orElseThrow(ConversationService::notFound).conversationType);
  var existing=within(actor,room,direct,()->chats.existing(actor,room,clientId));if(existing.isPresent())return existing.get();
  if(!uploads.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Uploads are busy; please retry");String key=null;
  try {byte[] bytes=file.getBytes();var valid=validator.validate(file.getOriginalFilename(),file.getContentType(),bytes,voice);long size=file.getSize();within(actor,room,direct,()->{quota(actor,room,size);return true;});
   boolean temporary=!conversations.findById(room).orElseThrow(ConversationService::notFound).persistentHistory;
   key=(temporary?"temporary/":"persistent/")+room+"/"+UUID.randomUUID();storage.put(key,bytes,valid.mime());
   var attachment=new MessageAttachment();attachment.originalFilename=valid.name();attachment.storageKey=key;attachment.mimeType=valid.mime();attachment.fileSize=size;attachment.attachmentType=valid.kind();attachment.durationSeconds=valid.duration();
   java.util.function.Supplier<ConversationService.Saved> save=()->{
    var duplicate=chats.existing(actor,room,clientId);
    if(duplicate.isPresent())return new ConversationService.Saved(duplicate.get(),false);
    quota(actor,room,size);return chats.attachment(actor,room,valid.kind(),clientId,attachment);
   };
   var saved=direct?social.inDirect(actor,room,save):engine.publishAttachment(actor,room,save);
   if(!saved.created())storage.delete(key);return saved.payload();
  }catch(Exception e){if(key!=null)try{storage.delete(key);}catch(Exception ignored){}throw e;}finally{uploads.release();}
 }
 private <T> T within(Actor actor,String room,boolean direct,java.util.function.Supplier<T> action){return direct?social.inDirect(actor,room,action):engine.inRoom(actor,room,action);}
 private void quota(Actor actor,String room,long bytes){var p=chats.member(actor,room,true);if(attachments.bytesByParticipant(p.id)+bytes>52428800)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Conversation upload quota exceeded (50 MiB per participant)");}
 public Map<String,Object> download(Actor actor,String id){var a=attachments.findById(id).orElseThrow(ConversationService::notFound);var m=messages.findById(a.messageId).orElseThrow(ConversationService::notFound);chats.member(actor,m.conversationId,false);return Map.of("url",storage.signedGet(a.storageKey,a.originalFilename,a.mimeType),"expiresIn",60);}
}
