package com.randomconnect.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="conversations")
public class Conversation extends BaseEntity {
 @Column(nullable=false,length=20) public String conversationType="RANDOM";
 @Column(nullable=false,length=20) public String status="ACTIVE";
 @Column(nullable=false) public boolean persistentHistory;
 @Column(nullable=false) public Instant startedAt=Instant.now();
 public Instant endedAt;
 public Instant expiresAt;
}
