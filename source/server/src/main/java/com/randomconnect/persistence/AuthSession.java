package com.randomconnect.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="auth_sessions")
public class AuthSession extends BaseEntity {
 @Column(nullable=false,length=36) public String userId;
 @Column(nullable=false,length=64) public String refreshTokenHash;
 @Column(nullable=false) public Instant expiresAt;
 public Instant revokedAt;
 @Column(nullable=false) public Instant rotatedAt=Instant.now();
}
