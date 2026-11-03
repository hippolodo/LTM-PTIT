package com.randomconnect.config;
import com.randomconnect.websocket.ChatWebSocketHandler;
import java.net.URI;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
@Configuration @EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer,WebMvcConfigurer {
 private final ChatWebSocketHandler handler; private final String[] origins;
 public WebSocketConfig(ChatWebSocketHandler handler,@Value("${randomconnect.allowed-origins}") String origins) {
  this.handler=handler;
  this.origins=Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).distinct().toArray(String[]::new);
  if(this.origins.length==0)throw new IllegalArgumentException("ALLOWED_ORIGINS must contain at least one explicit HTTP(S) origin");
  for(String origin:this.origins) {
   URI uri=URI.create(origin);
   if(!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost()==null || uri.getRawUserInfo()!=null || (uri.getRawPath()!=null && !uri.getRawPath().isEmpty()) || uri.getRawQuery()!=null || uri.getRawFragment()!=null || origin.contains("*"))
    throw new IllegalArgumentException("ALLOWED_ORIGINS must contain explicit HTTP(S) origins without paths or wildcards");
  }
 }
 @Bean public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean socketContainer() {
  var bean=new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();bean.setMaxTextMessageBufferSize(131072);return bean;
 }
 @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {registry.addHandler(handler,"/ws").setAllowedOrigins(origins);}
 @Override public void addCorsMappings(CorsRegistry registry) {registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET");}
}
