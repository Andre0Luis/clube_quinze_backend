package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.google.GoogleCalendarConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleCalendarConnectionRepository extends JpaRepository<GoogleCalendarConnection, Long> {

    Optional<GoogleCalendarConnection> findFirstByOrderByIdAsc();
}
