package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.notification.PushToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {
    List<PushToken> findByUserIdAndInvalidatedAtIsNull(Long userId);
    Optional<PushToken> findByToken(String token);
}
