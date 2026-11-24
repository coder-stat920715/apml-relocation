package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.EnquiryStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.model.Enquiry;
import com.apml.relocation.dto.AddressDto;
import com.apml.relocation.dto.EnquiryDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.DuplicateResourceException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.EnquiryRepository;
import com.apml.relocation.support.TestData;
import com.apml.relocation.util.ReferenceNumberGenerator;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnquiryServiceTest {

    @Mock EnquiryRepository enquiries;
    @Mock ReferenceNumberGenerator numbers;

    private EnquiryService service;

    @BeforeEach
    void setUp() {
        service = new EnquiryService(enquiries, numbers, TestData.clock());
        when(enquiries.save(any(Enquiry.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static EnquiryDto.CreateRequest request(String enquiryNo, AddressDto consignor, AddressDto consignee, BigDecimal cover) {
        return new EnquiryDto.CreateRequest(enquiryNo, "Souptik Tat", "9876543210", "s@example.com", null, true,
                "Kolkata", "Hyderabad", "pickup", "delivery", MovementType.HOUSEHOLD_TPT, 1500,
                List.of(new EnquiryDto.ItemRequest(ItemCategory.TWO_WHEELER, "Bike", 1, new BigDecimal("37.05"), true,
                        new BigDecimal("5000"), cover)),
                consignor, consignee, "Consignee", "9000000000");
    }

    @Test
    void createMapsEveryFieldAndStructuredAddresses() {
        AddressDto consignor = new AddressDto(null, null, null, "Dhakuria", "Kolkata", "West Bengal", "700031");
        AddressDto consignee = new AddressDto("Room 512", "PG", null, null, "Hyderabad", "Telangana", "500032");
        Enquiry e = service.create(request("EQKOL262708416", consignor, consignee, new BigDecimal("5000")));
        assertThat(e.getEnquiryNo()).isEqualTo("EQKOL262708416");
        assertThat(e.getSubmittedOn()).isEqualTo(TestData.NOW.toLocalDate());
        assertThat(e.getStatus()).isEqualTo(EnquiryStatus.SUBMITTED);
        assertThat(e.getConsigneeName()).isEqualTo("Consignee");
        assertThat(e.getConsigneeMobile()).isEqualTo("9000000000");
        assertThat(e.getConsignorAddress().getLocality()).isEqualTo("Dhakuria");
        assertThat(e.getConsigneeAddress().getPincode()).isEqualTo("500032");
        assertThat(e.getItems()).hasSize(1);
        assertThat(e.getItems().get(0).getRiskCoverageAvailed()).isEqualByComparingTo("5000");
        assertThat(e.getTotalCft()).isEqualByComparingTo("37.05");
    }

    @Test
    void createDefaultsMissingCoverageAndAddresses() {
        Enquiry e = service.create(request("EQKOL262708417", null, null, null));
        assertThat(e.getItems().get(0).getRiskCoverageAvailed()).isEqualByComparingTo("0");
        assertThat(e.getConsignorAddress()).isNull();
        assertThat(e.getConsigneeAddress()).isNull();
    }

    @Test
    void createGeneratesNumberWhenMissingAndRetriesOnCollision() {
        when(numbers.enquiryNo("Kolkata")).thenReturn("EQKOL260000001", "EQKOL260000002");
        when(enquiries.existsByEnquiryNo("EQKOL260000001")).thenReturn(true);
        assertThat(service.create(request(null, null, null, null)).getEnquiryNo()).isEqualTo("EQKOL260000002");
    }

    @Test
    void createTreatsBlankNumberAsMissing() {
        when(numbers.enquiryNo("Kolkata")).thenReturn("EQKOL260000003");
        assertThat(service.create(request(" ", null, null, null)).getEnquiryNo()).isEqualTo("EQKOL260000003");
    }

    @Test
    void createRejectsDuplicateSuppliedNumber() {
        when(enquiries.existsByEnquiryNo("EQKOL262708416")).thenReturn(true);
        EnquiryDto.CreateRequest duplicate = request("EQKOL262708416", null, null, null);
        assertThrows(DuplicateResourceException.class, () -> service.create(duplicate));
    }

    @Test
    void getReturnsEnquiryOrFails() {
        Enquiry e = TestData.enquiry();
        when(enquiries.findByEnquiryNo(TestData.ENQUIRY_NO)).thenReturn(Optional.of(e));
        assertThat(service.get(TestData.ENQUIRY_NO)).isSameAs(e);
        assertThrows(ResourceNotFoundException.class, () -> service.get("NOPE"));
    }

    @Test
    void approveMovesSubmittedEnquiryToApproved() {
        Enquiry e = TestData.enquiry();
        e.setStatus(EnquiryStatus.SUBMITTED);
        when(enquiries.findByEnquiryNo(TestData.ENQUIRY_NO)).thenReturn(Optional.of(e));
        assertThat(service.approve(TestData.ENQUIRY_NO).getStatus()).isEqualTo(EnquiryStatus.APPROVED);
    }

    @Test
    void approveRejectsNonSubmittedEnquiry() {
        Enquiry e = TestData.enquiry();
        e.setStatus(EnquiryStatus.APPROVED);
        when(enquiries.findByEnquiryNo(TestData.ENQUIRY_NO)).thenReturn(Optional.of(e));
        assertThrows(BusinessRuleException.class, () -> service.approve(TestData.ENQUIRY_NO));
    }

    @Test
    void approveRejectsExpiredEnquiry() {
        Enquiry e = TestData.enquiry();
        e.setStatus(EnquiryStatus.SUBMITTED);
        e.setSubmittedOn(TestData.NOW.toLocalDate().minusDays(16));
        when(enquiries.findByEnquiryNo(TestData.ENQUIRY_NO)).thenReturn(Optional.of(e));
        assertThrows(BusinessRuleException.class, () -> service.approve(TestData.ENQUIRY_NO));
    }

    @Test
    void approveAcceptsEnquiryOnItsLastValidDay() {
        Enquiry e = TestData.enquiry();
        e.setStatus(EnquiryStatus.SUBMITTED);
        e.setSubmittedOn(TestData.NOW.toLocalDate().minusDays(15));
        when(enquiries.findByEnquiryNo(TestData.ENQUIRY_NO)).thenReturn(Optional.of(e));
        assertThat(service.approve(TestData.ENQUIRY_NO).getStatus()).isEqualTo(EnquiryStatus.APPROVED);
    }
}
