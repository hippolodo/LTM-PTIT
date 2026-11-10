package com.randomconnect.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface GuestRepository extends JpaRepository<GuestIdentity,String> {
 java.util.Optional<GuestIdentity> findByGuestTokenHash(String hash);
 java.util.List<GuestIdentity> findTop100ByExpiresAtBefore(java.time.Instant now);
}
