package com.apml.relocation.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.apml.relocation.integration.ScenarioSupport;
import java.time.LocalDateTime;
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

/** Lookup, tracking timeline, scan ingestion, coordinator directory and POD endpoints over HTTP. */
@SpringBootTest
@ActiveProfiles("dev")
class TrackingControllerTest {

    private static final String TRACKING = "/api/v1/tracking/";
    private static final String SEEDED_DIGITAL = "GCKOL262708112";
    private static final String COMPLETE_POD = "{\"originalRcVerified\":true,\"originalInsuranceVerified\":true,"
            + "\"originalPucVerified\":true,\"consigneeIdVerified\":true,\"consigneeIdReference\":\"123456789012\","
            + "\"paymentReceiptsVerified\":true,\"unpackedAndInspectedBeforeSignOff\":true,\"podSignedBy\":\"Souptik Tat\"}";

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

    // ------------------------------------------------------------------ seeded sample shipment

    @Test
    void theSeededShipmentResolvesFromAllFourIdentifiers() throws Exception {
        List<String[]> references = List.of(new String[]{"EQKOL262708416", "ENQUIRY_NO"}, new String[]{"CFRKOL262706738", "CFR_NO"},
                new String[]{"1354001", "GCN_SERIAL_NO"}, new String[]{SEEDED_DIGITAL, "DIGITAL_GC_NO"});
        for (String[] reference : references) {
            mvc.perform(get(TRACKING + "lookup").param("reference", reference[0]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.matchedBy").value(reference[1]))
                    .andExpect(jsonPath("$.identifiers.enquiryNo").value("EQKOL262708416"))
                    .andExpect(jsonPath("$.identifiers.cfrNo").value("CFRKOL262706738"))
                    .andExpect(jsonPath("$.identifiers.gcnSerialNo").value(1354001))
                    .andExpect(jsonPath("$.identifiers.digitalGcNo").value(SEEDED_DIGITAL))
                    .andExpect(jsonPath("$.shipment.status").value("IN_TRANSIT"))
                    .andExpect(jsonPath("$.shipment.totalPayableAmount").value(7000.0))
                    .andExpect(jsonPath("$.shipment.balanceDue").value(0.0))
                    .andExpect(jsonPath("$.receipts.length()").value(2))
                    .andExpect(jsonPath("$.coordinators.length()").value(3))
                    .andExpect(jsonPath("$.timeline.events.length()").value(4))
                    .andExpect(jsonPath("$.timeline.currentMilestone").value("IN_TRANSIT"));
        }
    }

    @Test
    void coordinatorPhoneNumbersAreMaskedInTheLookup() throws Exception {
        mvc.perform(get(TRACKING + "lookup").param("reference", "CFRKOL262706738"))
                .andExpect(jsonPath("$.coordinators[?(@.name=='Rahul Ji')].maskedPhoneNumber", hasItem("******0001")))
                .andExpect(jsonPath("$.coordinators[?(@.name=='Ashok Sharma Ji')].branchCode", hasItem("KOL")))
                .andExpect(jsonPath("$.coordinators[?(@.role=='BOOKING_AGENT')].name", hasItem("APM11879")));
    }

    @Test
    void theSeededTimelineShowsTheProgressBarAndEventTable() throws Exception {
        mvc.perform(get(TRACKING + SEEDED_DIGITAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.milestones.length()").value(5))
                .andExpect(jsonPath("$.milestones[2].milestone").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.milestones[2].current").value(true))
                .andExpect(jsonPath("$.milestones[3].reached").value(false))
                .andExpect(jsonPath("$.events[0].eventCode").value("BOOKED_AT_ORIGIN"))
                .andExpect(jsonPath("$.events[0].eventDescription").value("Consignment Booked at KOLKATTA"))
                .andExpect(jsonPath("$.events[3].eventDescription").value("On the way KOLKATTA HUB To HYDERABAD HUB"))
                .andExpect(jsonPath("$.events[3].truckVehicleNo").value("WB03D1234"));
    }

    @Test
    void unknownReferencesReturnProblemDetails() throws Exception {
        mvc.perform(get(TRACKING + "lookup").param("reference", "NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get(TRACKING + "GCKOL260000000")).andExpect(status().isNotFound());
        mvc.perform(get(TRACKING + "lookup")).andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ scan ingestion

    @Test
    void scansAreRecordedAndValidated() throws Exception {
        String digital = scenarios.digitalGcNoOf(scenarios.inTransit());
        send(post(TRACKING + digital + "/events"), "{\"eventCode\":\"BOOKED_AT_ORIGIN\",\"recordedByAgentId\":\"APM11879\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.milestone").value("IN_TRANSIT_ROUTE"))
                .andExpect(jsonPath("$.hubCity").value("KOLKATA"))
                .andExpect(jsonPath("$.eventDescription").value("Consignment Booked at KOLKATA"));
        send(post(TRACKING + digital + "/events"), "{\"eventCode\":\"PICKUP_SCHEDULED\"}").andExpect(status().is(422));
        send(post(TRACKING + digital + "/events"), "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.eventCode").exists());
        send(post(TRACKING + "GCKOL260000000/events"), "{\"eventCode\":\"BOOKED_AT_ORIGIN\"}").andExpect(status().isNotFound());
    }

    @Test
    void hubArrivalAndDeliveryScansDriveDemurrageAndTheClaimWindow() throws Exception {
        String cfr = scenarios.inTransit();
        String digital = scenarios.digitalGcNoOf(cfr);
        String arrived = LocalDateTime.now(ZoneId.of("Asia/Kolkata")).minusDays(4).withNano(0).toString();

        send(post(TRACKING + digital + "/events"), "{\"eventCode\":\"DELIVERED\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.violations", hasItem("A delivery POD record is required before delivery")));
        send(post(TRACKING + digital + "/events"), "{\"eventCode\":\"ARRIVED_DESTINATION_HUB\",\"eventTimestamp\":\"" + arrived + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hubCity").value("HYDERABAD HUB"));
        send(post("/api/v1/consignments/" + cfr + "/pod"), COMPLETE_POD).andExpect(status().isCreated())
                .andExpect(jsonPath("$.consigneeIdMaskedRef").value("XXXX-XXXX-9012"));
        send(post(TRACKING + digital + "/events"), "{\"eventCode\":\"DELIVERED\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.milestone").value("DELIVERED"));

        mvc.perform(get(TRACKING + "lookup").param("reference", digital))
                .andExpect(jsonPath("$.shipment.status").value("DELIVERED"))
                .andExpect(jsonPath("$.shipment.demurrageDays").value(3))
                .andExpect(jsonPath("$.shipment.demurrageAmount").value(30.0))
                .andExpect(jsonPath("$.shipment.vehicleClaimDeadline").exists())
                .andExpect(jsonPath("$.shipment.householdClaimDeadline").exists())
                .andExpect(jsonPath("$.timeline.currentMilestone").value("DELIVERED"));
    }

    // ------------------------------------------------------------------ delivery POD

    @Test
    void anIncompletePodBlocksTheDeliverEndpoint() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.inTransit();
        send(post(base + "/deliver"), "{\"podSignedBy\":\"Souptik Tat\",\"consigneeIdVerified\":true}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.violations", hasItem("Original RC book was not verified at handover")))
                .andExpect(jsonPath("$.violations", hasItem("Payment receipts were not verified before release")));
        mvc.perform(get(base)).andExpect(jsonPath("$.status").value("IN_TRANSIT"));
        send(post(base + "/deliver"), "{\"consigneeIdVerified\":true}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.podSignedBy").exists());
    }

    @Test
    void podCanBeRecordedAndReadBack() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.inTransit() + "/pod";
        mvc.perform(get(base)).andExpect(status().isNotFound());
        send(post(base), COMPLETE_POD).andExpect(status().isCreated()).andExpect(jsonPath("$.originalRcVerified").value(true));
        mvc.perform(get(base)).andExpect(status().isOk())
                .andExpect(jsonPath("$.podSignedBy").value("Souptik Tat"))
                .andExpect(jsonPath("$.consigneeIdMaskedRef").value("XXXX-XXXX-9012"));
    }

    @Test
    void podCannotBeRecordedBeforeTheConsignmentIsInTransit() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.readyWithGcn() + "/pod";
        send(post(base), COMPLETE_POD).andExpect(status().is(422));
    }

    // ------------------------------------------------------------------ coordinator directory

    @Test
    void coordinatorsAreAssignedUpdatedAndListedWithMaskedPhones() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.inTransit() + "/coordinators";
        send(post(base), "{\"role\":\"LINE_HAUL_SUPERVISOR\",\"name\":\"Supervisor One\",\"branchCode\":\"kol\","
                + "\"phoneNumber\":\"9811122233\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.branchCode").value("KOL"))
                .andExpect(jsonPath("$.maskedPhoneNumber").value("******2233"));
        send(post(base), "{\"role\":\"LINE_HAUL_SUPERVISOR\",\"name\":\"Supervisor One\",\"branchCode\":\"HYD\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.maskedPhoneNumber").doesNotExist());
        mvc.perform(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].branchCode").value("HYD"));
    }

    @Test
    void coordinatorInputIsValidated() throws Exception {
        String base = "/api/v1/consignments/" + scenarios.inTransit() + "/coordinators";
        send(post(base), "{\"role\":\"BOOKING_AGENT\",\"name\":\"A\",\"branchCode\":\"KOLK\",\"phoneNumber\":\"123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.branchCode").exists())
                .andExpect(jsonPath("$.errors.phoneNumber").exists());
    }
}
