# APML Consignment & Relocation Management System

Java 21 · Spring Boot 4.1.2 · Spring Data JPA · PostgreSQL / H2 · SpringDoc

    mvn test                                  # unit + integration tests (H2)
    mvn spring-boot:run                       # dev profile: H2 + seeded sample scenario
    SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://host:5432/apml DB_USERNAME=.. DB_PASSWORD=.. mvn spring-boot:run

Swagger UI: http://localhost:8080/swagger-ui.html

## Architecture

    REST controllers (dto records, OpenAPI)  ->  GlobalExceptionHandler (RFC 9457 ProblemDetail)
              |
    services: Enquiry | Consignment (state machine) | Payment (10% / 100% gates, cancellation)
              | Compliance (KYC, vehicle, Master Article List) | Transit | Claim | Pricing
              |
    domain: entities (+auditing, @Version) | enums | CancellationPolicy (pure)
              |
    Spring Data repositories  ->  H2 (dev) / PostgreSQL (prod)

## Assumptions made where the brief was silent
- GST (non-exempt) = `apml.gst-rate` (default 5%) on freight + FOV; exempt customers pay 0.
- `volumeCft` is per unit; total CFT = sum(volume x quantity).
- HOUSEHOLD_TPT and VEHICLE_ONLY use the PART_LOAD (200 km/day + 3 days) estimate.
- Move/loading date must fall within 30 days of enquiry submission; quotation valid 15 days.
- Master Article List: "24 hours before packing" is measured to 00:00 of the packing date.
- Cancellation after the packing date is refused.
- Seed advance of INR 700 is below the 10% threshold (INR 722.50 on INR 7,225), so the seeded CFR stays QUOTED.
- Aadhaar/PAN numbers are stored as submitted; encrypt at rest before real use.

## Goods Consignment Note, dispatch, demurrage (v1.1)
- `POST  /api/v1/consignments/{cfr}/gcn` issue (201) / update (200) the GCN; `GET` same path to read it.
  Insured consignments (FOV cover > 0, not owner's risk) are forced to `SECTION_11_INSURED_RISK` with `declaredGoodsValue`.
- `PATCH /api/v1/consignments/{cfr}/dispatch` (legacy `POST` still works) with optional `truckVehicleNo`, `sealNo`, `cubeNo`.
  Gate: 100% paid + GCN with truck, seal, liability section, delivery mode and billing `PAID` (cube no. for CUBES).
- `POST /api/v1/consignments/{cfr}/hub-arrival` then `POST .../deliver`: delivery needs full verified payment proof;
  demurrage = max(5 x packages x extraDays, 5 x quintals x extraDays) after 1 free day (stored on the consignment,
  separate from `totalPayableAmount`).
- `POST/GET /api/v1/consignments/{cfr}/media` pre-packing / packed / delivery photo references (counted on damage claims).
- Receipts carry `chequeOrTxnRef`, `branchCode`, `generatedByAgentId`, `verified`; only verified receipts count as paid.
- Enquiries accept structured `consignorAddress` / `consigneeAddress`.
- Seed `CFRKOL262706738` is now READY_FOR_LOADING with receipts 700 + 6,300 and GCN S.No. 1354001.
- With `ddl-auto: update` on an existing PostgreSQL database, new columns are nullable; use Flyway/Liquibase for production.
