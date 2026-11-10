package com.randomconnect.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="messages")
public class ChatMessage extends BaseEntity {
 @Column(nullable=false,length=36) public String conversationId;
 @Column(nullable=false,length=36) public String senderParticipantId;
 @Column(nullable=false,length=64) public String clientMessageId;
 @Column(nullable=false,length=20) public String messageType="TEXT";
 @Column(length=4000) public String content;
 public Instant editedAt;
 public Instant deletedAt;
 @Column(nullable=false,length=20) public String status="SENT";
}
