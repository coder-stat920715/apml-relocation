package com.apml.relocation.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.enums.ClaimStatus;
import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.DamageType;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.enums.MediaStage;
import com.apml.relocation.domain.enums.PackingMaterial;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.model.Address;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.DamageClaim;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.domain.model.GoodsConsignmentNote;
import com.apml.relocation.domain.model.InspectionMedia;
import com.apml.relocation.domain.model.KycDocument;
import com.apml.relocation.domain.model.MasterArticleDeclaration;
import com.apml.relocation.domain.model.MoneyReceipt;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.domain.model.TransitLog;
import com.apml.relocation.domain.model.VehicleChecklist;
import com.apml.relocation.dto.ComplianceDto;
import com.apml.relocation.support.TestData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ApmlMapperTest {

    private final SurveyItem bike = TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000");
    private final Enquiry enquiry = TestData.enquiry(bike);
    private final Consignment consignment = TestData.consignment(ConsignmentStatus.QUOTED, enquiry);

    @Test
    void enquiryWithoutStructuredAddressesMapsNullAddresses() {
        var response = ApmlMapper.toResponse(enquiry);
        assertThat(response.enquiryNo()).isEqualTo(TestData.ENQUIRY_NO);
        assertThat(response.consignorAddress()).isNull();
        assertThat(response.consigneeAddress()).isNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).category()).isEqualTo(ItemCategory.TWO_WHEELER);
        assertThat(response.totalCft()).isEqualByComparingTo("10.00");
    }

    @Test
    void enquiryWithStructuredAddressesMapsThem() {
        enquiry.setConsignorAddress(new Address(null, null, null, "Dhakuria", "Kolkata", "West Bengal", "700031"));
        enquiry.setConsigneeAddress(new Address("Room 512", "PG", null, null, "Hyderabad", "Telangana", "500032"));
        enquiry.setConsigneeName("Consignee");
        enquiry.setConsigneeMobile("9000000000");
        var response = ApmlMapper.toResponse(enquiry);
        assertThat(response.consignorAddress().city()).isEqualTo("Kolkata");
        assertThat(response.consigneeAddress().pincode()).isEqualTo("500032");
        assertThat(response.consigneeName()).isEqualTo("Consignee");
        assertThat(response.consigneeMobile()).isEqualTo("9000000000");
    }

    @Test
    void consignmentResponseCarriesPaymentPosition() {
        var response = ApmlMapper.toResponse(consignment, new BigDecimal("700.00"), new BigDecimal("700.00"));
        assertThat(response.cfrNo()).isEqualTo(TestData.CFR);
        assertThat(response.balanceDue()).isEqualByComparingTo("6300.00");
        assertThat(response.requiredAdvance()).isEqualByComparingTo("700.00");
        assertThat(response.totalCft()).isEqualByComparingTo("10.00");
        assertThat(response.demurrageAmount()).isNull();
    }

    @Test
    void receiptMapsAliasAndVerifiedFlag() {
        MoneyReceipt r = new MoneyReceipt();
        r.setConsignment(consignment);
        r.setReceiptNo("ARVWB262707169");
        r.setAmount(new BigDecimal("700"));
        r.setMode(PaymentMode.UPI);
        r.setChequeOrTxnRef("APMEB-1");
        r.setBranchCode("KOL");
        r.setGeneratedByAgentId("APM11879");
        r.setVerified(null);
        var unverified = ApmlMapper.toResponse(r);
        assertThat(unverified.verified()).isFalse();
        assertThat(unverified.referenceNo()).isEqualTo("APMEB-1").isEqualTo(unverified.chequeOrTxnRef());
        r.setVerified(true);
        assertThat(ApmlMapper.toResponse(r).verified()).isTrue();
    }

    @Test
    void kycResponseMasksTheDocumentNumber() {
        KycDocument d = new KycDocument();
        d.setType(KycType.AADHAAR);
        d.setDocumentNumber("123456789012");
        d.setVerified(true);
        assertThat(ApmlMapper.toResponse(d).maskedDocumentNumber()).isEqualTo("********9012");
    }

    @Test
    void vehicleChecklistAndMasterArticleMapping() {
        VehicleChecklist v = new VehicleChecklist();
        v.setSurveyItem(bike);
        v.setRcBook(true);
        v.setFuelTankEmpty(Boolean.TRUE);
        var checklist = ApmlMapper.toResponse(v);
        assertThat(checklist.surveyItemId()).isEqualTo(1L);
        assertThat(checklist.vehicleType()).isEqualTo(ItemCategory.TWO_WHEELER);
        assertThat(checklist.rcBook()).isTrue();

        MasterArticleDeclaration d = new MasterArticleDeclaration();
        d.setSignedBy("Customer");
        d.getLines().add(new ArticleLine(1L, new BigDecimal("5000"), new BigDecimal("4000")));
        var declaration = ApmlMapper.toResponse(d);
        assertThat(declaration.signedBy()).isEqualTo("Customer");
        assertThat(declaration.lines()).hasSize(1);

        ArticleLine line = ApmlMapper.toDomain(new ComplianceDto.ArticleLineDto(7L, BigDecimal.TEN, BigDecimal.ONE));
        assertThat(line.getSurveyItemId()).isEqualTo(7L);
        assertThat(line.getRiskCoverageAvailed()).isEqualByComparingTo("1");
    }

    @Test
    void transitLogAndClaimMapping() {
        TransitLog t = new TransitLog();
        t.setLocation("Nagpur");
        t.setRemarks("Halt");
        t.setStatusAtLog(ConsignmentStatus.IN_TRANSIT);
        assertThat(ApmlMapper.toResponse(t).location()).isEqualTo("Nagpur");

        DamageClaim c = new DamageClaim();
        c.setId(9L);
        c.setSurveyItem(bike);
        c.setDamageType(DamageType.EXTERIOR);
        c.setClaimedAmount(BigDecimal.TEN);
        c.setApprovedAmount(BigDecimal.TEN);
        c.setStatus(ClaimStatus.APPROVED);
        c.setBaselineEvidenceCount(2);
        c.setDeliveryEvidenceCount(1);
        var claim = ApmlMapper.toResponse(c);
        assertThat(claim.claimId()).isEqualTo(9L);
        assertThat(claim.baselineEvidenceCount()).isEqualTo(2);
    }

    @Test
    void gcnResponseListsOnlyReturnableMaterial() {
        GoodsConsignmentNote g = TestData.gcn(consignment);
        g.getPackingMaterialsUsed().put(PackingMaterial.CARTON_BOX, 1);
        g.getPackingMaterialsUsed().put(PackingMaterial.HYGIENE_PAD, 2);
        var response = ApmlMapper.toResponse(g);
        assertThat(response.cfrNo()).isEqualTo(TestData.CFR);
        assertThat(response.packingMaterialsUsed()).hasSize(2);
        assertThat(response.returnableMaterialsDueSameDay()).containsOnlyKeys(PackingMaterial.HYGIENE_PAD);
    }

    @Test
    void mediaResponseWithAndWithoutSurveyItem() {
        InspectionMedia m = new InspectionMedia();
        m.setId(3L);
        m.setStage(MediaStage.PRE_PACKING);
        m.setFileReference("file://a.jpg");
        var wholeConsignment = ApmlMapper.toResponse(m);
        assertThat(wholeConsignment.surveyItemId()).isNull();
        assertThat(wholeConsignment.itemDescription()).isNull();

        m.setSurveyItem(bike);
        var perItem = ApmlMapper.toResponse(m);
        assertThat(perItem.surveyItemId()).isEqualTo(1L);
        assertThat(perItem.itemDescription()).isEqualTo("Item 1");
    }
}
