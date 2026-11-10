package com.randomconnect.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="guest_sessions")
public class GuestIdentity extends BaseEntity {
 @Column(nullable=false,unique=true,length=64) public String guestTokenHash;
 @Column(nullable=false) public Instant expiresAt;
 @Column(nullable=false) public Instant lastActiveAt=Instant.now();
 @Column(nullable=false,length=20) public String status="ACTIVE";
}
