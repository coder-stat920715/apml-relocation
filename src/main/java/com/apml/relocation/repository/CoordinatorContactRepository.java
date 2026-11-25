package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.CoordinatorContact;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoordinatorContactRepository extends JpaRepository<CoordinatorContact, Long> {
    List<CoordinatorContact> findByConsignmentOrderByAssignedAtAscIdAsc(Consignment consignment);
}
