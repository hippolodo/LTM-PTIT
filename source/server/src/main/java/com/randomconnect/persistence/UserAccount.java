package com.randomconnect.persistence;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="users")
public class UserAccount extends BaseEntity {
 @Column(nullable=false,unique=true,length=32) public String username;
 @Column(nullable=false,unique=true,length=254) public String email;
 @Column(nullable=false,length=100) public String passwordHash;
 @Column(nullable=false,length=80) public String displayName;
 @Column(length=500) public String avatarUrl;
 @Column(nullable=false,length=20) public String role="USER";
 @Column(nullable=false,length=20) public String status="ACTIVE";
 @Column(nullable=false) public Instant updatedAt=Instant.now();
 public Instant lastLoginAt;
}
