package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.DeliveryMode;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.LiabilitySection;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.PackingMaterial;
import com.apml.relocation.domain.enums.PaymentBillingType;
import com.apml.relocation.domain.enums.UnloadingBy;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.GoodsConsignmentNoteRepository;
import com.apml.relocation.support.TestData;
import com.apml.relocation.util.ReferenceNumberGenerator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
class GcnServiceTest {

    private static final String CFR = TestData.CFR;

    @Mock GoodsConsignmentNoteRepository gcns;
    @Mock ConsignmentRepository consignments;
    @Mock PaymentService payments;
    @Mock ReferenceNumberGenerator numbers;

    private GcnService service;
    private Consignment consignment;

    @BeforeEach
    void setUp() {
        service = new GcnService(gcns, consignments, payments, numbers, TestData.clock());
        useItems(TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000"), TestData.item(2, ItemCategory.CARTON_BOX, "5000", "5000"));
        when(gcns.save(any(GoodsConsignmentNote.class))).thenAnswer(i -> i.getArgument(0));
        when(numbers.digitalGcPrefix("Kolkata")).thenReturn("GCKOL2627");
    }

    private void useItems(com.apml.relocation.domain.model.SurveyItem... items) {
        consignment = TestData.consignment(ConsignmentStatus.READY_FOR_LOADING, TestData.enquiry(items));
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
    }

    private static GcnDto.IssueRequest blank() {
        return new GcnDto.IssueRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private static GcnDto.IssueRequest withLiability(LiabilitySection section, BigDecimal declared) {
        return new GcnDto.IssueRequest(null, null, section, declared, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    private static GcnDto.IssueRequest withWeights(BigDecimal actual, BigDecimal charged) {
        return new GcnDto.IssueRequest(null, null, null, null, null, null, null, null, actual, charged, null, null, null,
                null, null, null, null, null);
    }

    private static GcnDto.IssueRequest full() {
        return new GcnDto.IssueRequest(1354001L, TestData.CFR, LiabilitySection.SECTION_11_INSURED_RISK, new BigDecimal("10000"),
                DeliveryMode.DOOR_DELIVERY, UnloadingBy.BY_CARRIER, PaymentBillingType.TO_PAY, 2, new BigDecimal("44"),
                new BigDecimal("100"), new BigDecimal("6"), new BigDecimal("2"), new BigDecimal("3.5"),
                Map.of(PackingMaterial.HYGIENE_PAD, 1), "MUA-9999", "REG-1", "GSTIN-1", "996599");
    }

    private GoodsConsignmentNote existing() {
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setTotalPackages(2);
        g.setDeclaredGoodsValue(new BigDecimal("10000"));
        g.setLiabilitySection(LiabilitySection.SECTION_11_INSURED_RISK);
        when(gcns.findByConsignment(consignment)).thenReturn(Optional.of(g));
        return g;
    }

    // ------------------------------------------------------------------ lookups

    @Test
    void existsAndGetReflectTheRepository() {
        assertThat(service.exists(CFR)).isFalse();
        assertThrows(ResourceNotFoundException.class, () -> service.get(CFR));
        GoodsConsignmentNote g = existing();
        assertThat(service.exists(CFR)).isTrue();
        assertThat(service.get(CFR)).isSameAs(g);
        assertThat(service.find(consignment)).isSameAs(g);
        assertThrows(ResourceNotFoundException.class, () -> service.exists("NOPE"));
    }

    @Test
    void findReturnsNullWhenNoNoteExists() {
        assertThat(service.find(consignment)).isNull();
    }

    // ------------------------------------------------------------------ issue: preconditions

    @Test
    void issueIsOnlyAllowedBetweenBookingAndDispatch() {
        GcnDto.IssueRequest request = blank();
        consignment.setStatus(ConsignmentStatus.QUOTED);
        assertThrows(BusinessRuleException.class, () -> service.issue(CFR, request));
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        assertThrows(BusinessRuleException.class, () -> service.issue(CFR, request));
    }

    @Test
    void cfrReferenceMustMatchTheConsignment() {
        GcnDto.IssueRequest wrong = new GcnDto.IssueRequest(null, "CFRKOL269999999", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        assertThrows(BusinessRuleException.class, () -> service.issue(CFR, wrong));
    }

    @Test
    void cfrReferenceMatchIsCaseAndWhitespaceTolerant() {
        GcnDto.IssueRequest sloppy = new GcnDto.IssueRequest(null, "  cfrkol260000001 ", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        assertThat(service.issue(CFR, sloppy).getCfrReferenceNo()).isEqualTo(CFR);
        GcnDto.IssueRequest blankRef = new GcnDto.IssueRequest(null, "  ", null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
        assertThat(service.issue(CFR, blankRef).getCfrReferenceNo()).isEqualTo(CFR);
    }

    // ------------------------------------------------------------------ issue: new note

    @Test
    void firstNoteGetsDefaultsDerivedValuesAndSerial() {
        GoodsConsignmentNote g = service.issue(CFR, blank());
        assertThat(g.getGcnSerialNo()).isEqualTo(1354001L);
        assertThat(g.getGcnDate()).isEqualTo(TestData.NOW);
        assertThat(g.getIbaCode()).isEqualTo("MUA-1783");
        assertThat(g.getCarriageActRegNo()).isEqualTo("MH 01/CRA/2016/31");
        assertThat(g.getGstin()).isEqualTo("19AAFCA3559A1ZW");
        assertThat(g.getSacCode()).isEqualTo("996511");
        assertThat(g.getPaymentBillingType()).isEqualTo(PaymentBillingType.TBB);
        assertThat(g.getTotalPackages()).isEqualTo(2);
        assertThat(g.getLiabilitySection()).isEqualTo(LiabilitySection.SECTION_11_INSURED_RISK);
        assertThat(g.getDeclaredGoodsValue()).isEqualByComparingTo("10000");
        verify(payments).syncBillingType(consignment);
    }

    @Test
    void serialContinuesFromTheHighestIssuedNumber() {
        GoodsConsignmentNote last = TestData.gcn(consignment);
        last.setGcnSerialNo(1354005L);
        when(gcns.findTopByOrderByGcnSerialNoDesc()).thenReturn(Optional.of(last));
        assertThat(service.issue(CFR, blank()).getGcnSerialNo()).isEqualTo(1354006L);
    }

    @Test
    void suppliedSerialMustBeUnique() {
        GcnDto.IssueRequest taken = new GcnDto.IssueRequest(1354001L, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        when(gcns.existsByGcnSerialNo(1354001L)).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.issue(CFR, taken));
        when(gcns.existsByGcnSerialNo(1354001L)).thenReturn(false);
        assertThat(service.issue(CFR, taken).getGcnSerialNo()).isEqualTo(1354001L);
    }

    @Test
    void fullRequestPopulatesEveryField() {
        GoodsConsignmentNote g = service.issue(CFR, full());
        assertThat(g.getIbaCode()).isEqualTo("MUA-9999");
        assertThat(g.getCarriageActRegNo()).isEqualTo("REG-1");
        assertThat(g.getGstin()).isEqualTo("GSTIN-1");
        assertThat(g.getSacCode()).isEqualTo("996599");
        assertThat(g.getDeliveryMode()).isEqualTo(DeliveryMode.DOOR_DELIVERY);
        assertThat(g.getUnloadingBy()).isEqualTo(UnloadingBy.BY_CARRIER);
        assertThat(g.getPaymentBillingType()).isEqualTo(PaymentBillingType.TO_PAY);
        assertThat(g.getTotalPackages()).isEqualTo(2);
        assertThat(g.getActualWeightKg()).isEqualByComparingTo("44");
        assertThat(g.getChargedWeightKg()).isEqualByComparingTo("100");
        assertThat(g.getLengthFt()).isEqualByComparingTo("6");
        assertThat(g.getBreadthFt()).isEqualByComparingTo("2");
        assertThat(g.getHeightFt()).isEqualByComparingTo("3.5");
        assertThat(g.getPackingMaterialsUsed()).containsEntry(PackingMaterial.HYGIENE_PAD, 1);
        assertThat(g.getDeclaredGoodsValue()).isEqualByComparingTo("10000");
    }

    // ------------------------------------------------------------------ issue: existing note

    @Test
    void updateKeepsExistingValuesWhenTheRequestIsBlank() {
        GoodsConsignmentNote g = existing();
        GoodsConsignmentNote updated = service.issue(CFR, blank());
        assertThat(updated).isSameAs(g);
        assertThat(updated.getTotalPackages()).isEqualTo(2);
        assertThat(updated.getDeclaredGoodsValue()).isEqualByComparingTo("10000");
    }

    @Test
    void serialNumberCannotBeChangedOnceIssued() {
        existing();
        GcnDto.IssueRequest other = new GcnDto.IssueRequest(1L, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        assertThrows(BusinessRuleException.class, () -> service.issue(CFR, other));
        GcnDto.IssueRequest same = new GcnDto.IssueRequest(1354001L, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        assertThat(service.issue(CFR, same).getGcnSerialNo()).isEqualTo(1354001L);
    }

    @Test
    void updateReplacesPackingMaterials() {
        GoodsConsignmentNote g = existing();
        g.getPackingMaterialsUsed().put(PackingMaterial.FOAM, 3);
        GcnDto.IssueRequest request = new GcnDto.IssueRequest(null, null, null, null, null, null, null, null, null, null, null,
                null, null, Map.of(PackingMaterial.LED_BOX, 1), null, null, null, null);
        assertThat(service.issue(CFR, request).getPackingMaterialsUsed()).containsOnlyKeys(PackingMaterial.LED_BOX);
    }

    // ------------------------------------------------------------------ weights

    @Test
    void chargedWeightMayNotBeBelowActualWeight() {
        GcnDto.IssueRequest reversed = withWeights(new BigDecimal("100"), new BigDecimal("44"));
        assertThrows(BusinessRuleException.class, () -> service.issue(CFR, reversed));
    }

    @Test
    void singleWeightsAreAcceptedWithoutComparison() {
        assertThat(service.issue(CFR, withWeights(new BigDecimal("44"), null)).getChargedWeightKg()).isNull();
        assertThat(service.issue(CFR, withWeights(null, new BigDecimal("44"))).getActualWeightKg()).isNull();
        assertThat(service.issue(CFR, withWeights(new BigDecimal("44"), new BigDecimal("44"))).getChargedWeightKg())
                .isEqualByComparingTo("44");
    }

    // ------------------------------------------------------------------ liability rules

    @Test
    void insuredConsignmentRejectsSection10() {
        GcnDto.IssueRequest request = withLiability(LiabilitySection.SECTION_10_LIMITED_LIABILITY, null);
        assertThrows(ComplianceViolationException.class, () -> service.issue(CFR, request));
    }

    @Test
    void insuredConsignmentAcceptsSection11WithExplicitDeclaredValue() {
        GoodsConsignmentNote g = service.issue(CFR, withLiability(LiabilitySection.SECTION_11_INSURED_RISK, new BigDecimal("8000")));
        assertThat(g.getLiabilitySection()).isEqualTo(LiabilitySection.SECTION_11_INSURED_RISK);
        assertThat(g.getDeclaredGoodsValue()).isEqualByComparingTo("8000");
    }

    @Test
    void insuredConsignmentNeedsAPositiveDeclaredValue() {
        useItems(TestData.item(1, ItemCategory.TWO_WHEELER, "0", "100"));
        GcnDto.IssueRequest request = blank();
        assertThrows(ComplianceViolationException.class, () -> service.issue(CFR, request));
    }

    @Test
    void ownerRiskConsignmentIsLimitedLiability() {
        consignment.setOwnerRisk(true);
        GoodsConsignmentNote g = service.issue(CFR, blank());
        assertThat(g.getLiabilitySection()).isEqualTo(LiabilitySection.SECTION_10_LIMITED_LIABILITY);
        assertThat(g.getDeclaredGoodsValue()).isNull();
        GoodsConsignmentNote withValue = service.issue(CFR, withLiability(null, new BigDecimal("500")));
        assertThat(withValue.getDeclaredGoodsValue()).isEqualByComparingTo("500");
    }

    @Test
    void ownerRiskConsignmentRejectsSection11() {
        consignment.setOwnerRisk(true);
        GcnDto.IssueRequest request = withLiability(LiabilitySection.SECTION_11_INSURED_RISK, null);
        assertThrows(ComplianceViolationException.class, () -> service.issue(CFR, request));
    }

    @Test
    void zeroOrMissingCoverMeansNoInsurance() {
        useItems(TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "0"));
        assertThat(GcnService.isInsured(consignment)).isFalse();
        consignment.getEnquiry().getItems().get(0).setRiskCoverageAvailed(null);
        assertThat(GcnService.isInsured(consignment)).isFalse();
        consignment.getEnquiry().getItems().get(0).setRiskCoverageAvailed(BigDecimal.ONE);
        assertThat(GcnService.isInsured(consignment)).isTrue();
        consignment.setOwnerRisk(true);
        assertThat(GcnService.isInsured(consignment)).isFalse();
    }

    // ------------------------------------------------------------------ dispatch details

    @Test
    void dispatchDetailsRequireAnIssuedNote() {
        assertThrows(ComplianceViolationException.class, () -> service.applyDispatchDetails(consignment, null));
    }

    @Test
    void nullDetailsLeaveTheNoteUntouched() {
        GoodsConsignmentNote g = existing();
        assertThat(service.applyDispatchDetails(consignment, null)).isSameAs(g);
        assertThat(g.getTruckVehicleNo()).isNull();
    }

    @Test
    void truckSealAndCubeAreNormalisedAndStored() {
        GoodsConsignmentNote g = existing();
        service.applyDispatchDetails(consignment, new GcnDto.DispatchRequest("wb-03 d1234", " CUBE-7 ", " SEAL-9 "));
        assertThat(g.getTruckVehicleNo()).isEqualTo("WB03D1234");
        assertThat(g.getCubeNo()).isEqualTo("CUBE-7");
        assertThat(g.getSealNo()).isEqualTo("SEAL-9");
    }

    @Test
    void blankOrMissingDetailsAreIgnored() {
        GoodsConsignmentNote g = existing();
        g.setTruckVehicleNo("WB03D1234");
        g.setSealNo("S1");
        g.setCubeNo("C1");
        service.applyDispatchDetails(consignment, new GcnDto.DispatchRequest(" ", " ", " "));
        service.applyDispatchDetails(consignment, new GcnDto.DispatchRequest(null, null, null));
        assertThat(g.getTruckVehicleNo()).isEqualTo("WB03D1234");
        assertThat(g.getSealNo()).isEqualTo("S1");
        assertThat(g.getCubeNo()).isEqualTo("C1");
    }

    @Test
    void invalidTruckNumberIsRejected() {
        existing();
        GcnDto.DispatchRequest bad = new GcnDto.DispatchRequest("NOT A TRUCK!", null, null);
        assertThrows(BusinessRuleException.class, () -> service.applyDispatchDetails(consignment, bad));
    }

    @Test
    void markDispatchedStampsTheTime() {
        GoodsConsignmentNote g = TestData.gcn(consignment);
        service.markDispatched(g);
        assertThat(g.getDispatchedAt()).isEqualTo(TestData.NOW);
        verify(gcns).save(g);
    }

    // ------------------------------------------------------------------ dispatch gate

    private GoodsConsignmentNote ready() {
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.setTruckVehicleNo("WB03D1234");
        g.setSealNo("SEAL-1");
        g.setDeliveryMode(DeliveryMode.DOOR_DELIVERY);
        g.setLiabilitySection(LiabilitySection.SECTION_11_INSURED_RISK);
        g.setDeclaredGoodsValue(new BigDecimal("10000"));
        g.setPaymentBillingType(PaymentBillingType.PAID);
        return g;
    }

    private List<String> violationsOf(GoodsConsignmentNote g) {
        return assertThrows(ComplianceViolationException.class, () -> service.assertDispatchReady(consignment, g)).getViolations();
    }

    @Test
    void completeNotePassesTheGate() {
        GoodsConsignmentNote g = ready();
        service.assertDispatchReady(consignment, g);
        assertThat(g.getTruckVehicleNo()).isEqualTo("WB03D1234");
    }

    @Test
    void gateReportsMismatchedCfrReference() {
        GoodsConsignmentNote g = ready();
        g.setCfrReferenceNo("CFRKOL269999999");
        assertThat(violationsOf(g)).containsExactly("GCN cfrReferenceNo does not match consignment CFR");
    }

    @Test
    void gateRequiresTruckAndSeal() {
        GoodsConsignmentNote g = ready();
        g.setTruckVehicleNo(null);
        g.setSealNo(null);
        assertThat(violationsOf(g)).containsExactlyInAnyOrder("truckVehicleNo is required", "sealNo is required");
        g.setTruckVehicleNo(" ");
        g.setSealNo(" ");
        assertThat(violationsOf(g)).hasSize(2);
    }

    @Test
    void gateRequiresCubeNumberOnlyForCubeShipments() {
        GoodsConsignmentNote g = ready();
        service.assertDispatchReady(consignment, g);
        consignment.getEnquiry().setMovementType(MovementType.CUBES);
        assertThat(violationsOf(g)).containsExactly("cubeNo is required for CUBES shipments");
        g.setCubeNo(" ");
        assertThat(violationsOf(g)).hasSize(1);
        g.setCubeNo("CUBE-1");
        service.assertDispatchReady(consignment, g);
        assertThat(g.getCubeNo()).isEqualTo("CUBE-1");
    }

    @Test
    void gateRequiresDeliveryModeAndBillingTypePaid() {
        GoodsConsignmentNote g = ready();
        g.setDeliveryMode(null);
        g.setPaymentBillingType(PaymentBillingType.TO_PAY);
        assertThat(violationsOf(g)).hasSize(2).anyMatch(v -> v.contains("deliveryMode")).anyMatch(v -> v.contains("PAID"));
    }

    @Test
    void gateChecksLiabilitySectionAgainstInsurance() {
        GoodsConsignmentNote g = ready();
        g.setLiabilitySection(null);
        assertThat(violationsOf(g)).containsExactly("liabilitySection is required");

        g.setLiabilitySection(LiabilitySection.SECTION_10_LIMITED_LIABILITY);
        assertThat(violationsOf(g)).hasSize(1).first().asString().contains("must be SECTION_11_INSURED_RISK");

        consignment.setOwnerRisk(true);
        service.assertDispatchReady(consignment, g);
        g.setLiabilitySection(LiabilitySection.SECTION_11_INSURED_RISK);
        assertThat(violationsOf(g)).hasSize(1).first().asString().contains("requires FOV");
    }

    @Test
    void gateRequiresDeclaredValueUnderSection11() {
        GoodsConsignmentNote g = ready();
        g.setDeclaredGoodsValue(null);
        assertThat(violationsOf(g)).containsExactly("declaredGoodsValue is required under Section 11");
        g.setDeclaredGoodsValue(BigDecimal.ZERO);
        assertThat(violationsOf(g)).hasSize(1);
    }

    // ------------------------------------------------------------------ digital GC number

    private static GcnDto.DispatchRequest digital(String number) {
        return new GcnDto.DispatchRequest(null, null, null, number);
    }

    @Test
    void digitalGcNumberIsGeneratedAtDispatchWhenNotSupplied() {
        GoodsConsignmentNote g = existing();
        service.applyDispatchDetails(consignment, null);
        assertThat(g.getDigitalGcNo()).isEqualTo("GCKOL262700001");
    }

    @Test
    void generatedDigitalGcNumberContinuesTheSequence() {
        GoodsConsignmentNote g = existing();
        GoodsConsignmentNote last = TestData.gcn(consignment);
        last.setDigitalGcNo("GCKOL262708112");
        when(gcns.findTopByDigitalGcNoStartingWithOrderByDigitalGcNoDesc("GCKOL2627")).thenReturn(Optional.of(last));
        service.applyDispatchDetails(consignment, digital(" "));
        assertThat(g.getDigitalGcNo()).isEqualTo("GCKOL262708113");
    }

    @Test
    void assignedDigitalGcNumberIsKeptWhenNoneIsSupplied() {
        GoodsConsignmentNote g = existing();
        g.setDigitalGcNo("GCKOL262700005");
        service.applyDispatchDetails(consignment, digital(null));
        assertThat(g.getDigitalGcNo()).isEqualTo("GCKOL262700005");
    }

    @Test
    void suppliedDigitalGcNumberIsNormalisedAndStored() {
        GoodsConsignmentNote g = existing();
        service.applyDispatchDetails(consignment, digital(" gckol 262708112 "));
        assertThat(g.getDigitalGcNo()).isEqualTo("GCKOL262708112");
    }

    @Test
    void malformedDigitalGcNumberIsRejected() {
        existing();
        GcnDto.DispatchRequest bad = digital("GC-123");
        assertThrows(BusinessRuleException.class, () -> service.applyDispatchDetails(consignment, bad));
    }

    @Test
    void resendingTheSameDigitalGcNumberIsHarmlessButChangingItIsNot() {
        GoodsConsignmentNote g = existing();
        g.setDigitalGcNo("GCKOL262708112");
        when(gcns.existsByDigitalGcNo("GCKOL262708112")).thenReturn(true);
        service.applyDispatchDetails(consignment, digital("GCKOL262708112"));
        assertThat(g.getDigitalGcNo()).isEqualTo("GCKOL262708112");
        GcnDto.DispatchRequest other = digital("GCKOL262708999");
        assertThrows(BusinessRuleException.class, () -> service.applyDispatchDetails(consignment, other));
    }

    @Test
    void digitalGcNumberMustBeUnique() {
        existing();
        when(gcns.existsByDigitalGcNo("GCKOL262708112")).thenReturn(true);
        GcnDto.DispatchRequest taken = digital("GCKOL262708112");
        assertThrows(DuplicateResourceException.class, () -> service.applyDispatchDetails(consignment, taken));
    }
}
