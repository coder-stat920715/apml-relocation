package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.VehicleChecklist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleChecklistRepository extends JpaRepository<VehicleChecklist, Long> {
    List<VehicleChecklist> findByConsignment(Consignment consignment);
    Optional<VehicleChecklist> findByConsignmentAndSurveyItem_Id(Consignment consignment, Long surveyItemId);
}
