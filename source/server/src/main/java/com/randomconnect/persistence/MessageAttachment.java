package com.randomconnect.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="message_attachments")
public class MessageAttachment extends BaseEntity {
 @Column(nullable=false,unique=true,length=36) public String messageId;
 @Column(nullable=false,length=180) public String originalFilename;
 @Column(nullable=false,unique=true,length=500) public String storageKey;
 @Column(nullable=false,length=100) public String mimeType;
 @Column(nullable=false) public long fileSize;
 @Column(nullable=false,length=20) public String attachmentType;
 public Double durationSeconds;
}
