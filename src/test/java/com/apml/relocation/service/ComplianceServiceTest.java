package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.enums.RestrictedItem;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.KycDocument;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.domain.model.VehicleChecklist;
import com.apml.relocation.exception.ComplianceViolationException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.KycDocumentRepository;
import com.apml.relocation.repository.MasterArticleDeclarationRepository;
import com.apml.relocation.repository.VehicleChecklistRepository;
import com.apml.relocation.support.TestData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComplianceServiceTest {

    private static final String CFR = TestData.CFR;

    @Mock ConsignmentRepository consignments;
    @Mock KycDocumentRepository kycDocuments;
    @Mock VehicleChecklistRepository checklists;
    @Mock MasterArticleDeclarationRepository declarations;

    private ComplianceService service;
    private SurveyItem bike;
    private SurveyItem carton;
    private Consignment consignment;
    private final List<KycDocument> kycList = new ArrayList<>();
    private final List<VehicleChecklist> checklistList = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ComplianceService(consignments, kycDocuments, checklists, declarations, TestData.clock());
        bike = TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000");
        carton = TestData.item(2, ItemCategory.CARTON_BOX, "5000", "5000");
        useConsignment(TestData.enquiry(bike, carton));
        when(kycDocuments.save(any(KycDocument.class))).thenAnswer(i -> i.getArgument(0));
        when(checklists.save(any(VehicleChecklist.class))).thenAnswer(i -> i.getArgument(0));
        when(declarations.save(any(MasterArticleDeclaration.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void useConsignment(Enquiry enquiry) {
        consignment = TestData.consignment(ConsignmentStatus.QUOTED, enquiry);
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
        when(kycDocuments.findByConsignment(consignment)).thenReturn(kycList);
        when(checklists.findByConsignment(consignment)).thenReturn(checklistList);
    }

    private VehicleChecklist checklistFor(SurveyItem item, boolean docs, Boolean tankEmpty, String liters) {
        VehicleChecklist v = new VehicleChecklist();
        v.setConsignment(consignment);
        v.setSurveyItem(item);
        v.setRcBook(docs);
        v.setValidInsurance(docs);
        v.setPucCertificate(docs);
        v.setRoadTaxReceipt(docs);
        v.setFuelTankEmpty(tankEmpty);
        v.setFuelLiters(liters == null ? null : new BigDecimal(liters));
        return v;
    }

    private KycDocument kyc(KycType type, boolean verified, Integer photos) {
        KycDocument d = new KycDocument();
        d.setConsignment(consignment);
        d.setType(type);
        d.setVerified(verified);
        d.setPhotoCount(photos);
        return d;
    }

    private void useCar() {
        useConsignment(TestData.enquiry(TestData.item(3, ItemCategory.FOUR_WHEELER, "100000", "100000")));
    }

    // ------------------------------------------------------------------ vehicle checklist

    @Test
    void initCreatesChecklistsOnlyForVehicles() {
        service.initVehicleChecklists(consignment);
        verify(checklists, times(1)).save(any(VehicleChecklist.class));
    }

    @Test
    void checklistUpdateFailsForUnknownConsignmentOrChecklist() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateVehicleChecklist("NOPE", 1L, true, true, true, true, Boolean.TRUE, null));
        assertThrows(ResourceNotFoundException.class,
                () -> service.updateVehicleChecklist(CFR, 1L, true, true, true, true, Boolean.TRUE, null));
    }

    @Test
    void checklistUpdateIsBlockedOnceDocumentsAreLocked() {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        assertThrows(ComplianceViolationException.class,
                () -> service.updateVehicleChecklist(CFR, 1L, true, true, true, true, Boolean.TRUE, null));
    }

    @Test
    void twoWheelerChecklistRequiresDrainedTank() {
        VehicleChecklist v = checklistFor(bike, false, null, null);
        when(checklists.findByConsignmentAndSurveyItem_Id(consignment, 1L)).thenReturn(Optional.of(v));
        assertThrows(ComplianceViolationException.class,
                () -> service.updateVehicleChecklist(CFR, 1L, true, true, true, true, Boolean.FALSE, null));
        VehicleChecklist saved = service.updateVehicleChecklist(CFR, 1L, true, true, true, true, Boolean.TRUE, null);
        assertThat(saved.isRcBook()).isTrue();
        assertThat(saved.isValidInsurance()).isTrue();
        assertThat(saved.isPucCertificate()).isTrue();
        assertThat(saved.isRoadTaxReceipt()).isTrue();
        assertThat(saved.getFuelTankEmpty()).isTrue();
    }

    @Test
    void fourWheelerChecklistCapsFuelAt24Litres() {
        useCar();
        VehicleChecklist v = checklistFor(consignment.getEnquiry().getItems().get(0), false, null, null);
        when(checklists.findByConsignmentAndSurveyItem_Id(consignment, 3L)).thenReturn(Optional.of(v));
        assertThrows(ComplianceViolationException.class,
                () -> service.updateVehicleChecklist(CFR, 3L, true, true, true, true, null, new BigDecimal("24.1")));
        assertThrows(ComplianceViolationException.class,
                () -> service.updateVehicleChecklist(CFR, 3L, true, true, true, true, null, null));
        assertThat(service.updateVehicleChecklist(CFR, 3L, true, true, true, true, null, new BigDecimal("24.0")).getFuelLiters())
                .isEqualByComparingTo("24.0");
    }

    @Test
    void fuelRuleOnlyAppliesToVehicles() {
        assertThat(ComplianceService.fuelViolation(checklistFor(carton, true, null, null))).isEmpty();
        assertThat(ComplianceService.fuelViolation(checklistFor(bike, true, Boolean.TRUE, null))).isEmpty();
        assertThat(ComplianceService.fuelViolation(checklistFor(bike, true, Boolean.FALSE, null))).isPresent();
    }

    @Test
    void listVehicleChecklistsReturnsRepositoryContent() {
        checklistList.add(checklistFor(bike, true, Boolean.TRUE, null));
        assertThat(service.listVehicleChecklists(CFR)).hasSize(1);
    }

    // ------------------------------------------------------------------ KYC

    @Test
    void kycFailsForUnknownConsignmentOrLockedDocuments() {
        assertThrows(ResourceNotFoundException.class, () -> service.submitKyc("NOPE", KycType.PAN, "ABCDE1234F", null));
        consignment.setStatus(ConsignmentStatus.READY_FOR_LOADING);
        assertThrows(ComplianceViolationException.class, () -> service.submitKyc(CFR, KycType.PAN, "ABCDE1234F", null));
    }

    @Test
    void aadhaarMustBeTwelveDigitsAfterNormalisation() {
        assertThrows(ComplianceViolationException.class, () -> service.submitKyc(CFR, KycType.AADHAAR, null, null));
        assertThrows(ComplianceViolationException.class, () -> service.submitKyc(CFR, KycType.AADHAAR, "1234", null));
        KycDocument d = service.submitKyc(CFR, KycType.AADHAAR, "1234 5678 9012", 3);
        assertThat(d.getDocumentNumber()).isEqualTo("123456789012");
        assertThat(d.getPhotoCount()).isNull();
        assertThat(d.isVerified()).isTrue();
    }

    @Test
    void panMustMatchTheStandardFormat() {
        assertThrows(ComplianceViolationException.class, () -> service.submitKyc(CFR, KycType.PAN, null, null));
        assertThrows(ComplianceViolationException.class, () -> service.submitKyc(CFR, KycType.PAN, "ABC", null));
        assertThat(service.submitKyc(CFR, KycType.PAN, "abcde1234f", null).getDocumentNumber()).isEqualTo("ABCDE1234F");
    }

    @Test
    void passportPhotosNeedTwoForBikeOrHouseholdGoods() {
        assertThrows(ComplianceViolationException.class, () -> service.submitKyc(CFR, KycType.PASSPORT_PHOTOS, null, null));
        assertThrows(ComplianceViolationException.class, () -> service.submitKyc(CFR, KycType.PASSPORT_PHOTOS, null, 1));
        KycDocument d = service.submitKyc(CFR, KycType.PASSPORT_PHOTOS, "ignored", 2);
        assertThat(d.getPhotoCount()).isEqualTo(2);
        assertThat(d.getDocumentNumber()).isNull();
    }

    @Test
    void passportPhotosNeedFourWhenACarIsIncluded() {
        useCar();
        assertThrows(ComplianceViolationException.class, () -> service.submitKyc(CFR, KycType.PASSPORT_PHOTOS, null, 2));
        assertThat(service.submitKyc(CFR, KycType.PASSPORT_PHOTOS, null, 4).getPhotoCount()).isEqualTo(4);
        assertThat(ComplianceService.requiredPhotos(consignment)).isEqualTo(4);
    }

    @Test
    void resubmittingKycReplacesTheExistingDocument() {
        KycDocument existing = kyc(KycType.PAN, false, null);
        when(kycDocuments.findByConsignmentAndType(consignment, KycType.PAN)).thenReturn(Optional.of(existing));
        KycDocument saved = service.submitKyc(CFR, KycType.PAN, "ABCDE1234F", null);
        assertThat(saved).isSameAs(existing);
        assertThat(saved.isVerified()).isTrue();
    }

    @Test
    void listKycReturnsRepositoryContent() {
        kycList.add(kyc(KycType.PAN, true, null));
        assertThat(service.listKyc(CFR)).hasSize(1);
    }

    // ------------------------------------------------------------------ Master Article List

    private List<ArticleLine> bikeOnly() {
        return List.of(new ArticleLine(1L, new BigDecimal("5000"), new BigDecimal("4000")));
    }

    @Test
    void masterArticleFailsForUnknownConsignmentOrLockedDocuments() {
        assertThrows(ResourceNotFoundException.class, () -> service.submitMasterArticle("NOPE", "me", Set.of(), bikeOnly()));
        consignment.setStatus(ConsignmentStatus.DELIVERED);
        assertThrows(ComplianceViolationException.class, () -> service.submitMasterArticle(CFR, "me", Set.of(), bikeOnly()));
    }

    @Test
    void masterArticleRejectsRestrictedItems() {
        Set<RestrictedItem> restricted = Set.of(RestrictedItem.JEWELRY);
        List<ArticleLine> lines = bikeOnly();
        assertThrows(ComplianceViolationException.class, () -> service.submitMasterArticle(CFR, "me", restricted, lines));
    }

    @Test
    void masterArticleAcceptsNullOrEmptyRestrictedSet() {
        assertThat(service.submitMasterArticle(CFR, "me", null, bikeOnly()).getSignedBy()).isEqualTo("me");
        assertThat(service.submitMasterArticle(CFR, "me", Set.of(), bikeOnly()).getSignedAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void masterArticleMustBeSubmittedAtLeast24HoursBeforePacking() {
        List<ArticleLine> lines = bikeOnly();
        consignment.setPackingDate(TestData.NOW.toLocalDate().plusDays(1));
        assertThrows(ComplianceViolationException.class, () -> service.submitMasterArticle(CFR, "me", Set.of(), lines));
        consignment.setPackingDate(TestData.NOW.toLocalDate());
        assertThrows(ComplianceViolationException.class, () -> service.submitMasterArticle(CFR, "me", Set.of(), lines));
    }

    private List<String> masterArticleViolations(ArticleLine... lines) {
        List<ArticleLine> list = List.of(lines);
        return assertThrows(ComplianceViolationException.class,
                () -> service.submitMasterArticle(CFR, "me", Set.of(), list)).getViolations();
    }

    @Test
    void masterArticleRejectsUnknownAndDuplicateItems() {
        assertThat(masterArticleViolations(new ArticleLine(99L, BigDecimal.TEN, BigDecimal.ONE)))
                .anyMatch(v -> v.contains("Unknown survey item 99"));
        assertThat(masterArticleViolations(new ArticleLine(1L, BigDecimal.TEN, BigDecimal.ONE),
                new ArticleLine(1L, BigDecimal.TEN, BigDecimal.ONE))).anyMatch(v -> v.contains("Duplicate line"));
    }

    @Test
    void masterArticleRejectsNegativeAmountsAndCoverAboveDeclaredValue() {
        assertThat(masterArticleViolations(new ArticleLine(1L, new BigDecimal("-1"), new BigDecimal("-2"))))
                .anyMatch(v -> v.contains("Negative amounts"));
        assertThat(masterArticleViolations(new ArticleLine(1L, new BigDecimal("5"), new BigDecimal("-1"))))
                .anyMatch(v -> v.contains("Negative amounts"));
        assertThat(masterArticleViolations(new ArticleLine(1L, new BigDecimal("5"), new BigDecimal("6"))))
                .anyMatch(v -> v.contains("cannot exceed declared value"));
    }

    @Test
    void omittedItemsDefaultToZeroDeclaredValue() {
        MasterArticleDeclaration d = service.submitMasterArticle(CFR, "me", Set.of(), bikeOnly());
        assertThat(d.getLines()).hasSize(2);
        assertThat(d.coverageFor(1L)).isEqualByComparingTo("4000");
        assertThat(d.coverageFor(2L)).isEqualByComparingTo("0");
    }

    @Test
    void resubmittingReplacesTheExistingDeclaration() {
        MasterArticleDeclaration existing = new MasterArticleDeclaration();
        existing.getLines().add(new ArticleLine(1L, BigDecimal.ONE, BigDecimal.ONE));
        when(declarations.findByConsignment(consignment)).thenReturn(Optional.of(existing));
        MasterArticleDeclaration d = service.submitMasterArticle(CFR, "me", Set.of(), bikeOnly());
        assertThat(d).isSameAs(existing);
        assertThat(d.getLines()).hasSize(2);
        assertThat(d.coverageFor(1L)).isEqualByComparingTo("4000");
    }

    @Test
    void getMasterArticleFindsOrFails() {
        assertThrows(ResourceNotFoundException.class, () -> service.getMasterArticle(CFR));
        MasterArticleDeclaration d = new MasterArticleDeclaration();
        when(declarations.findByConsignment(consignment)).thenReturn(Optional.of(d));
        assertThat(service.getMasterArticle(CFR)).isSameAs(d);
    }

    // ------------------------------------------------------------------ aggregate evaluation

    private void completeEverything() {
        kycList.add(kyc(KycType.AADHAAR, true, null));
        kycList.add(kyc(KycType.PAN, true, null));
        kycList.add(kyc(KycType.PASSPORT_PHOTOS, true, 2));
        checklistList.add(checklistFor(bike, true, Boolean.TRUE, null));
        when(declarations.findByConsignment(consignment)).thenReturn(Optional.of(new MasterArticleDeclaration()));
    }

    @Test
    void evaluateListsEveryMissingRequirement() {
        checklistList.add(checklistFor(bike, false, null, null));
        List<String> violations = service.evaluate(consignment);
        assertThat(violations).hasSize(9)
                .anyMatch(v -> v.contains("AADHAAR")).anyMatch(v -> v.contains("PAN")).anyMatch(v -> v.contains("PASSPORT_PHOTOS"))
                .anyMatch(v -> v.contains("RC_BOOK")).anyMatch(v -> v.contains("VALID_INSURANCE"))
                .anyMatch(v -> v.contains("PUC_CERTIFICATE")).anyMatch(v -> v.contains("ROAD_TAX_RECEIPT"))
                .anyMatch(v -> v.contains("fuel tank")).anyMatch(v -> v.contains("Master Article List"));
    }

    @Test
    void evaluateAcceptsACompleteFile() {
        completeEverything();
        assertThat(service.evaluate(consignment)).isEmpty();
        assertThat(service.evaluate(CFR)).isEmpty();
        service.assertCompliant(consignment);
    }

    @Test
    void evaluateFlagsUnverifiedKycAndShortPhotoSets() {
        completeEverything();
        kycList.set(0, kyc(KycType.AADHAAR, false, null));
        assertThat(service.evaluate(consignment)).hasSize(1).first().asString().contains("AADHAAR");

        kycList.set(0, kyc(KycType.AADHAAR, true, null));
        kycList.set(2, kyc(KycType.PASSPORT_PHOTOS, true, null));
        assertThat(service.evaluate(consignment)).hasSize(1).first().asString().contains("Passport photos");

        kycList.set(2, kyc(KycType.PASSPORT_PHOTOS, true, 1));
        assertThat(service.evaluate(consignment)).hasSize(1).first().asString().contains("Passport photos");
    }

    @Test
    void evaluateExpectsFourPhotosWhenACarTravels() {
        useCar();
        kycList.add(kyc(KycType.AADHAAR, true, null));
        kycList.add(kyc(KycType.PAN, true, null));
        kycList.add(kyc(KycType.PASSPORT_PHOTOS, true, 2));
        checklistList.add(checklistFor(consignment.getEnquiry().getItems().get(0), true, null, "10"));
        when(declarations.findByConsignment(consignment)).thenReturn(Optional.of(new MasterArticleDeclaration()));
        assertThat(service.evaluate(consignment)).hasSize(1).first().asString().contains("4 required");
    }

    @Test
    void assertCompliantThrowsWithAllViolations() {
        ComplianceViolationException ex = assertThrows(ComplianceViolationException.class, () -> service.assertCompliant(consignment));
        assertThat(ex.getViolations()).hasSize(4);
    }
}
