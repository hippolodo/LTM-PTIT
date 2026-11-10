package com.randomconnect.persistence;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@MappedSuperclass
public abstract class BaseEntity {
 @Id @Column(length=36) public String id=UUID.randomUUID().toString();
 @Column(nullable=false,updatable=false) public Instant createdAt=Instant.now();
}
