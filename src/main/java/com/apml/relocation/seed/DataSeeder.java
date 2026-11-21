package com.apml.relocation.seed;

import com.apml.relocation.domain.enums.DeliveryMode;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.enums.MediaStage;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.PackingMaterial;
import com.apml.relocation.domain.enums.PaymentBillingType;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.enums.UnloadingBy;
import com.apml.relocation.domain.model.ArticleLine;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.SurveyItem;
import com.apml.relocation.dto.AddressDto;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.dto.GcnDto;
import com.apml.relocation.dto.MediaDto;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.service.ComplianceService;
import com.apml.relocation.service.ConsignmentService;
import com.apml.relocation.service.EnquiryService;
import com.apml.relocation.service.GcnService;
import com.apml.relocation.service.MediaService;
import com.apml.relocation.service.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sample scenario: Souptik Tat, Dhakuria (Kolkata) -> Zolo Lakeview PG, Gowlidoddy (Hyderabad).
 * The consignment is walked to READY_FOR_LOADING with two receipts (INR 700 + INR 6,300 = INR 7,000) and GCN S.No. 1354001,
 * so it can be dispatched via PATCH /api/v1/consignments/CFRKOL262706738/dispatch.
 */
@Component
@ConditionalOnProperty(name = "apml.seed-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    public static final String ENQUIRY_NO = "EQKOL262708416";
    public static final String CFR_NO = "CFRKOL262706738";
    public static final String RECEIPT_1 = "ARVWB262707169";
    public static final String RECEIPT_2 = "ARVWB262707202";
    public static final long GCN_SERIAL_NO = 1354001L;

    private final ConsignmentRepository consignments;
    private final EnquiryService enquiries;
    private final ConsignmentService consignmentService;
    private final PaymentService payments;
    private final ComplianceService compliance;
    private final GcnService gcnService;
    private final MediaService mediaService;
    private final Clock clock;

    @Override
    public void run(String... args) {
        if (consignments.existsByCfrNo(CFR_NO)) return;
        LocalDate today = LocalDate.now(clock);

        AddressDto consignor = new AddressDto(null, null, null, "Dhakuria", "Kolkata", "West Bengal", "700031");
        AddressDto consignee = new AddressDto("Room No 512", "Zolo Lakeview Paying Guest (PG)", "Survey No 219 and 220, Plot 63",
                "Padma Sri Gardens Layout, Gowlidoddy", "Hyderabad", "Telangana", "500032");

        enquiries.create(new EnquiryDto.CreateRequest(
                ENQUIRY_NO, "Souptik Tat", "9830012345", "souptik.tat@example.com", null, true,
                "Kolkata", "Hyderabad", consignor.fullAddress(), consignee.fullAddress(),
                MovementType.HOUSEHOLD_TPT, 1500,
                List.of(
                        new EnquiryDto.ItemRequest(ItemCategory.TWO_WHEELER, "Hero Glamour XTEC Motorcycle", 1,
                                new BigDecimal("37.05"), true, new BigDecimal("5000.00"), new BigDecimal("5000.00")),
                        new EnquiryDto.ItemRequest(ItemCategory.CARTON_BOX, "Carton Box", 1,
                                new BigDecimal("3.92"), false, new BigDecimal("5000.00"), new BigDecimal("5000.00"))),
                consignor, consignee, "Souptik Tat", "9830012345"));
        enquiries.approve(ENQUIRY_NO);

        // FOV on INR 10,000 declared value is INR 225, so freight of INR 6,775 gives the INR 7,000 total on the receipts.
        Consignment c = consignmentService.create(new ConsignmentDto.CreateRequest(
                ENQUIRY_NO, CFR_NO, today.plusDays(7), today.plusDays(8), new BigDecimal("6775.00"), false));

        payments.record(CFR_NO, RECEIPT_1, new BigDecimal("700.00"), PaymentMode.UPI, "APMEB-5093071311", "KOL", "APM11879", true);
        consignmentService.confirmBooking(CFR_NO);

        SurveyItem bike = c.getEnquiry().getItems().stream().filter(i -> i.getCategory().isVehicle()).findFirst().orElseThrow();
        SurveyItem carton = c.getEnquiry().getItems().stream().filter(i -> !i.getCategory().isVehicle()).findFirst().orElseThrow();

        compliance.submitKyc(CFR_NO, KycType.AADHAAR, "999988887777", null);
        compliance.submitKyc(CFR_NO, KycType.PAN, "ABCDE1234F", null);
        compliance.submitKyc(CFR_NO, KycType.PASSPORT_PHOTOS, null, 2);
        compliance.updateVehicleChecklist(CFR_NO, bike.getId(), true, true, true, true, Boolean.TRUE, null);
        compliance.submitMasterArticle(CFR_NO, "Souptik Tat", Set.of(), List.of(
                new ArticleLine(bike.getId(), new BigDecimal("5000.00"), new BigDecimal("5000.00")),
                new ArticleLine(carton.getId(), new BigDecimal("5000.00"), new BigDecimal("5000.00"))));
        consignmentService.verifyDocuments(CFR_NO);

        payments.record(CFR_NO, RECEIPT_2, new BigDecimal("6300.00"), PaymentMode.NEFT_RTGS, "APMEB-9946469952", "KOL", "APM11879", true);
        consignmentService.markReadyForLoading(CFR_NO);

        gcnService.issue(CFR_NO, new GcnDto.IssueRequest(
                GCN_SERIAL_NO, CFR_NO, null, new BigDecimal("10000.00"), DeliveryMode.DOOR_DELIVERY, UnloadingBy.BY_CARRIER,
                PaymentBillingType.PAID, 2, new BigDecimal("44.00"), new BigDecimal("100.00"),
                new BigDecimal("6.00"), new BigDecimal("2.00"), new BigDecimal("3.50"),
                Map.of(PackingMaterial.CARTON_BOX, 1, PackingMaterial.STRETCH_FILM, 2, PackingMaterial.FOAM, 1,
                        PackingMaterial.CORRUGATED_ROLL, 1, PackingMaterial.AIR_BUBBLE, 1, PackingMaterial.HYGIENE_PAD, 1),
                null, null, null, null));

        mediaService.add(CFR_NO, new MediaDto.Request(MediaStage.PRE_PACKING, "seed://pre-packing/motorcycle-left.jpg",
                bike.getId(), null, "Existing minor scuff on left panel before packing"));
        mediaService.add(CFR_NO, new MediaDto.Request(MediaStage.PRE_PACKING, "seed://pre-packing/carton-box.jpg",
                carton.getId(), null, "Carton sealed by customer"));
        mediaService.add(CFR_NO, new MediaDto.Request(MediaStage.PACKED_AT_GODOWN, "seed://packed/motorcycle-wrapped.jpg",
                bike.getId(), null, "Wrapped with stretch film and foam"));
        mediaService.add(CFR_NO, new MediaDto.Request(MediaStage.PACKED_AT_GODOWN, "seed://packed/package-1-of-2.jpg",
                null, null, "Both packages at godown"));

        log.info("Seeded {}: READY_FOR_LOADING, receipts {} + {} = INR 7000, GCN S.No. {}", CFR_NO, RECEIPT_1, RECEIPT_2, GCN_SERIAL_NO);
    }
}