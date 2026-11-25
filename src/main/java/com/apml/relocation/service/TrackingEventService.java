package com.apml.relocation.service;

import com.apml.relocation.domain.enums.TrackingEventCode;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.TrackingEvent;
import com.apml.relocation.repository.GoodsConsignmentNoteRepository;
import com.apml.relocation.repository.TrackingEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Low-level, append-only access to hub scan events (no business gating; see TrackingService). */
@Service
@RequiredArgsConstructor
public class TrackingEventService {

    /** Optional overrides for an event; blank values fall back to defaults derived from the event code and the GCN. */
    public record Details(String hubCity, String description, String truckVehicleNo, String agentId) {
        public static final Details EMPTY = new Details(null, null, null, null);
    }

    private final TrackingEventRepository events;
    private final GoodsConsignmentNoteRepository gcns;

    @Transactional
    public TrackingEvent append(Consignment c, TrackingEventCode code, LocalDateTime timestamp, Details details) {
        Details d = details == null ? Details.EMPTY : details;
        GoodsConsignmentNote g = gcns.findByConsignment(c).orElse(null);
        String origin = c.getEnquiry().getOriginCity();
        String destination = c.getEnquiry().getDestinationCity();

        TrackingEvent e = new TrackingEvent();
        e.setConsignment(c);
        e.setDigitalGcNo(g == null ? null : g.getDigitalGcNo());
        e.setMilestone(code.getMilestone());
        e.setEventCode(code);
        e.setEventTimestamp(timestamp);
        e.setEventDate(timestamp.toLocalDate());
        e.setHubCity(firstNonBlank(d.hubCity(), code.hubCity(origin, destination)));
        e.setEventDescription(firstNonBlank(d.description(), code.description(origin, destination)));
        e.setTruckVehicleNo(firstNonBlank(d.truckVehicleNo(), g == null ? null : g.getTruckVehicleNo()));
        e.setRecordedByAgentId(d.agentId());
        return events.save(e);
    }

    @Transactional(readOnly = true)
    public List<TrackingEvent> timeline(Consignment c) {
        return events.findByConsignmentOrderByEventTimestampAscIdAsc(c);
    }

    /** Most recently recorded event (by insertion order), or null when nothing was scanned yet. */
    @Transactional(readOnly = true)
    public TrackingEvent latest(Consignment c) {
        return events.findTopByConsignmentOrderByIdDesc(c);
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }
}
