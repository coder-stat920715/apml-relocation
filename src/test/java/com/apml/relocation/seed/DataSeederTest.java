package com.apml.relocation.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.repository.ConsignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class DataSeederTest {

    @Autowired DataSeeder seeder;
    @Autowired ConsignmentRepository consignments;

    @Test
    void sampleScenarioIsSeededOnStartup() {
        Consignment c = consignments.findByCfrNo(DataSeeder.CFR_NO).orElseThrow();
        assertThat(c.getStatus()).isEqualTo(ConsignmentStatus.READY_FOR_LOADING);
        assertThat(c.getTotalPayableAmount()).isEqualByComparingTo("7000.00");
        assertThat(c.getEnquiry().getEnquiryNo()).isEqualTo(DataSeeder.ENQUIRY_NO);
    }

    @Test
    void runningTheSeederAgainIsANoOp() {
        long before = consignments.count();
        seeder.run();
        assertThat(consignments.count()).isEqualTo(before);
    }
}
