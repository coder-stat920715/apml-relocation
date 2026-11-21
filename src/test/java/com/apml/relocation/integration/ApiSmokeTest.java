package com.apml.relocation.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** HTTP-level checks against the seeded Souptik Tat scenario and the RFC 9457 error contract. */
@SpringBootTest
@ActiveProfiles("dev")
class ApiSmokeTest {

    @Autowired WebApplicationContext context;
    MockMvc mvc;

    
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void seededConsignmentIsServedWithExpectedFigures() throws Exception {
        mvc.perform(get("/api/v1/consignments/CFRKOL262706738"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUOTED"))
                .andExpect(jsonPath("$.totalCft").value(40.97))
                .andExpect(jsonPath("$.fovChargeAmount").value(225.0))
                .andExpect(jsonPath("$.totalPayableAmount").value(7225.0))
                .andExpect(jsonPath("$.amountPaid").value(700.0))
                .andExpect(jsonPath("$.requiredAdvance").value(722.5));
    }

    @Test
    void seededAdvanceOf700IsBelowTenPercent_soBookingIsRefusedWithProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/consignments/CFRKOL262706738/confirm-booking"))
                .andExpect(status().isPaymentRequired())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.shortfall").value(22.5));
    }

    @Test
    void unknownConsignmentReturns404ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/consignments/DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://apml.example.com/problems/resource-not-found"));
    }

    @Test
    void invalidEnquiryReturns400WithFieldErrors() throws Exception {
        // Structurally complete JSON (all primitives present) so that Bean Validation, not JSON parsing, rejects it.
        String body = """
                {"customerName":"","mobile":"123","gstExempted":false,"originCity":"Kolkata",
                 "destinationCity":"Hyderabad","pickupAddress":"a","deliveryAddress":"b",
                 "movementType":"HOUSEHOLD_TPT","distanceKm":100,"items":[]}
                """;
        mvc.perform(post("/api/v1/enquiries").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors.customerName").exists())
                .andExpect(jsonPath("$.errors.mobile").exists())
                .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        mvc.perform(post("/api/v1/enquiries").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://apml.example.com/problems/malformed-request"));
    }

    @Test
    void kycNumbersAreMaskedInResponses() throws Exception {
        mvc.perform(put("/api/v1/consignments/CFRKOL262706738/kyc").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AADHAAR\",\"documentNumber\":\"123456789012\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedDocumentNumber").value("********9012"));
    }
}