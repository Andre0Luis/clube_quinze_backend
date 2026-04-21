package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.appointment.Appointment;
import br.com.clube_quinze.api.model.enumeration.AppointmentStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    Page<Appointment> findByClientId(Long clientId, Pageable pageable);

    @Query("select a from Appointment a where a.client.id = :clientId and a.scheduledAt >= :now order by a.scheduledAt asc")
    List<Appointment> findUpcomingByClient(@Param("clientId") Long clientId, @Param("now") LocalDateTime now);

    @Query("select a from Appointment a where a.status = :status and a.scheduledAt between :start and :end")
    List<Appointment> findByStatusAndBetween(
            @Param("status") AppointmentStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

        @Query("""
            select a
            from Appointment a
            join fetch a.client c
            where a.status = :status
            and a.scheduledAt >= :from
            order by a.scheduledAt asc
            """)
        List<Appointment> findUpcomingByStatus(
            @Param("status") AppointmentStatus status,
            @Param("from") LocalDateTime from,
            Pageable pageable);

    boolean existsByScheduledAt(LocalDateTime scheduledAt);

    boolean existsByScheduledAtAndIdNot(LocalDateTime scheduledAt, Long id);

    List<Appointment> findByScheduledAtBetween(LocalDateTime start, LocalDateTime end);
}
