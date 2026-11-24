package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.TransitLog;
import com.apml.relocation.dto.TransitDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.TransitLogRepository;
import com.apml.relocation.support.TestData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransitServiceTest {

    private static final String CFR = TestData.CFR;

    @Mock ConsignmentRepository consignments;
    @Mock TransitLogRepository logs;

    private TransitService service;
    private Consignment consignment;

    @BeforeEach
    void setUp() {
        service = new TransitService(consignments, logs, TestData.clock());
        consignment = TestData.consignment(ConsignmentStatus.READY_FOR_LOADING,
                TestData.enquiry(TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000")));
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
        when(logs.save(any(TransitLog.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void estimateUsesMovementTypeDistanceAndLoadingDate() {
        TransitDto.EstimateResponse e = service.estimate(CFR);
        assertThat(e.estimatedTransitDays()).isEqualTo(11);
        assertThat(e.kmPerDay()).isEqualTo(200);
        assertThat(e.bufferDays()).isEqualTo(3);
        assertThat(e.expectedDeliveryDate()).isEqualTo(consignment.getLoadingDate().plusDays(11));
    }

    @Test
    void logsAreAcceptedFromReadyForLoadingOnwards() {
        for (ConsignmentStatus status : List.of(ConsignmentStatus.READY_FOR_LOADING, ConsignmentStatus.IN_TRANSIT,
                ConsignmentStatus.DELIVERED)) {
            consignment.setStatus(status);
            TransitLog log = service.addLog(CFR, "Nagpur", "Halt");
            assertThat(log.getStatusAtLog()).isEqualTo(status);
            assertThat(log.getLoggedAt()).isEqualTo(TestData.NOW);
            assertThat(log.getLocation()).isEqualTo("Nagpur");
        }
    }

    @Test
    void logsAreRejectedBeforeLoadingIsReady() {
        consignment.setStatus(ConsignmentStatus.QUOTED);
        assertThrows(BusinessRuleException.class, () -> service.addLog(CFR, "Nagpur", null));
    }

    @Test
    void listReturnsLogsAndUnknownConsignmentFails() {
        TransitLog log = new TransitLog();
        when(logs.findByConsignmentOrderByLoggedAtAsc(consignment)).thenReturn(List.of(log));
        assertThat(service.list(CFR)).containsExactly(log);
        assertThrows(ResourceNotFoundException.class, () -> service.estimate("NOPE"));
    }
}
