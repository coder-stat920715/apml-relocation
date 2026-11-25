package com.apml.relocation.integration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private static final String SEEDED = "/api/v1/consignments/CFRKOL262706738";
    private static final String PROBLEMS = "https://apml.example.com/problems/";
    private static final String POD_JSON = "{\"originalRcVerified\":true,\"originalInsuranceVerified\":true,"
            + "\"originalPucVerified\":true,\"consigneeIdVerified\":true,\"paymentReceiptsVerified\":true,"
            + "\"unpackedAndInspectedBeforeSignOff\":true,\"podSignedBy\":\"Souptik Tat\"}";

    @Autowired WebApplicationContext context;
    @Autowired ScenarioSupport scenarios;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // ------------------------------------------------------------------ seeded data

    @Test
    void seededConsignmentIsInTransitAndFullyPaid() throws Exception {
        mvc.perform(get(SEEDED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.totalCft").value(40.97))
                .andExpect(jsonPath("$.fovChargeAmount").value(225.0))
                .andExpect(jsonPath("$.totalPayableAmount").value(7000.0))
                .andExpect(jsonPath("$.amountPaid").value(7000.0))
                .andExpect(jsonPath("$.balanceDue").value(0.0));
    }

    @Test
    void seededMultiReceiptLedgerIsExposed() throws Exception {
        mvc.perform(get(SEEDED + "/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].receiptNo", hasItems("ARVWB262707169", "ARVWB262707202")))
                .andExpect(jsonPath("$[*].chequeOrTxnRef", hasItems("APMEB-5093071311", "APMEB-9946469952")))
                .andExpect(jsonPath("$[0].branchCode").value("KOL"))
                .andExpect(jsonPath("$[0].generatedByAgentId").value("APM11879"));
    }

    @Test
    void seededGcnCarriesSection11DoorDeliveryAndReturnableMaterial() throws Exception {
        mvc.perform(get(SEEDED + "/gcn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gcnSerialNo").value(1354001))
                .andExpect(jsonPath("$.cfrReferenceNo").value("CFRKOL262706738"))
                .andExpect(jsonPath("$.ibaCode").value("MUA-1783"))
                .andExpect(jsonPath("$.gstin").value("19AAFCA3559A1ZW"))
                .andExpect(jsonPath("$.sacCode").value("996511"))
                .andExpect(jsonPath("$.liabilitySection").value("SECTION_11_INSURED_RISK"))
                .andExpect(jsonPath("$.declaredGoodsValue").value(10000.0))
                .andExpect(jsonPath("$.deliveryMode").value("DOOR_DELIVERY"))
                .andExpect(jsonPath("$.paymentBillingType").value("PAID"))
                .andExpect(jsonPath("$.digitalGcNo").value("GCKOL262708112"))
                .andExpect(jsonPath("$.totalPackages").value(2))
                .andExpect(jsonPath("$.actualWeightKg").value(44.0))
                .andExpect(jsonPath("$.returnableMaterialsDueSameDay.HYGIENE_PAD").value(1));
    }

    @Test
    void seededEnquiryStoresTheFullConsigneeAddress() throws Exception {
        mvc.perform(get("/api/v1/enquiries/EQKOL262708416"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consigneeAddress.buildingOrPgName").value("Zolo Lakeview Paying Guest (PG)"))
                .andExpect(jsonPath("$.consigneeAddress.pincode").value("500032"))
                .andExpect(jsonPath("$.deliveryAddress").value("Zolo Lakeview Paying Guest (PG), Room No 512, "
                        + "Survey No 219 and 220, Plot 63, Padma Sri Gardens Layout, Gowlidoddy, Hyderabad, Telangana - 500032"));
    }

    @Test
    void seededInspectionMediaIsListed() throws Exception {
        mvc.perform(get(SEEDED + "/media"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].stage", hasItem("PRE_PACKING")))
                .andExpect(jsonPath("$[*].stage", hasItem("PACKED_AT_GODOWN")));
    }

    // ------------------------------------------------------------------ dispatch gate

    @Test
    void dispatchWithoutTruckAndSealIsRejected_forBothPatchAndLegacyPost() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.readyWithGcn();
        mvc.perform(patch(base + "/dispatch").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.violations", hasItems("truckVehicleNo is required", "sealNo is required")));
        mvc.perform(post(base + "/dispatch"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "compliance-violation"));
        mvc.perform(get(base)).andExpect(jsonPath("$.status").value("READY_FOR_LOADING"));
    }

    @Test
    void deliverBeforeDispatchIsAStateConflict() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.readyWithGcn();
        mvc.perform(post(base + "/deliver").contentType(MediaType.APPLICATION_JSON).content(POD_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-state-transition"));
    }

    @Test
    void deliverWithoutAPodIsRejectedWithViolations() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.inTransit();
        mvc.perform(post(base + "/deliver"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.violations", hasItem("A delivery POD record is required before delivery")));
        mvc.perform(get(base)).andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    void confirmingAnAlreadyAdvancedBookingIsAStateConflict() throws Exception {
        mvc.perform(post(SEEDED + "/confirm-booking")).andExpect(status().isConflict());
    }

    @Test
    void fullDispatchHubArrivalAndDeliveryFlowWithDemurrage() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.readyWithGcn();

        mvc.perform(patch(base + "/dispatch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"truckVehicleNo\":\"WB03D1234\",\"sealNo\":\"SEAL-5001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        String arrived = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusDays(4).withNano(0).toString();
        mvc.perform(post(base + "/hub-arrival").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"arrivedAt\":\"" + arrived + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.arrivedAtDestinationHubAt").exists());

        mvc.perform(post(base + "/deliver").contentType(MediaType.APPLICATION_JSON).content(POD_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.demurrageDays").value(3))
                .andExpect(jsonPath("$.demurrageAmount").value(30.0));
    }

    // ------------------------------------------------------------------ GCN validation & multi-receipt over HTTP

    @Test
    void gcnWithSection10OnAnInsuredConsignmentIsRejected() throws Exception {
        String cfr = scenarios.readyWithGcn();
        mvc.perform(post("/api/v1/consignments/" + cfr + "/gcn").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"liabilitySection\":\"SECTION_10_LIMITED_LIABILITY\",\"deliveryMode\":\"DOOR_DELIVERY\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "compliance-violation"));
    }

    @Test
    void gcnWithWrongCfrReferenceIsRejected() throws Exception {
        String cfr = scenarios.readyWithGcn();
        mvc.perform(post("/api/v1/consignments/" + cfr + "/gcn").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cfrReferenceNo\":\"CFRKOL260000000\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "business-rule-violation"));
    }

    @Test
    void gcnUpdateReturns200AndFirstIssueReturns201() throws Exception {
        String cfr = scenarios.readyWithGcn();
        mvc.perform(post("/api/v1/consignments/" + cfr + "/gcn").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unloadingBy\":\"BY_PARTY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unloadingBy").value("BY_PARTY"))
                .andExpect(jsonPath("$.liabilitySection").value("SECTION_11_INSURED_RISK"));
    }

    @Test
    void twoReceiptsOverHttpSettleTheBalance() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.quoted().getCfrNo();
        mvc.perform(post(base + "/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":700.00,\"mode\":\"UPI\",\"chequeOrTxnRef\":\"APMEB-5093071311\","
                                + "\"branchCode\":\"KOL\",\"generatedByAgentId\":\"APM11879\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified").value(true));
        mvc.perform(post(base + "/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":6300.00,\"mode\":\"NEFT_RTGS\",\"referenceNo\":\"APMEB-9946469952\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chequeOrTxnRef").value("APMEB-9946469952"));
        mvc.perform(get(base))
                .andExpect(jsonPath("$.amountPaid").value(7000.0))
                .andExpect(jsonPath("$.balanceDue").value(0.0));
        mvc.perform(post(base + "/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1.00,\"mode\":\"UPI\"}"))
                .andExpect(status().is(422));
    }

    @Test
    void mediaCanBeAttachedAndInvalidStageIsRejected() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.quoted().getCfrNo() + "/media";
        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"PRE_PACKING\",\"fileReference\":\"s3://bucket/pre.jpg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("PRE_PACKING"));
        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"UNPACKED_AT_DELIVERY\",\"fileReference\":\"s3://bucket/post.jpg\"}"))
                .andExpect(status().is(422));
        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content("{\"stage\":\"PRE_PACKING\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fileReference").exists());
    }

    // ------------------------------------------------------------------ error contract & masking

    @Test
    void unknownConsignmentReturns404ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/consignments/DOES-NOT-EXIST"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value(PROBLEMS + "resource-not-found"));
    }

    @Test
    void invalidEnquiryReturns400WithFieldErrors() throws Exception {
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
                .andExpect(jsonPath("$.type").value(PROBLEMS + "malformed-request"));
    }

    @Test
    void kycNumbersAreMaskedInResponses() throws Exception {
        String cfr = scenarios.quoted().getCfrNo();
        mvc.perform(put("/api/v1/consignments/" + cfr + "/kyc").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AADHAAR\",\"documentNumber\":\"123456789012\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedDocumentNumber").value("********9012"));
    }
}
