package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ClaimStatus;
import com.apml.relocation.domain.enums.DamageType;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DamageClaim;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.exception.ClaimRejectedException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.DamageClaimRepository;
import com.apml.relocation.repository.InspectionMediaRepository;
import com.apml.relocation.repository.MasterArticleDeclarationRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
class ClaimServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 12, 0);
    private static final long ITEM_ID = 10L;
    private static final String CFR = "CFRTEST1";

    @Mock ConsignmentRepository consignments;
    @Mock MasterArticleDeclarationRepository declarations;
    @Mock DamageClaimRepository claimRepo;
    @Mock InspectionMediaRepository mediaRepo;

    private ClaimService service;

    @BeforeEach
    void setUp() {
        service = new ClaimService(consignments, declarations, claimRepo, mediaRepo, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
        when(claimRepo.save(any(DamageClaim.class))).thenAnswer(i -> i.getArgument(0));
    }

    private void givenDelivered(ItemCategory category, long hoursAgo, boolean ownerRisk, String cover) {
        Enquiry e = new Enquiry();
        SurveyItem item = new SurveyItem();
        item.setId(ITEM_ID);
        item.setCategory(category);
        item.setDescription("Test item");
        item.setVolumeCft(BigDecimal.ONE);
        e.addItem(item);

        Consignment c = new Consignment();
        c.setCfrNo(CFR);
        c.setEnquiry(e);
        c.setOwnerRisk(ownerRisk);
        c.setDeliveredAt(NOW.minusHours(hoursAgo));

        MasterArticleDeclaration d = new MasterArticleDeclaration();
        d.setConsignment(c);
        BigDecimal cv = new BigDecimal(cover);
        d.getLines().add(new ArticleLine(ITEM_ID, cv, cv));

        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(c));
        when(declarations.findByConsignment(c)).thenReturn(Optional.of(d));
    }

    private DamageClaim lodge(DamageType type, String amount) {
        return service.lodge(CFR, ITEM_ID, type, "dent", new BigDecimal(amount));
    }

    // ---------------- 12-hour window: vehicles

    @Test
    void vehicleClaimWithin12Hours_exteriorDamage_isApproved() {
        givenDelivered(ItemCategory.TWO_WHEELER, 11, false, "5000");
        assertThat(lodge(DamageType.EXTERIOR, "3000").getStatus()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void vehicleClaimExactlyAt12Hours_isStillAccepted() {
        givenDelivered(ItemCategory.FOUR_WHEELER, 12, false, "50000");
        assertThat(lodge(DamageType.EXTERIOR, "1000").getApprovedAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void vehicleClaimAfter12Hours_isRejected() {
        givenDelivered(ItemCategory.TWO_WHEELER, 13, false, "5000");
        assertThrows(ClaimRejectedException.class, () -> lodge(DamageType.EXTERIOR, "1000"));
    }

    @Test
    void vehicleInternalOrMechanicalDamage_isRejected() {
        givenDelivered(ItemCategory.FOUR_WHEELER, 2, false, "50000");
        assertThrows(ClaimRejectedException.class, () -> lodge(DamageType.INTERNAL_MECHANICAL, "1000"));
    }

    // ---------------- 72-hour window: household goods / cartons

    @Test
    void cartonClaimAt13Hours_isAcceptedBecauseWindowIs72Hours() {
        givenDelivered(ItemCategory.CARTON_BOX, 13, false, "5000");
        assertThat(lodge(DamageType.OTHER, "2000").getStatus()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void householdGoodsClaimAt71Hours_isAccepted_at73Hours_isRejected() {
        givenDelivered(ItemCategory.HOUSEHOLD_GOODS, 71, false, "8000");
        assertThat(lodge(DamageType.OTHER, "2000").getApprovedAmount()).isEqualByComparingTo("2000");

        givenDelivered(ItemCategory.HOUSEHOLD_GOODS, 73, false, "8000");
        assertThrows(ClaimRejectedException.class, () -> lodge(DamageType.OTHER, "2000"));
    }

    // ---------------- FOV / coverage rules

    @Test
    void ownerRiskShipment_claimRejectedAutomatically() {
        givenDelivered(ItemCategory.CARTON_BOX, 1, true, "5000");
        assertThrows(ClaimRejectedException.class, () -> lodge(DamageType.OTHER, "500"));
    }

    @Test
    void payoutIsCappedAtDeclaredCoverage() {
        givenDelivered(ItemCategory.CARTON_BOX, 1, false, "5000");
        DamageClaim c = lodge(DamageType.OTHER, "9000");
        assertThat(c.getApprovedAmount()).isEqualByComparingTo("5000");
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.APPROVED_CAPPED);
    }

    @Test
    void itemOmittedFromMasterArticleList_hasZeroCover_andIsIneligible() {
        givenDelivered(ItemCategory.CARTON_BOX, 1, false, "0");
        assertThrows(ClaimRejectedException.class, () -> lodge(DamageType.OTHER, "500"));
    }
}
