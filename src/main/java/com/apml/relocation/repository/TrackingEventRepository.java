package com.apml.relocation.repository;

import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.TrackingEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {
    List<TrackingEvent> findByConsignmentOrderByEventTimestampAscIdAsc(Consignment consignment);
    TrackingEvent findTopByConsignmentOrderByIdDesc(Consignment consignment);
}
