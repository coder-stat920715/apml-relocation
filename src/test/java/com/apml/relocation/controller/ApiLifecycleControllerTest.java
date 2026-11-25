package com.apml.relocation.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apml.relocation.integration.ScenarioSupport;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Drives every REST endpoint through one complete consignment lifecycle (enquiry to closed). */
@SpringBootTest
@ActiveProfiles("dev")
class ApiLifecycleControllerTest {

    private static final String BASE = "/api/v1/consignments/";

    @Autowired WebApplicationContext context;
    @Autowired ScenarioSupport scenarios;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private ResultActions send(MockHttpServletRequestBuilder builder, String json) throws Exception {
        return mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String bodyOf(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    private static long idOf(String enquiryJson, String category) {
        List<Number> ids = JsonPath.read(enquiryJson, "$.items[?(@.category=='" + category + "')].id");
        return ids.get(0).longValue();
    }

    @Test
    void completeLifecycleThroughEveryEndpoint() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        // ---- enquiry
        String enquiryBody = """
                {"customerName":"Lifecycle Customer","mobile":"9876543210","email":"l@example.com","gstExempted":true,
                 "originCity":"Kolkata","destinationCity":"Hyderabad","pickupAddress":"Dhakuria, Kolkata",
                 "deliveryAddress":"Gowlidoddy, Hyderabad","movementType":"HOUSEHOLD_TPT","distanceKm":1500,
                 "items":[{"category":"TWO_WHEELER","description":"Bike","quantity":1,"volumeCft":37.05,"crateRequired":true,
                           "declaredValue":5000,"riskCoverageAvailed":5000},
                          {"category":"CARTON_BOX","description":"Carton","quantity":1,"volumeCft":3.92,"crateRequired":false,
                           "declaredValue":5000,"riskCoverageAvailed":5000}],
                 "consignorAddress":{"locality":"Dhakuria","city":"Kolkata","state":"West Bengal","pincode":"700031"},
                 "consigneeAddress":{"premiseOrRoomNo":"Room 512","buildingOrPgName":"Zolo PG","city":"Hyderabad",
                                     "state":"Telangana","pincode":"500032"},
                 "consigneeName":"Lifecycle Customer","consigneeMobile":"9876543210"}
                """;
        String enquiry = bodyOf(send(post("/api/v1/enquiries"), enquiryBody).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.totalCft").value(40.97)));
        String enquiryNo = JsonPath.read(enquiry, "$.enquiryNo");
        long bikeId = idOf(enquiry, "TWO_WHEELER");
        long cartonId = idOf(enquiry, "CARTON_BOX");
        mvc.perform(get("/api/v1/enquiries/" + enquiryNo)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/enquiries/" + enquiryNo + "/approve")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // ---- quotation and advance
        String consignmentBody = "{\"enquiryNo\":\"" + enquiryNo + "\",\"packingDate\":\"" + today.plusDays(10)
                + "\",\"loadingDate\":\"" + today.plusDays(11) + "\",\"grossFreightAmount\":6775.00,\"ownerRisk\":false}";
        String quoted = bodyOf(send(post("/api/v1/consignments"), consignmentBody).andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPayableAmount").value(7000.0)));
        String base = BASE + JsonPath.read(quoted, "$.cfrNo");

        String receipt = bodyOf(send(post(base + "/payments"),
                "{\"amount\":700.00,\"mode\":\"UPI\",\"chequeOrTxnRef\":\"APMEB-1\",\"branchCode\":\"KOL\","
                        + "\"generatedByAgentId\":\"APM11879\"}").andExpect(status().isCreated()));
        String receiptNo = JsonPath.read(receipt, "$.receiptNo");
        mvc.perform(patch(base + "/payments/" + receiptNo + "/verify")).andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));
        mvc.perform(get(base + "/payments")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get(base + "/cancellation-quote")).andExpect(status().isOk())
                .andExpect(jsonPath("$.daysBeforePacking").value(10)).andExpect(jsonPath("$.refund").value(700.0));
        mvc.perform(post(base + "/confirm-booking")).andExpect(jsonPath("$.status").value("BOOKING_CONFIRMED"));

        // ---- compliance
        send(put(base + "/kyc"), "{\"type\":\"AADHAAR\",\"documentNumber\":\"123456789012\"}").andExpect(status().isOk());
        send(put(base + "/kyc"), "{\"type\":\"PAN\",\"documentNumber\":\"ABCDE1234F\"}").andExpect(status().isOk());
        send(put(base + "/kyc"), "{\"type\":\"PASSPORT_PHOTOS\",\"photoCount\":2}").andExpect(status().isOk());
        mvc.perform(get(base + "/kyc")).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get(base + "/vehicle-checklists")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get(base + "/compliance")).andExpect(jsonPath("$.compliant").value(false));
        send(put(base + "/vehicle-checklists/" + bikeId),
                "{\"rcBook\":true,\"validInsurance\":true,\"pucCertificate\":true,\"roadTaxReceipt\":true,\"fuelTankEmpty\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.fuelTankEmpty").value(true));
        send(post(base + "/master-article-list"), "{\"signedBy\":\"Lifecycle Customer\",\"lines\":["
                + "{\"surveyItemId\":" + bikeId + ",\"declaredValue\":5000,\"riskCoverageAvailed\":5000},"
                + "{\"surveyItemId\":" + cartonId + ",\"declaredValue\":5000,\"riskCoverageAvailed\":5000}]}")
                .andExpect(status().isOk());
        mvc.perform(get(base + "/master-article-list")).andExpect(jsonPath("$.lines", hasSize(2)));
        mvc.perform(get(base + "/compliance")).andExpect(jsonPath("$.compliant").value(true));
        mvc.perform(post(base + "/verify-documents")).andExpect(jsonPath("$.status").value("DOCUMENTS_VERIFIED"));

        // ---- balance, Goods Consignment Note, dispatch
        send(post(base + "/payments"), "{\"amount\":6300.00,\"mode\":\"NEFT_RTGS\",\"chequeOrTxnRef\":\"APMEB-2\"}")
                .andExpect(status().isCreated());
        mvc.perform(post(base + "/ready-for-loading")).andExpect(jsonPath("$.status").value("READY_FOR_LOADING"));
        send(post(base + "/gcn"), "{\"deliveryMode\":\"DOOR_DELIVERY\",\"totalPackages\":2,\"actualWeightKg\":44,\"chargedWeightKg\":100}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.paymentBillingType").value("PAID"));
        send(post(base + "/gcn"), "{\"unloadingBy\":\"BY_CARRIER\"}").andExpect(status().isOk());
        mvc.perform(get(base + "/gcn")).andExpect(jsonPath("$.liabilitySection").value("SECTION_11_INSURED_RISK"));
        mvc.perform(get(base + "/transit-estimate")).andExpect(jsonPath("$.estimatedTransitDays").value(11));
        send(patch(base + "/dispatch"), "{\"truckVehicleNo\":\"WB03D1234\",\"sealNo\":\"SEAL-77\"}")
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        // ---- transit, arrival, evidence, delivery
        send(post(base + "/transit-logs"), "{\"location\":\"Nagpur\",\"remarks\":\"Halt\"}").andExpect(status().isCreated());
        mvc.perform(get(base + "/transit-logs")).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(post(base + "/hub-arrival")).andExpect(status().isOk());
        send(post(base + "/media"), "{\"stage\":\"UNPACKED_AT_DELIVERY\",\"fileReference\":\"s3://x/unpacked.jpg\","
                + "\"surveyItemId\":" + bikeId + "}").andExpect(status().isCreated());
        mvc.perform(get(base + "/media")).andExpect(jsonPath("$", hasSize(1)));
        send(post(base + "/deliver"), "{\"originalRcVerified\":true,\"originalInsuranceVerified\":true,"
                + "\"originalPucVerified\":true,\"consigneeIdVerified\":true,\"consigneeIdReference\":\"123456789012\","
                + "\"paymentReceiptsVerified\":true,\"unpackedAndInspectedBeforeSignOff\":true,"
                + "\"podSignedBy\":\"Lifecycle Customer\",\"damageNotedOnPod\":false}")
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.demurrageAmount").value(0.0));

        // ---- claim and closure
        send(post(base + "/claims"), "{\"surveyItemId\":" + bikeId + ",\"damageType\":\"EXTERIOR\","
                + "\"description\":\"Scratch\",\"claimedAmount\":100}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.approvedAmount").value(100.0))
                .andExpect(jsonPath("$.baselineEvidenceCount").value(0)).andExpect(jsonPath("$.deliveryEvidenceCount").value(1));
        mvc.perform(get(base + "/claims")).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post(base + "/close")).andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void cancelAcceptsAnOptionalReason() throws Exception {
        String withReason = BASE + scenarios.quoted().getCfrNo();
        send(post(withReason + "/cancel"), "{\"reason\":\"Changed plans\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancellationCharge").value(0.0));
        mvc.perform(get(withReason + "/cancellation-quote")).andExpect(status().isConflict());

        String withoutBody = BASE + scenarios.quoted().getCfrNo();
        mvc.perform(post(withoutBody + "/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
