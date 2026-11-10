package com.randomconnect.service;
import com.randomconnect.auth.*;
import com.randomconnect.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
@Service
public class ConversationService {
 private final ConversationRepository conversations;private final ParticipantRepository participants;private final MessageRepository messages;private final AttachmentRepository attachments;private final UserRepository users;private final long temporaryHours;
 public record Saved(Map<String,Object> payload,boolean created){}
 public ConversationService(ConversationRepository c,ParticipantRepository p,MessageRepository m,AttachmentRepository a,UserRepository u,@Value("${app.temporary-chat-hours:24}") long hours){conversations=c;participants=p;messages=m;attachments=a;users=u;temporaryHours=hours;}
 @Transactional public Map<String,Object> create(String id,Actor first,Actor second){var c=new Conversation();c.id=id;c.persistentHistory=first.isUser()&&second.isUser();if(!c.persistentHistory)c.expiresAt=Instant.now().plus(Duration.ofHours(temporaryHours));conversations.saveAndFlush(c);for(var actor:List.of(first,second)){var p=new Participant();p.conversationId=id;if(actor.isUser())p.userId=actor.id();else p.guestSessionId=actor.id();participants.save(p);}return policy(c);}
 public Map<String,Object> policy(Conversation c){var out=new LinkedHashMap<String,Object>();out.put("persistentHistory",c.persistentHistory);out.put("expiresAt",c.expiresAt);out.put("retentionNotice",c.persistentHistory?"Saved to both accounts; each participant can hide their own history.":"Temporary conversation; expires within "+temporaryHours+" hours and never appears in account history.");return out;}
 @Transactional public void close(String id){var c=conversations.findById(id);if(c.isEmpty()||!c.get().status.equals("ACTIVE"))return;c.get().status="CLOSED";c.get().endedAt=Instant.now();for(var p:participants.findByConversationId(id))p.leftAt=Instant.now();}
 public Participant member(Actor actor,String id,boolean open){var c=conversations.findById(id).orElseThrow(ConversationService::notFound);if(c.expiresAt!=null&&!c.expiresAt.isAfter(Instant.now()))throw notFound();var p=participants.findByConversationId(id).stream().filter(x->actor.isUser()?actor.id().equals(x.userId):actor.id().equals(x.guestSessionId)).findFirst().orElseThrow(ConversationService::notFound);if(p.hiddenAt!=null)throw notFound();if(open&&(!c.status.equals("ACTIVE")||p.leftAt!=null))throw new ResponseStatusException(HttpStatus.CONFLICT,"Conversation has ended");return p;}
 @Transactional public Saved text(Actor actor,String id,String text,String clientId){var p=member(actor,id,true);return save(p,id,"TEXT",text,clientId,null);}
 @Transactional public Saved attachment(Actor actor,String id,String kind,String clientId,MessageAttachment attachment){var p=member(actor,id,true);return save(p,id,kind,null,clientId,attachment);}
 private Saved save(Participant p,String id,String kind,String text,String clientId,MessageAttachment attachment){
  UUID.fromString(clientId);var existing=messages.findByConversationIdAndSenderParticipantIdAndClientMessageId(id,p.id,clientId);if(existing.isPresent())return new Saved(dto(existing.get(),p,attachments.findByMessageId(existing.get().id).orElse(null)),false);
  var m=new ChatMessage();m.conversationId=id;m.senderParticipantId=p.id;m.clientMessageId=clientId;m.messageType=kind;m.content=text;messages.saveAndFlush(m);
  if(attachment!=null){attachment.messageId=m.id;attachments.saveAndFlush(attachment);}return new Saved(dto(m,p,attachment),true);
 }
 @Transactional(readOnly=true) public Optional<Map<String,Object>> existing(Actor actor,String id,String clientId){var p=member(actor,id,true);return messages.findByConversationIdAndSenderParticipantIdAndClientMessageId(id,p.id,clientId).map(m->dto(m,p,attachments.findByMessageId(m.id).orElse(null)));}
 @Transactional public Map<String,Object> receipt(Actor actor,String id,String messageId,String status){var p=member(actor,id,false);var m=messages.findById(messageId).orElseThrow(ConversationService::notFound);if(!m.conversationId.equals(id)||m.senderParticipantId.equals(p.id))throw notFound();if(status.equals("READ")||m.status.equals("SENT"))m.status=status;return Map.of("messageId",m.id,"status",m.status);}
 @Transactional(readOnly=true) public Map<String,Object> history(Actor actor,int page,int size){requireUser(actor);var result=conversations.history(actor.id(),PageRequest.of(Math.max(0,page),Math.min(50,Math.max(1,size))));var ids=result.getContent().stream().map(c->c.id).toList();var members=ids.isEmpty()?List.<Participant>of():participants.inConversations(ids);var names=users.findAllById(members.stream().map(p->p.userId).filter(Objects::nonNull).distinct().toList()).stream().collect(Collectors.toMap(u->u.id,u->u.displayName));var items=result.getContent().stream().map(c->{var out=summary(c);out.put("participants",members.stream().filter(p->p.conversationId.equals(c.id)).map(p->Map.of("id",p.userId==null?p.guestSessionId:p.userId,"displayName",p.userId==null?"Guest":names.getOrDefault(p.userId,"User"))).toList());return out;}).toList();return Map.of("items",items,"page",result.getNumber(),"totalPages",result.getTotalPages(),"totalElements",result.getTotalElements());}
 @Transactional(readOnly=true) public Map<String,Object> detail(Actor actor,String id){member(actor,id,false);return summary(conversations.findById(id).orElseThrow(ConversationService::notFound));}
 private LinkedHashMap<String,Object> summary(Conversation c){var out=new LinkedHashMap<>(policy(c));out.put("id",c.id);out.put("conversationType",c.conversationType);out.put("status",c.status);out.put("startedAt",c.startedAt);out.put("endedAt",c.endedAt);return out;}
 @Transactional(readOnly=true) public Map<String,Object> page(Actor actor,String id,int page,int size){member(actor,id,false);var result=messages.findByConversationIdOrderByCreatedAtDescIdDesc(id,PageRequest.of(Math.max(0,page),Math.min(50,Math.max(1,size))));var members=participants.findByConversationId(id).stream().collect(Collectors.toMap(p->p.id,p->p));var ids=result.getContent().stream().map(m->m.id).toList();var assets=ids.isEmpty()?Map.<String,MessageAttachment>of():attachments.forMessages(ids).stream().collect(Collectors.toMap(a->a.messageId,a->a));return Map.of("items",result.getContent().stream().map(m->dto(m,members.get(m.senderParticipantId),assets.get(m.id))).toList(),"page",result.getNumber(),"totalPages",result.getTotalPages());}
 @Transactional public void hide(Actor actor,String id){requireUser(actor);var p=member(actor,id,false);var c=conversations.findById(id).orElseThrow(ConversationService::notFound);if(!c.persistentHistory||c.status.equals("ACTIVE"))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only ended account conversations can be hidden");p.hiddenAt=Instant.now();}
 private void requireUser(Actor actor){if(!actor.isUser())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Registered account required");}
 @Transactional(readOnly=true)
 public Map<String,Object> window(Actor actor,String id,String before,String after) {
  member(actor,id,false);
  if(before!=null && after!=null)throw new IllegalArgumentException("Choose one cursor");
  var limit=PageRequest.of(0,50);
  List<ChatMessage> batch;
  String cursor=after!=null?after:before;
  if(cursor==null)batch=messages.findByConversationIdOrderByCreatedAtDescIdDesc(id,limit).getContent();
  else {
   var anchor=messages.findById(cursor).orElseThrow(ConversationService::notFound);
   if(!id.equals(anchor.conversationId))throw notFound();
   batch=after!=null?messages.after(id,anchor.createdAt,anchor.id,limit):messages.before(id,anchor.createdAt,anchor.id,limit);
  }
  var members=participants.findByConversationId(id).stream().collect(Collectors.toMap(p->p.id,p->p));
  var ids=batch.stream().map(m->m.id).toList();
  var assets=ids.isEmpty()?Map.<String,MessageAttachment>of():attachments.forMessages(ids).stream().collect(Collectors.toMap(a->a.messageId,a->a));
  return Map.of("items",batch.stream().map(m->dto(m,members.get(m.senderParticipantId),assets.get(m.id))).toList(),"hasMore",batch.size()==50);
 }
 private Map<String,Object> dto(ChatMessage m,Participant p,MessageAttachment a){var out=new LinkedHashMap<String,Object>();out.put("messageId",m.id);out.put("clientMessageId",m.clientMessageId);out.put("senderId",p.userId==null?p.guestSessionId:p.userId);out.put("messageType",m.messageType);out.put("text",m.content);out.put("sentAt",m.createdAt);out.put("status",m.status);if(a!=null){var asset=new LinkedHashMap<String,Object>();asset.put("id",a.id);asset.put("name",a.originalFilename);asset.put("mimeType",a.mimeType);asset.put("size",a.fileSize);asset.put("durationSeconds",a.durationSeconds);out.put("attachment",asset);}return out;}
 public static ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Conversation or attachment not found");}
}
