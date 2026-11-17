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

    @Query("select a from Appointment a join fetch a.client where a.status in :statuses "
            + "and a.scheduledAt between :start and :end")
    List<Appointment> findByStatusInAndBetween(
            @Param("statuses") java.util.Collection<AppointmentStatus> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            select a
            from Appointment a
            join fetch a.client c
            where a.status in :statuses
            and a.scheduledAt >= :from
            order by a.scheduledAt asc
            """)
    List<Appointment> findUpcomingByStatusIn(
            @Param("statuses") java.util.Collection<AppointmentStatus> statuses,
            @Param("from") LocalDateTime from,
            Pageable pageable);

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

    /**
     * Grava o vínculo com o Google por query: não passa pelos callbacks da entidade, então updatedAt não muda
     * (senão o agendamento pareceria "alterado" e seria sincronizado de novo, em laço).
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Appointment a set a.googleEventId = :eventId, a.googleSyncedFor = :syncedFor where a.id = :id")
    int markGoogleSynced(@Param("id") Long id, @Param("eventId") String eventId,
                         @Param("syncedFor") LocalDateTime syncedFor);

    /** Agendamentos recentes/futuros ainda não refletidos no Google (nunca sincronizados ou alterados depois). */
    @Query("select a from Appointment a join fetch a.client where a.scheduledAt >= :from "
            + "and (a.googleSyncedFor is null or a.updatedAt > a.googleSyncedFor) order by a.scheduledAt asc")
    List<Appointment> findNeedingGoogleSync(@Param("from") LocalDateTime from, Pageable pageable);

    /** Esquece o vínculo com o Google (conta ou agenda nova, ou desconexão): tudo volta a ser "não sincronizado". */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Appointment a set a.googleEventId = null, a.googleSyncedFor = null "
            + "where a.googleEventId is not null or a.googleSyncedFor is not null")
    int resetGoogleSync();

    boolean existsByScheduledAt(LocalDateTime scheduledAt);

    boolean existsByScheduledAtAndIdNot(LocalDateTime scheduledAt, Long id);

    List<Appointment> findByScheduledAtBetween(LocalDateTime start, LocalDateTime end);
}
