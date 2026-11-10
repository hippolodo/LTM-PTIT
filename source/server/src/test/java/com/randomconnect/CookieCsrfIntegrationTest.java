package com.randomconnect;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.*;
// Isolated from MockMvc csrf() postprocessors, which replace the filter's repository.
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="randomconnect.csrf-http-test=true")
class CookieCsrfIntegrationTest {
 @LocalServerPort int port;
 @Autowired ObjectMapper json;
 @Test void realCookieCsrfFlow()throws Exception {
  var client=java.net.http.HttpClient.newBuilder().cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL)).build();var uri=java.net.URI.create("http://localhost:"+port+"/api/auth/csrf");
  var response=client.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isEqualTo(200);assertThat(response.headers().allValues("Set-Cookie")).anySatisfy(v->assertThat(v).contains("XSRF-TOKEN=","HttpOnly"));var body=json.readTree(response.body());
  var guestUri=java.net.URI.create("http://localhost:"+port+"/api/auth/guest");var missing=client.send(java.net.http.HttpRequest.newBuilder(guestUri).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(missing.statusCode()).isEqualTo(403);
  var accepted=client.send(java.net.http.HttpRequest.newBuilder(guestUri).header(body.path("headerName").asText(),body.path("token").asText()).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.ofString());assertThat(accepted.statusCode()).isEqualTo(200);
 }
}
