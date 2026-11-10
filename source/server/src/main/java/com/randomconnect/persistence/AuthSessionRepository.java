package com.randomconnect.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface AuthSessionRepository extends JpaRepository<AuthSession,String> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select s from AuthSession s where s.id=:id") java.util.Optional<AuthSession> locked(@Param("id") String id);
 java.util.List<AuthSession> findTop100ByExpiresAtBefore(java.time.Instant now);
}
