package com.randomconnect.persistence;

import jakarta.persistence.*;

@Entity
@Table(name = "social_connections")
public class SocialConnection extends BaseEntity {
 @Column(nullable = false, length = 36) public String firstUserId;
 @Column(nullable = false, length = 36) public String secondUserId;
 @Column(length = 36) public String requesterId;
 @Column(nullable = false, length = 20) public String friendshipStatus = "NONE";
 @Column(length = 36, unique = true) public String conversationId;

 public String peer(String userId) {
  return firstUserId.equals(userId) ? secondUserId : firstUserId;
 }
}
