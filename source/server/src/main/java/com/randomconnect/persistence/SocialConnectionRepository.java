package com.randomconnect.persistence;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SocialConnectionRepository extends JpaRepository<SocialConnection, String> {
 Optional<SocialConnection> findByFirstUserIdAndSecondUserId(String first, String second);
 Optional<SocialConnection> findByConversationId(String conversation);

 @Query("select s from SocialConnection s where s.firstUserId=:user or s.secondUserId=:user")
 List<SocialConnection> forUser(@Param("user") String user);
}
