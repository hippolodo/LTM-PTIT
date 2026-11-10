package com.randomconnect.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ParticipantRepository extends JpaRepository<Participant,String> {
 java.util.List<Participant> findByConversationId(String conversation);
 @Query("select p from Participant p where p.conversationId in :ids") java.util.List<Participant> inConversations(@Param("ids") java.util.Collection<String> ids);
 void deleteByConversationId(String conversation);
 boolean existsByGuestSessionId(String id);
}
