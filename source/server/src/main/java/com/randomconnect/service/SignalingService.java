package com.randomconnect.service;
import com.randomconnect.dto.SocketMessage;
import com.fasterxml.jackson.databind.JsonNode;
public class SignalingService {
 public void validate(SocketMessage m) {
  var p=m.payload();if(p==null || !p.isObject())invalid();
  if(m.type().equals("ICE_CANDIDATE")) {
   var c=p.get("candidate");if(c==null || !c.isObject())invalid();
   requireText(c,"candidate",4096);
   if(c.has("sdpMid") && !c.get("sdpMid").isNull() && (!c.get("sdpMid").isTextual() || c.get("sdpMid").asText().length()>100))invalid();
   if(c.has("sdpMLineIndex") && !c.get("sdpMLineIndex").isNull() && (!c.get("sdpMLineIndex").isInt() || c.get("sdpMLineIndex").asInt()<0))invalid();
  } else {
   var d=p.get("sdp");if(d==null || !d.isObject())invalid();
   String expected=m.type().equals("WEBRTC_OFFER")?"offer":"answer";
   if(!expected.equals(d.path("type").asText()))invalid();requireText(d,"sdp",48000);
  }
 }
 private void requireText(JsonNode n,String field,int max) {var v=n.get(field);if(v==null || !v.isTextual() || v.asText().isBlank() || v.asText().length()>max)invalid();}
 private void invalid() {throw new IllegalArgumentException("INVALID_MESSAGE");}
}
