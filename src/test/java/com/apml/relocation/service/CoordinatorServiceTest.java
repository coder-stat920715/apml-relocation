package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.CoordinatorRole;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.CoordinatorContact;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.CoordinatorContactRepository;
import com.apml.relocation.support.TestData;
import java.util.ArrayList;
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
class CoordinatorServiceTest {

    private static final String CFR = TestData.CFR;

    @Mock CoordinatorContactRepository contacts;
    @Mock ConsignmentRepository consignments;

    private CoordinatorService service;
    private Consignment consignment;
    private final List<CoordinatorContact> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new CoordinatorService(contacts, consignments, TestData.clock());
        consignment = TestData.consignment(ConsignmentStatus.IN_TRANSIT,
                TestData.enquiry(TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000")));
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
        when(contacts.findByConsignmentOrderByAssignedAtAscIdAsc(consignment)).thenReturn(stored);
        when(contacts.save(any(CoordinatorContact.class))).thenAnswer(i -> {
            CoordinatorContact saved = i.getArgument(0);
            if (!stored.contains(saved)) {
                stored.add(saved);
            }
            return saved;
        });
    }

    @Test
    void assignCreatesATrimmedNormalisedContact() {
        CoordinatorContact c = service.assign(CFR, CoordinatorRole.ORIGIN_HUB_COORDINATOR, "  Rahul Ji ", " kol ", "9000000001");
        assertThat(c.getName()).isEqualTo("Rahul Ji");
        assertThat(c.getBranchCode()).isEqualTo("KOL");
        assertThat(c.getPhoneNumber()).isEqualTo("9000000001");
        assertThat(c.getRole()).isEqualTo(CoordinatorRole.ORIGIN_HUB_COORDINATOR);
        assertThat(c.getAssignedAt()).isEqualTo(TestData.NOW);
        assertThat(c.getConsignment()).isSameAs(consignment);
    }

    @Test
    void assigningTheSameRoleAndNameUpdatesTheExistingContact() {
        CoordinatorContact first = service.assign(CFR, CoordinatorRole.ORIGIN_HUB_COORDINATOR, "Rahul Ji", "KOL", "9000000001");
        CoordinatorContact second = service.assign(CFR, CoordinatorRole.ORIGIN_HUB_COORDINATOR, "RAHUL JI", "HYD", null);
        assertThat(second).isSameAs(first);
        assertThat(stored).hasSize(1);
        assertThat(second.getBranchCode()).isEqualTo("HYD");
        assertThat(second.getPhoneNumber()).isNull();
    }

    @Test
    void sameRoleWithADifferentNameAddsAnotherPerson() {
        service.assign(CFR, CoordinatorRole.ORIGIN_HUB_COORDINATOR, "Rahul Ji", "KOL", null);
        service.assign(CFR, CoordinatorRole.ORIGIN_HUB_COORDINATOR, "Ashok Sharma Ji", "KOL", null);
        assertThat(stored).hasSize(2);
    }

    @Test
    void sameNameInADifferentRoleAddsAnotherEntry() {
        service.assign(CFR, CoordinatorRole.BOOKING_AGENT, "APM11879", "KOL", null);
        service.assign(CFR, CoordinatorRole.LINE_HAUL_SUPERVISOR, "APM11879", "KOL", null);
        assertThat(stored).hasSize(2);
    }

    @Test
    void closedAndCancelledConsignmentsRejectChanges() {
        consignment.setStatus(ConsignmentStatus.CANCELLED);
        assertThrows(BusinessRuleException.class, () -> service.assign(CFR, CoordinatorRole.BOOKING_AGENT, "A", "KOL", null));
        consignment.setStatus(ConsignmentStatus.CLOSED);
        assertThrows(BusinessRuleException.class, () -> service.assign(CFR, CoordinatorRole.BOOKING_AGENT, "A", "KOL", null));
    }

    @Test
    void unknownConsignmentIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.assign("NOPE", CoordinatorRole.BOOKING_AGENT, "A", "KOL", null));
        assertThrows(ResourceNotFoundException.class, () -> service.list("NOPE"));
    }

    @Test
    void listReturnsContactsByCfrOrConsignment() {
        service.assign(CFR, CoordinatorRole.BOOKING_AGENT, "APM11879", "KOL", null);
        assertThat(service.list(CFR)).hasSize(1);
        assertThat(service.list(consignment)).hasSize(1);
    }
}
