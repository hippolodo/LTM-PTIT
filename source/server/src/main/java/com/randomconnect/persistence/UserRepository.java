package com.randomconnect.persistence;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface UserRepository extends JpaRepository<UserAccount,String> {
 java.util.Optional<UserAccount> findByUsernameOrEmail(String username,String email);
 boolean existsByUsernameOrEmail(String username,String email);
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @Query("select u from UserAccount u where u.id=:id")
 java.util.Optional<UserAccount> locked(@Param("id") String id);

 @Query("select u from UserAccount u where u.status='ACTIVE' and u.id<>:self and (lower(u.username) like :term escape '!' or lower(u.displayName) like :term escape '!') order by u.username")
 java.util.List<UserAccount> search(@Param("self") String self, @Param("term") String term, org.springframework.data.domain.Pageable page);
}
