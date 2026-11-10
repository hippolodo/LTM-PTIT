package com.randomconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.randomconnect.auth.*;
import com.randomconnect.persistence.*;
import com.randomconnect.service.*;
import com.randomconnect.storage.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(DirectUploadIntegrationTest.StorageTestConfig.class)
class DirectUploadIntegrationTest {
 static class MemoryStorage implements StorageService {
  final Map<String,byte[]> objects=new ConcurrentHashMap<>();
  volatile Runnable afterPut;
  public void put(String key,byte[] bytes,String type) { objects.put(key,bytes);if(afterPut!=null)afterPut.run(); }
  public void delete(String key) { objects.remove(key); }
  public String signedGet(String key,String name,String type) { return "https://storage.invalid/"+key; }
 }
 @TestConfiguration static class StorageTestConfig {
  @Bean @Primary MemoryStorage memoryStorage(){return new MemoryStorage();}
 }
 @Autowired MemoryStorage storage;
 @Autowired UploadService uploads;
 @Autowired SocialService social;
 @Autowired AuthService auth;
 @Autowired IdentityService identities;
 @Autowired AttachmentRepository attachments;
 @Autowired ConversationRepository conversations;
 @Autowired ParticipantRepository participants;
 @Autowired MessageRepository messages;
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 AuthService.LoginGrant ga,gb;Actor a,b;String room;

 AuthService.LoginGrant user(){String name="upload"+UUID.randomUUID().toString().replace("-", "").substring(0,12);return auth.register(name,name+"@example.invalid","Password123!",name);}
 Actor actor(AuthService.LoginGrant grant){return new Actor("USER",grant.user().id(),null);}
 MockMultipartFile text(){return new MockMultipartFile("file","hello.txt","text/plain","hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 @BeforeEach void setup(){ga=user();gb=user();a=actor(ga);b=actor(gb);room=(String)social.open(a,b.id()).get("id");storage.afterPut=null;}
 @AfterEach void finish(){storage.afterPut=null;}
 String attachmentId(Map<String,Object> sent){return (String)((Map<?,?>)sent.get("attachment")).get("id");}
 long objects(){return storage.objects.keySet().stream().filter(k->k.contains(room)).count();}

 @Test void httpUploadWithoutSocketReachesPartnerAndRejectsOutsiderAndGuest()throws Exception {
  String client=UUID.randomUUID().toString();
  var result=mvc.perform(multipart("/api/conversations/"+room+"/attachments").file(text())
    .param("clientMessageId",client).header("Authorization","Bearer "+ga.accessToken()))
   .andExpect(status().isOk()).andExpect(jsonPath("messageType").value("FILE")).andReturn();
  var sent=json.readTree(result.getResponse().getContentAsString());String id=sent.path("attachment").path("id").asText();
  mvc.perform(get("/api/attachments/"+id+"/download-url").header("Authorization","Bearer "+gb.accessToken())).andExpect(status().isOk());
  assertThat((List<?>)social.messages(b,room,null,null).get("items")).hasSize(1);
  var outsider=user();
  mvc.perform(multipart("/api/conversations/"+room+"/attachments").file(text()).param("clientMessageId",UUID.randomUUID().toString()).header("Authorization","Bearer "+outsider.accessToken())).andExpect(status().isNotFound());
  mvc.perform(get("/api/attachments/"+id+"/download-url").header("Authorization","Bearer "+outsider.accessToken())).andExpect(status().isNotFound());
  var guest=identities.createGuest();
  mvc.perform(multipart("/api/conversations/"+room+"/attachments").file(text()).param("clientMessageId",UUID.randomUUID().toString()).header("Authorization","Guest "+guest.token())).andExpect(status().isForbidden());
  assertThat(objects()).isEqualTo(1);
 }

 @Test void retryAtQuotaReturnsExistingButNewUploadIsRejected()throws Exception {
  String client=UUID.randomUUID().toString();var sent=uploads.upload(a,room,text(),client,false);
  var attachment=attachments.findById(attachmentId(sent)).orElseThrow();attachment.fileSize=52428800;attachments.saveAndFlush(attachment);
  assertThat(uploads.upload(a,room,text(),client,false).get("messageId")).isEqualTo(sent.get("messageId"));
  assertThatThrownBy(()->uploads.upload(a,room,text(),UUID.randomUUID().toString(),false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("quota");
  assertThat(objects()).isEqualTo(1);
 }

 @Test void conversationEndingDuringPutRemovesUnattachedObject() {
  storage.afterPut=()->{var c=conversations.findById(room).orElseThrow();c.status="CLOSED";conversations.saveAndFlush(c);};
  assertThatThrownBy(()->uploads.upload(a,room,text(),UUID.randomUUID().toString(),false)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  assertThat(objects()).isZero();assertThat(messages.findByConversationIdOrderByCreatedAtDescIdDesc(room,org.springframework.data.domain.PageRequest.of(0,10))).isEmpty();
 }

 @Test void concurrentUploadsCannotExceedParticipantQuota()throws Exception {
  var sent=uploads.upload(a,room,text(),UUID.randomUUID().toString(),false);
  var attachment=attachments.findById(attachmentId(sent)).orElseThrow();attachment.fileSize=52428800-5;attachments.saveAndFlush(attachment);
  var barrier=new CyclicBarrier(2);
  storage.afterPut=()->{try{barrier.await(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException(e);}};
  try(var pool=Executors.newFixedThreadPool(2)){
   java.util.concurrent.Callable<Boolean> task=()->{try{uploads.upload(a,room,text(),UUID.randomUUID().toString(),false);return true;}catch(org.springframework.web.server.ResponseStatusException e){assertThat(e.getStatusCode().value()).isEqualTo(429);return false;}};
   var first=pool.submit(task);var second=pool.submit(task);
   assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
  }
  var participant=participants.findByConversationId(room).stream().filter(p->a.id().equals(p.userId)).findFirst().orElseThrow();
  assertThat(attachments.bytesByParticipant(participant.id)).isEqualTo(52428800);
  assertThat(objects()).isEqualTo(2);
 }

 @Test void directVoiceUsesProbeAndIsPlayableByPartner()throws Exception {
  try{var process=new ProcessBuilder("ffprobe","-version").start();Assumptions.assumeTrue(process.waitFor()==0);}catch(Exception e){Assumptions.assumeTrue(false,"ffprobe unavailable");}
  var file=new MockMultipartFile("file","voice.wav","audio/wav",UploadSecurityTest.wave(1));
  var sent=uploads.upload(a,room,file,UUID.randomUUID().toString(),true);
  assertThat(sent.get("messageType")).isEqualTo("AUDIO");
  assertThat((Double)((Map<?,?>)sent.get("attachment")).get("durationSeconds")).isBetween(.99,1.01);
  assertThat(uploads.download(b,attachmentId(sent))).containsKey("url");
 }
}
