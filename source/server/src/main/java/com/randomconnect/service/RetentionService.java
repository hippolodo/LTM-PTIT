package com.randomconnect.service;
import com.randomconnect.persistence.*;
import com.randomconnect.auth.*;
import com.randomconnect.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.util.*;
@Service
public class RetentionService {
 private final ConversationRepository conversations;private final ParticipantRepository participants;private final MessageRepository messages;private final AttachmentRepository attachments;private final GuestRepository guests;private final AuthSessionRepository sessions;private final StorageService storage;private final TransactionTemplate tx;private final ApplicationEventPublisher events;
 public RetentionService(ConversationRepository c,ParticipantRepository p,MessageRepository m,AttachmentRepository a,GuestRepository g,AuthSessionRepository s,StorageService storage,PlatformTransactionManager manager,ApplicationEventPublisher e){conversations=c;participants=p;messages=m;attachments=a;guests=g;sessions=s;this.storage=storage;tx=new TransactionTemplate(manager);events=e;}
 public void reconcile(){tx.executeWithoutResult(status->{for(var c:conversations.findByStatus("ACTIVE")){if("DIRECT".equals(c.conversationType))continue;c.status="CLOSED";c.endedAt=Instant.now();for(var p:participants.findByConversationId(c.id))p.leftAt=Instant.now();}});}
 @Scheduled(fixedDelayString="${RETENTION_SCAN_MS:60000}",initialDelay=60000)
 public void cleanup(){
  var expired=new ArrayList<>(conversations.findTop100ByPersistentHistoryFalseAndExpiresAtBefore(Instant.now()));expired.addAll(conversations.hiddenByAll());
  for(var c:expired)try{tx.executeWithoutResult(status->{for(var a:attachments.forConversation(c.id)){storage.delete(a.storageKey);attachments.delete(a);}attachments.flush();messages.deleteByConversationId(c.id);messages.flush();participants.deleteByConversationId(c.id);participants.flush();conversations.deleteById(c.id);});}catch(Exception ignored){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Retention cleanup deferred; database or storage unavailable");}
  for(var s:sessions.findTop100ByExpiresAtBefore(Instant.now()))try{tx.executeWithoutResult(status->{events.publishEvent(new SessionRevoked(s.id));sessions.deleteById(s.id);});}catch(Exception ignored){}
  for(var g:guests.findTop100ByExpiresAtBefore(Instant.now()))try{tx.executeWithoutResult(status->{if(!participants.existsByGuestSessionId(g.id))guests.deleteById(g.id);});}catch(Exception ignored){}
 }
}
