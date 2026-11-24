package com.apml.relocation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.model.Address;
import org.junit.jupiter.api.Test;

class AddressTest {

    @Test
    void fullAddressJoinsAllPartsAndAppendsPincode() {
        Address a = new Address("Room No 512", "Zolo Lakeview Paying Guest (PG)", "Survey No 219 and 220, Plot 63",
                "Padma Sri Gardens Layout, Gowlidoddy", "Hyderabad", "Telangana", "500032");
        assertThat(a.fullAddress()).isEqualTo("Zolo Lakeview Paying Guest (PG), Room No 512, Survey No 219 and 220, Plot 63, "
                + "Padma Sri Gardens Layout, Gowlidoddy, Hyderabad, Telangana - 500032");
    }

    @Test
    void blankAndNullPartsAreSkipped() {
        Address a = new Address(null, " ", "", "Dhakuria", "Kolkata", "West Bengal", "700031");
        assertThat(a.fullAddress()).isEqualTo("Dhakuria, Kolkata, West Bengal - 700031");
    }

    @Test
    void missingOrBlankPincodeIsOmitted() {
        assertThat(new Address(null, null, null, null, "Kolkata", null, null).fullAddress()).isEqualTo("Kolkata");
        assertThat(new Address(null, null, null, null, "Kolkata", null, "  ").fullAddress()).isEqualTo("Kolkata");
    }
}
