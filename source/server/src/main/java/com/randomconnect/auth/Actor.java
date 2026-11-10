package com.randomconnect.auth;
public record Actor(String kind,String id,String sessionId) {
 public boolean isUser(){return "USER".equals(kind);}
 public boolean sameIdentity(Actor other){return kind.equals(other.kind)&&id.equals(other.id);}
}
