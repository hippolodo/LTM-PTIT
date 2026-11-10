package com.randomconnect.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AttachmentRepository extends JpaRepository<MessageAttachment,String> {
 java.util.Optional<MessageAttachment> findByMessageId(String message);
 @Query("select a from MessageAttachment a where a.messageId in :ids") java.util.List<MessageAttachment> forMessages(@Param("ids") java.util.Collection<String> ids);
 @Query("select a from MessageAttachment a,ChatMessage m where a.messageId=m.id and m.conversationId=:id") java.util.List<MessageAttachment> forConversation(@Param("id") String id);
 @Query("select coalesce(sum(a.fileSize),0) from MessageAttachment a,ChatMessage m where a.messageId=m.id and m.senderParticipantId=:id") long bytesByParticipant(@Param("id") String id);
}
