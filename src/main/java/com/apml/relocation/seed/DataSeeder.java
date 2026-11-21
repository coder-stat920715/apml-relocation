package com.apml.relocation.seed;

import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.PaymentMode;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.dto.ConsignmentDto;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.service.ConsignmentService;
import com.apml.relocation.service.EnquiryService;
import com.apml.relocation.service.PaymentService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Sample scenario: Souptik Tat, Dhakuria (Kolkata) -> Gowlidoddy (Hyderabad). */
@Component
@ConditionalOnProperty(name = "apml.seed-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    public static final String ENQUIRY_NO = "EQKOL262708416";
    public static final String CFR_NO = "CFRKOL262706738";
    public static final String RECEIPT_NO = "ARVWB262707169";

    private final ConsignmentRepository consignments;
    private final EnquiryService enquiries;
    private final ConsignmentService consignmentService;
    private final PaymentService payments;
    private final Clock clock;

    @Override
    public void run(String... args) {
        if (consignments.existsByCfrNo(CFR_NO)) return;
        LocalDate today = LocalDate.now(clock);

        enquiries.create(new EnquiryDto.CreateRequest(
                ENQUIRY_NO, "Souptik Tat", "9830012345", "souptik.tat@example.com", null, true,
                "Kolkata", "Hyderabad",
                "Dhakuria, Kolkata, West Bengal 700031", "Gowlidoddy, Hyderabad, Telangana 500075",
                MovementType.HOUSEHOLD_TPT, 1500,
                List.of(
                        new EnquiryDto.ItemRequest(ItemCategory.TWO_WHEELER, "Hero Glamour XTEC Motorcycle", 1,
                                new BigDecimal("37.05"), true, new BigDecimal("5000.00"), new BigDecimal("5000.00")),
                        new EnquiryDto.ItemRequest(ItemCategory.CARTON_BOX, "Carton Box", 1,
                                new BigDecimal("3.92"), false, new BigDecimal("5000.00"), new BigDecimal("5000.00")))));
        enquiries.approve(ENQUIRY_NO);

        Consignment c = consignmentService.create(new ConsignmentDto.CreateRequest(
                ENQUIRY_NO, CFR_NO, today.plusDays(7), today.plusDays(8), new BigDecimal("7000.00"), false));

        payments.record(CFR_NO, RECEIPT_NO, new BigDecimal("700.00"), PaymentMode.UPI, "UPI-SEED-0001");
        log.info("Seeded {} (total INR {}), advance INR 700 received under {} - still below the 10% booking threshold of INR {}",
                CFR_NO, c.getTotalPayableAmount(), RECEIPT_NO, payments.requiredAdvance(c));
    }
}
