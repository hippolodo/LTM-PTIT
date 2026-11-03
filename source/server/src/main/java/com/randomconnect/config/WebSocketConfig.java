package com.randomconnect.config;
import com.randomconnect.websocket.ChatWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
@Configuration @EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer,WebMvcConfigurer {
 private final ChatWebSocketHandler handler; private final String[] origins;
 public WebSocketConfig(ChatWebSocketHandler handler,@Value("${randomconnect.allowed-origins}") String origins) {this.handler=handler;this.origins=origins.split(",");}
 @Bean public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean socketContainer() {
  var bean=new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();bean.setMaxTextMessageBufferSize(131072);return bean;
 }
 @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {registry.addHandler(handler,"/ws").setAllowedOrigins(origins);}
 @Override public void addCorsMappings(CorsRegistry registry) {registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET");}
}
