package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.notification.PushDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {
}
