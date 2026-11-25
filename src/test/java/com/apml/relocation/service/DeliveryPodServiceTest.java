package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DeliveryPodRecord;
import com.apml.relocation.dto.PodDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.DeliveryPodRecordRepository;
import com.apml.relocation.support.TestData;
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
class DeliveryPodServiceTest {

    private static final String CFR = TestData.CFR;

    @Mock DeliveryPodRecordRepository pods;
    @Mock ConsignmentRepository consignments;

    private DeliveryPodService service;
    private Consignment consignment;

    @BeforeEach
    void setUp() {
        service = new DeliveryPodService(pods, consignments, TestData.clock());
        useItems(ItemCategory.TWO_WHEELER);
        when(pods.save(any(DeliveryPodRecord.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void useItems(ItemCategory category) {
        consignment = TestData.consignment(ConsignmentStatus.IN_TRANSIT, TestData.enquiry(TestData.item(1, category, "5000", "5000")));
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
    }

    private static PodDto.Request request(Boolean flag, String signedBy, java.time.LocalDateTime signedAt, String idReference) {
        return new PodDto.Request(flag, flag, flag, flag, idReference, flag, flag, signedBy, signedAt, "remarks", flag);
    }

    private DeliveryPodRecord storedPod(boolean ticked, String signedBy) {
        DeliveryPodRecord p = new DeliveryPodRecord();
        p.setConsignment(consignment);
        p.setOriginalRcVerified(ticked);
        p.setOriginalInsuranceVerified(ticked);
        p.setOriginalPucVerified(ticked);
        p.setConsigneeIdVerified(ticked);
        p.setPaymentReceiptsVerified(ticked);
        p.setUnpackedAndInspectedBeforeSignOff(ticked);
        p.setPodSignedBy(signedBy);
        when(pods.findByConsignment(consignment)).thenReturn(Optional.of(p));
        return p;
    }

    // ------------------------------------------------------------------ record

    @Test
    void recordStoresChecklistWithMaskedIdAndDefaultSignTime() {
        DeliveryPodRecord p = service.record(CFR, request(true, "Souptik Tat", null, "1234 5678 9012"));
        assertThat(p.isOriginalRcVerified()).isTrue();
        assertThat(p.isOriginalInsuranceVerified()).isTrue();
        assertThat(p.isOriginalPucVerified()).isTrue();
        assertThat(p.isConsigneeIdVerified()).isTrue();
        assertThat(p.isPaymentReceiptsVerified()).isTrue();
        assertThat(p.isUnpackedAndInspectedBeforeSignOff()).isTrue();
        assertThat(p.isDamageNotedOnPod()).isTrue();
        assertThat(p.getConsigneeIdMaskedRef()).isEqualTo("XXXX-XXXX-9012").doesNotContain("1234 5678");
        assertThat(p.getPodSignedAt()).isEqualTo(TestData.NOW);
        assertThat(p.getDeliveryRemarks()).isEqualTo("remarks");
    }

    @Test
    void missingFlagsCountAsNotVerified() {
        DeliveryPodRecord p = service.record(CFR, request(null, "Souptik Tat", TestData.NOW.minusHours(1), null));
        assertThat(p.isOriginalRcVerified()).isFalse();
        assertThat(p.isConsigneeIdVerified()).isFalse();
        assertThat(p.isDamageNotedOnPod()).isFalse();
        assertThat(p.getConsigneeIdMaskedRef()).isNull();
        assertThat(p.getPodSignedAt()).isEqualTo(TestData.NOW.minusHours(1));
    }

    @Test
    void explicitFalseFlagsAreStoredAsFalse() {
        assertThat(service.record(CFR, request(false, "Souptik Tat", null, null)).isPaymentReceiptsVerified()).isFalse();
    }

    @Test
    void recordReplacesAnExistingPod() {
        DeliveryPodRecord existing = storedPod(false, "Old Signer");
        DeliveryPodRecord p = service.record(CFR, request(true, "New Signer", null, null));
        assertThat(p).isSameAs(existing);
        assertThat(p.getPodSignedBy()).isEqualTo("New Signer");
    }

    @Test
    void recordRejectsAFutureSignTime() {
        PodDto.Request future = request(true, "Souptik Tat", TestData.NOW.plusMinutes(1), null);
        assertThrows(BusinessRuleException.class, () -> service.record(CFR, future));
    }

    @Test
    void recordNeedsAnInTransitConsignment() {
        consignment.setStatus(ConsignmentStatus.READY_FOR_LOADING);
        PodDto.Request request = request(true, "Souptik Tat", null, null);
        assertThrows(BusinessRuleException.class, () -> service.record(CFR, request));
    }

    @Test
    void recordFailsForUnknownConsignment() {
        PodDto.Request request = request(true, "Souptik Tat", null, null);
        assertThrows(ResourceNotFoundException.class, () -> service.record("NOPE", request));
    }

    @Test
    void getFindsOrFails() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(CFR));
        DeliveryPodRecord p = storedPod(true, "Souptik Tat");
        assertThat(service.get(CFR)).isSameAs(p);
    }

    // ------------------------------------------------------------------ privacy

    @Test
    void referencesAreNeverKeptRaw() {
        assertThat(DeliveryPodService.maskReference(null)).isNull();
        assertThat(DeliveryPodService.maskReference("1234-5678-9012")).isEqualTo("XXXX-XXXX-9012");
        assertThat(DeliveryPodService.maskReference("ID 42")).isEqualTo("XXXX-XXXX-XXXX");
        assertThat(DeliveryPodService.maskReference("")).isEqualTo("XXXX-XXXX-XXXX");
    }

    // ------------------------------------------------------------------ delivery gate

    @Test
    void gateNeedsAPodRecord() {
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class, () -> service.assertDeliverable(consignment));
        assertThat(ex.getViolations()).containsExactly("A delivery POD record is required before delivery");
    }

    @Test
    void completePodPassesTheGate() {
        DeliveryPodRecord p = storedPod(true, "Souptik Tat");
        service.assertDeliverable(consignment);
        assertThat(p.getPodSignedBy()).isEqualTo("Souptik Tat");
    }

    @Test
    void vehicleConsignmentNeedsOriginalDocumentsVerified() {
        storedPod(false, "Souptik Tat");
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class, () -> service.assertDeliverable(consignment));
        assertThat(ex.getViolations()).hasSize(6)
                .anyMatch(v -> v.contains("RC book")).anyMatch(v -> v.contains("insurance")).anyMatch(v -> v.contains("PUC"))
                .anyMatch(v -> v.contains("Consignee ID")).anyMatch(v -> v.contains("Payment receipts"))
                .anyMatch(v -> v.contains("unpacked and inspected"));
    }

    @Test
    void householdOnlyConsignmentSkipsVehicleDocuments() {
        useItems(ItemCategory.CARTON_BOX);
        storedPod(false, "Souptik Tat");
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class, () -> service.assertDeliverable(consignment));
        assertThat(ex.getViolations()).hasSize(3).noneMatch(v -> v.contains("RC book"));
    }

    @Test
    void podMustBeSigned() {
        DeliveryPodRecord p = storedPod(true, null);
        assertThrows(ComplianceViolationException.class, () -> service.assertDeliverable(consignment));
        p.setPodSignedBy("  ");
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class, () -> service.assertDeliverable(consignment));
        assertThat(ex.getViolations()).containsExactly("POD must be signed by the consignee");
    }
}
