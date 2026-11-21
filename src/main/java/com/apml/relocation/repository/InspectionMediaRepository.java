package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.InspectionMedia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionMediaRepository extends JpaRepository<InspectionMedia, Long> {
    List<InspectionMedia> findByConsignmentOrderByCapturedAtAsc(Consignment consignment);
}
