package com.randomconnect.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface MessageRepository extends JpaRepository<ChatMessage,String> {
 org.springframework.data.domain.Page<ChatMessage> findByConversationIdOrderByCreatedAtDescIdDesc(String id,org.springframework.data.domain.Pageable page);
 java.util.Optional<ChatMessage> findByConversationIdAndSenderParticipantIdAndClientMessageId(String conversation,String participant,String clientId);
 void deleteByConversationId(String conversation);
 @org.springframework.data.jpa.repository.Query("select m from ChatMessage m where m.conversationId=:conversation and (m.createdAt>:time or (m.createdAt=:time and m.id>:cursor)) order by m.createdAt,m.id")
 java.util.List<ChatMessage> after(@org.springframework.data.repository.query.Param("conversation") String conversation, @org.springframework.data.repository.query.Param("time") java.time.Instant time, @org.springframework.data.repository.query.Param("cursor") String cursor, org.springframework.data.domain.Pageable page);
 @org.springframework.data.jpa.repository.Query("select m from ChatMessage m where m.conversationId=:conversation and (m.createdAt<:time or (m.createdAt=:time and m.id<:cursor)) order by m.createdAt desc,m.id desc")
 java.util.List<ChatMessage> before(@org.springframework.data.repository.query.Param("conversation") String conversation, @org.springframework.data.repository.query.Param("time") java.time.Instant time, @org.springframework.data.repository.query.Param("cursor") String cursor, org.springframework.data.domain.Pageable page);
}
