package com.randomconnect.model;
import java.time.Instant;
import java.util.UUID;
public record ChatRoom(String id, String first, String second, Instant createdAt) {
 public ChatRoom(String first,String second) { this(UUID.randomUUID().toString(),first,second,Instant.now()); }
 public String peer(String member) { if(first.equals(member)) return second; if(second.equals(member)) return first; throw new IllegalArgumentException("NOT_IN_ROOM"); }
}
