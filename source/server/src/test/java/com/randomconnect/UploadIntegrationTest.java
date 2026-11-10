package com.randomconnect;
import com.randomconnect.auth.*;
import com.randomconnect.persistence.*;
import com.randomconnect.service.*;
import com.randomconnect.storage.*;
import com.randomconnect.dto.SocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.socket.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UploadIntegrationTest.FakeStorageConfiguration.class)
class UploadIntegrationTest {
 static class MemoryStorage implements StorageService {final Map<String,byte[]> objects=new java.util.concurrent.ConcurrentHashMap<>();Runnable afterPut;public void put(String k,byte[] b,String type){objects.put(k,b);if(afterPut!=null){var action=afterPut;afterPut=null;action.run();}}public void delete(String k){objects.remove(k);}public String signedGet(String k,String name,String type){return "https://test-storage.invalid/"+k;}}
 @TestConfiguration static class FakeStorageConfiguration {@Bean @Primary MemoryStorage fakeStorage(){return new MemoryStorage();}}
 @Autowired MemoryStorage storage;@Autowired IdentityService identities;@Autowired MatchmakingService engine;@Autowired UploadService uploads;@Autowired ConversationRepository conversations;@Autowired RetentionService retention;@Autowired ObjectMapper json;
 final List<String> socketIds=new ArrayList<>();String first;Actor a,b;String room;
 @BeforeEach void pair()throws Exception {var ga=identities.createGuest();var gb=identities.createGuest();a=new Actor("GUEST",ga.guestId(),null);b=new Actor("GUEST",gb.guestId(),null);first=open(a);String second=open(b);engine.accept(first,new SocketMessage("FIND_PARTNER",null,null,json.createObjectNode()));engine.accept(second,new SocketMessage("FIND_PARTNER",null,null,json.createObjectNode()));room=conversations.findByStatus("ACTIVE").stream().filter(c->!c.persistentHistory).map(c->c.id).filter(id->{try{var participants=this.participants.findByConversationId(id);return participants.stream().anyMatch(p->a.id().equals(p.guestSessionId));}catch(Exception e){return false;}}).findFirst().orElseThrow();}
 @Autowired ParticipantRepository participants;
 String open(Actor actor)throws Exception {String id=UUID.randomUUID().toString();var ws=mock(WebSocketSession.class);when(ws.getId()).thenReturn(id);when(ws.isOpen()).thenReturn(true);when(ws.getAttributes()).thenReturn(Map.of("actor",actor));engine.open(ws);socketIds.add(id);return id;}
 @AfterEach void cleanup(){storage.afterPut=null;for(String id:socketIds)engine.disconnect(id);}
 MockMultipartFile text(){return new MockMultipartFile("file","hello.txt","text/plain","hello attachment".getBytes(StandardCharsets.UTF_8));}
 @Test void uploadDeduplicatesAndDownloadRejectsOutsider()throws Exception {String client=UUID.randomUUID().toString();var sent=uploads.upload(a,room,text(),client,false);int count=storage.objects.size();assertThat(uploads.upload(a,room,text(),client,false).get("messageId")).isEqualTo(sent.get("messageId"));assertThat(storage.objects.size()).isEqualTo(count);String id=(String)((Map<?,?>)sent.get("attachment")).get("id");assertThat(uploads.download(b,id).get("url").toString()).startsWith("https://test-storage.invalid/");var outsider=identities.createGuest();assertThatThrownBy(()->uploads.download(new Actor("GUEST",outsider.guestId(),null),id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void nextDuringUploadDeletesUnattachedObject() {int before=storage.objects.size();storage.afterPut=()->engine.accept(first,new SocketMessage("NEXT_PARTNER",null,room,json.createObjectNode()));assertThatThrownBy(()->uploads.upload(a,room,text(),UUID.randomUUID().toString(),false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(storage.objects.size()).isEqualTo(before);}
 @Test void expiredGuestConversationAndObjectsAreRemoved()throws Exception {var sent=uploads.upload(a,room,text(),UUID.randomUUID().toString(),false);String key=storage.objects.keySet().stream().filter(k->k.contains(room)).findFirst().orElseThrow();var c=conversations.findById(room).orElseThrow();c.expiresAt=java.time.Instant.now().minusSeconds(1);conversations.saveAndFlush(c);retention.cleanup();assertThat(conversations.existsById(room)).isFalse();assertThat(storage.objects).doesNotContainKey(key);}
 @Autowired AttachmentRepository attachmentRepo;
 @Test void quotaAndOutsiderCannotUpload()throws Exception {
  var outsider=identities.createGuest();int before=storage.objects.size();assertThatThrownBy(()->uploads.upload(new Actor("GUEST",outsider.guestId(),null),room,text(),UUID.randomUUID().toString(),false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);assertThat(storage.objects.size()).isEqualTo(before);
  var sent=uploads.upload(a,room,text(),UUID.randomUUID().toString(),false);String id=(String)((Map<?,?>)sent.get("attachment")).get("id");var attachment=attachmentRepo.findById(id).orElseThrow();attachment.fileSize=52428800;attachmentRepo.saveAndFlush(attachment);
  assertThatThrownBy(()->uploads.upload(a,room,text(),UUID.randomUUID().toString(),false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("quota");
 }
 @Test void voiceUploadsAndCanBeReadByPartner()throws Exception {
  try{var process=new ProcessBuilder("ffprobe","-version").start();org.junit.jupiter.api.Assumptions.assumeTrue(process.waitFor()==0);}catch(Exception e){org.junit.jupiter.api.Assumptions.assumeTrue(false,"ffprobe not installed");}
  var file=new MockMultipartFile("file","voice.wav","audio/wav",UploadSecurityTest.wave(1));var sent=uploads.upload(a,room,file,UUID.randomUUID().toString(),true);assertThat(sent.get("messageType")).isEqualTo("AUDIO");var asset=(Map<?,?>)sent.get("attachment");assertThat((Double)asset.get("durationSeconds")).isBetween(.99,1.01);assertThat(uploads.download(b,(String)asset.get("id"))).containsKey("url");
 }
}
