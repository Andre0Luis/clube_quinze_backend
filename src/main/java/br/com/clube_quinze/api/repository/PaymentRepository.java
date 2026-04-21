package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.enumeration.PaymentStatus;
import br.com.clube_quinze.api.model.payment.Payment;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByUserId(Long userId, Pageable pageable);

    @Query("select p from Payment p where p.status = :status and p.paidAt between :start and :end")
    List<Payment> findPaidBetween(
            @Param("status") PaymentStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

        @Query("""
            select coalesce(sum(p.amount), 0)
            from Payment p
            where p.status = :status
            and p.paidAt between :start and :end
            """)
        java.math.BigDecimal sumAmountByStatusAndPaidAtBetween(
            @Param("status") PaymentStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("select p from Payment p where p.status = 'PENDING' and p.paidAt is null")
    List<Payment> findPending();
}
