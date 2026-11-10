package com.randomconnect.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="conversation_participants")
public class Participant extends BaseEntity {
 @Column(nullable=false,length=36) public String conversationId;
 @Column(length=36) public String userId;
 @Column(length=36) public String guestSessionId;
 @Column(nullable=false) public Instant joinedAt=Instant.now();
 public Instant leftAt;
 public Instant hiddenAt;
}
