package com.randomconnect.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ConversationRepository extends JpaRepository<Conversation,String> {
 java.util.List<Conversation> findTop100ByPersistentHistoryFalseAndExpiresAtBefore(java.time.Instant now);
 @Query("select c from Conversation c where c.persistentHistory=true and exists (select p.id from Participant p where p.conversationId=c.id and p.userId=:user and p.hiddenAt is null) order by c.startedAt desc,c.id desc")
 org.springframework.data.domain.Page<Conversation> history(@Param("user") String user,org.springframework.data.domain.Pageable page);
 java.util.List<Conversation> findByStatus(String status);
 @Query("select c from Conversation c where c.status='CLOSED' and c.persistentHistory=true and not exists (select p.id from Participant p where p.conversationId=c.id and p.hiddenAt is null)") java.util.List<Conversation> hiddenByAll();
}
