package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DeliveryPodRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryPodRecordRepository extends JpaRepository<DeliveryPodRecord, Long> {
    Optional<DeliveryPodRecord> findByConsignment(Consignment consignment);
}
