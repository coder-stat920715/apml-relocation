package com.apml.relocation.service;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.TransitLog;
import com.apml.relocation.dto.TransitDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.TransitLogRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransitService {

    private static final Set<ConsignmentStatus> LOGGABLE =
            Set.of(ConsignmentStatus.READY_FOR_LOADING, ConsignmentStatus.IN_TRANSIT, ConsignmentStatus.DELIVERED);

    private final ConsignmentRepository consignments;
    private final TransitLogRepository logs;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TransitDto.EstimateResponse estimate(String cfrNo) {
        Consignment c = load(cfrNo);
        Enquiry e = c.getEnquiry();
        int days = e.getMovementType().estimateDays(e.getDistanceKm());
        return new TransitDto.EstimateResponse(cfrNo, e.getMovementType(), e.getDistanceKm(),
                e.getMovementType().getKmPerDay(), e.getMovementType().getBufferDays(), days,
                c.getLoadingDate().plusDays(days));
    }

    @Transactional
    public TransitLog addLog(String cfrNo, String location, String remarks) {
        Consignment c = load(cfrNo);
        if (!LOGGABLE.contains(c.getStatus())) {
            throw new BusinessRuleException("Transit logs can only be added once the consignment is ready for loading");
        }
        TransitLog t = new TransitLog();
        t.setConsignment(c);
        t.setLocation(location);
        t.setRemarks(remarks);
        t.setStatusAtLog(c.getStatus());
        t.setLoggedAt(LocalDateTime.now(clock));
        return logs.save(t);
    }

    @Transactional(readOnly = true)
    public List<TransitLog> list(String cfrNo) {
        return logs.findByConsignmentOrderByLoggedAtAsc(load(cfrNo));
    }

    private Consignment load(String cfrNo) {
        return consignments.findByCfrNo(cfrNo).orElseThrow(() -> new ResourceNotFoundException("Consignment", cfrNo));
    }
}
