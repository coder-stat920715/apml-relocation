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
