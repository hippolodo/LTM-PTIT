package com.randomconnect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("render")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
 "ALLOWED_ORIGINS=https://randomconnect-test.vercel.app, https://randomconnect-preview.vercel.app",
 "logging.level.root=WARN", "debug=false"
})
class RenderProfileIntegrationTest {
 private static final String TRUSTED="https://randomconnect-test.vercel.app";
 @LocalServerPort int port;
 @Autowired Environment environment;
 @Autowired ObjectMapper mapper;
 private final HttpClient http=HttpClient.newHttpClient();

 @AfterEach void releaseHttpClient() {http.shutdownNow();}

 private HttpResponse<String> health(String origin) throws Exception {
  var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/health"))
      .header("Origin",origin).GET().build();
  return http.send(request,HttpResponse.BodyHandlers.ofString());
 }

 @Test void renderProfileBindsPubliclyAndKeepsHealthAndExactCors() throws Exception {
  assertThat(environment.getProperty("server.address")).isEqualTo("0.0.0.0");
  assertThat(environment.getProperty("server.tomcat.threads.max",Integer.class)).isEqualTo(40);
  for(String origin:new String[]{TRUSTED,"https://randomconnect-preview.vercel.app"}) {
   var response=health(origin);
   assertThat(response.statusCode()).isEqualTo(200);
   assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(origin);
   assertThat(mapper.readTree(response.body()).path("status").asText()).isEqualTo("UP");
  }
 }

 @Test void renderProfileRejectsUnlistedAndLocalhostCors() throws Exception {
  for(String origin:new String[]{"https://untrusted.invalid","http://localhost:5173"}) {
   var response=health(origin);
   assertThat(response.statusCode()).isEqualTo(403);
   assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }
 }

 @Test void trustedWebSocketReceivesSessionReadyOnExistingEndpoint() throws Exception {
  var received=new CompletableFuture<JsonNode>();
  var socket=http.newWebSocketBuilder().header("Origin",TRUSTED)
      .buildAsync(URI.create("ws://localhost:"+port+"/ws"),new WebSocket.Listener() {
       private final StringBuilder buffer=new StringBuilder();
       @Override public void onOpen(WebSocket ws) {ws.request(1);}
       @Override public CompletionStage<?> onText(WebSocket ws,CharSequence text,boolean last) {
        buffer.append(text);
        if(last)try {received.complete(mapper.readTree(buffer.toString()));}catch(Exception error) {received.completeExceptionally(error);}
        ws.request(1);return CompletableFuture.completedFuture(null);
       }
      }).get(8,TimeUnit.SECONDS);
  try {assertThat(received.get(8,TimeUnit.SECONDS).path("type").asText()).isEqualTo("SESSION_READY");}
  finally {socket.sendClose(1000,"test complete").join();}
 }

 @Test void untrustedWebSocketIsRejectedBeforeUpgrade() {
  assertThatThrownBy(()->http.newWebSocketBuilder().header("Origin","https://untrusted.invalid")
      .buildAsync(URI.create("ws://localhost:"+port+"/ws"),new WebSocket.Listener() {}).join())
      .isInstanceOf(CompletionException.class);
 }
}
