package ra.edu.identityservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ra.edu.identityservice.entity.RefreshToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalse(UUID userId);

    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithUserAndRoles(@Param("tokenHash") String tokenHash);
}
