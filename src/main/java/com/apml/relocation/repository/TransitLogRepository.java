package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.TransitLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransitLogRepository extends JpaRepository<TransitLog, Long> {
    List<TransitLog> findByConsignmentOrderByLoggedAtAsc(Consignment consignment);
}
