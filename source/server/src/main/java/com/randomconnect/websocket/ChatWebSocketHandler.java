package com.randomconnect.websocket;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.randomconnect.dto.SocketMessage;
import com.randomconnect.service.MatchmakingService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
 private final MatchmakingService service; private final ObjectMapper mapper;
 public ChatWebSocketHandler(MatchmakingService service,ObjectMapper mapper) {this.service=service;this.mapper=mapper;}
 @Override public void afterConnectionEstablished(WebSocketSession s) {s.setTextMessageSizeLimit(131072);service.open(s);}
 @Override protected void handleTextMessage(WebSocketSession s,TextMessage text) {
  String request=null;
  try {
   if(text.getPayloadLength()>65536)throw new IllegalArgumentException();
   var m=mapper.readValue(text.getPayload(),SocketMessage.class);request=m.requestId();
   if(m.type()==null || m.type().length()>40 || (request!=null && request.length()>100) || (m.roomId()!=null && m.roomId().length()>100))throw new IllegalArgumentException();
   service.accept(s.getId(),m);
  } catch(Exception e) {service.error(s.getId(),request,"INVALID_MESSAGE");}
 }
 @Override public void afterConnectionClosed(WebSocketSession s,CloseStatus status) {service.disconnect(s.getId());}
 @Override public void handleTransportError(WebSocketSession s,Throwable error) throws Exception {service.disconnect(s.getId());s.close(CloseStatus.SERVER_ERROR);}
}
