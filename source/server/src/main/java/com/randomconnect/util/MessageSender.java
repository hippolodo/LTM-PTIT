package com.randomconnect.util;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
@Component
public class MessageSender {
 private final ObjectMapper mapper;
 public MessageSender(ObjectMapper mapper) {this.mapper=mapper;}
 public boolean send(WebSocketSession socket,Object value) {
  try { if(!socket.isOpen()) return false; socket.sendMessage(new TextMessage(mapper.writeValueAsString(value))); return true; }
  catch(Exception ex) { try {socket.close(CloseStatus.SERVER_ERROR);} catch(Exception ignored) {} return false; }
 }
}
