package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ClaimStatus;
import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.DamageType;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MediaStage;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DamageClaim;
import com.apml.relocation.domain.model.InspectionMedia;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.exception.ClaimRejectedException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.DamageClaimRepository;
import com.apml.relocation.repository.InspectionMediaRepository;
import com.apml.relocation.repository.MasterArticleDeclarationRepository;
import com.apml.relocation.support.TestData;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Complements ClaimServiceTest with lookup failures, evidence counting and listing. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaimEvidenceAndLookupTest {

    private static final String CFR = TestData.CFR;

    @Mock ConsignmentRepository consignments;
    @Mock MasterArticleDeclarationRepository declarations;
    @Mock DamageClaimRepository claimRepo;
    @Mock InspectionMediaRepository mediaRepo;

    private ClaimService service;
    private Consignment consignment;
    private SurveyItem bike;
    private SurveyItem carton;

    @BeforeEach
    void setUp() {
        service = new ClaimService(consignments, declarations, claimRepo, mediaRepo, TestData.clock());
        bike = TestData.item(10, ItemCategory.TWO_WHEELER, "5000", "5000");
        carton = TestData.item(11, ItemCategory.CARTON_BOX, "5000", "5000");
        consignment = TestData.consignment(ConsignmentStatus.DELIVERED, TestData.enquiry(bike, carton));
        consignment.setDeliveredAt(TestData.NOW.minusHours(1));
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
        when(claimRepo.save(any(DamageClaim.class))).thenAnswer(i -> i.getArgument(0));
        MasterArticleDeclaration d = new MasterArticleDeclaration();
        d.getLines().add(new ArticleLine(10L, new BigDecimal("5000"), new BigDecimal("5000")));
        when(declarations.findByConsignment(consignment)).thenReturn(Optional.of(d));
    }

    private static InspectionMedia media(MediaStage stage, SurveyItem item) {
        InspectionMedia m = new InspectionMedia();
        m.setStage(stage);
        m.setSurveyItem(item);
        return m;
    }

    @Test
    void unknownConsignmentIsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.lodge("NOPE", 10L, DamageType.EXTERIOR, "dent", BigDecimal.TEN));
        assertThrows(ResourceNotFoundException.class, () -> service.list("NOPE"));
    }

    @Test
    void claimsNeedADeliveredConsignment() {
        consignment.setDeliveredAt(null);
        assertThrows(ClaimRejectedException.class,
                () -> service.lodge(CFR, 10L, DamageType.EXTERIOR, "dent", BigDecimal.TEN));
    }

    @Test
    void unknownItemIsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.lodge(CFR, 99L, DamageType.EXTERIOR, "dent", BigDecimal.TEN));
    }

    @Test
    void claimsNeedASignedMasterArticleList() {
        when(declarations.findByConsignment(consignment)).thenReturn(Optional.empty());
        assertThrows(ClaimRejectedException.class,
                () -> service.lodge(CFR, 10L, DamageType.EXTERIOR, "dent", BigDecimal.TEN));
    }

    @Test
    void evidenceOnFileIsCountedPerItemAndForTheWholeConsignment() {
        when(mediaRepo.findByConsignmentOrderByCapturedAtAsc(consignment)).thenReturn(List.of(
                media(MediaStage.PRE_PACKING, bike),
                media(MediaStage.PACKED_AT_GODOWN, null),
                media(MediaStage.UNPACKED_AT_DELIVERY, bike),
                media(MediaStage.PRE_PACKING, carton),
                media(MediaStage.UNPACKED_AT_DELIVERY, carton)));
        DamageClaim claim = service.lodge(CFR, 10L, DamageType.EXTERIOR, "Scratch", new BigDecimal("500"));
        assertThat(claim.getBaselineEvidenceCount()).isEqualTo(2);
        assertThat(claim.getDeliveryEvidenceCount()).isEqualTo(1);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("500");
    }

    @Test
    void claimWithoutAnyEvidenceRecordsZeroCounts() {
        DamageClaim claim = service.lodge(CFR, 10L, DamageType.EXTERIOR, "Scratch", new BigDecimal("500"));
        assertThat(claim.getBaselineEvidenceCount()).isZero();
        assertThat(claim.getDeliveryEvidenceCount()).isZero();
    }

    @Test
    void listReturnsStoredClaims() {
        DamageClaim claim = new DamageClaim();
        when(claimRepo.findByConsignment(consignment)).thenReturn(List.of(claim));
        assertThat(service.list(CFR)).containsExactly(claim);
    }
}
