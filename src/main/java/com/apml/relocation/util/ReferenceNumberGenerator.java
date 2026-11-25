package com.apml.relocation.util;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Generates APML style references: EQKOL262708416, CFRKOL262706738, ARVWB262707169. */
@Component
public class ReferenceNumberGenerator {

    private final Clock clock;

    public ReferenceNumberGenerator(Clock clock) { this.clock = clock; }

    public String enquiryNo(String originCity) { return "EQ" + cityCode(originCity) + suffix(); }

    public String cfrNo(String originCity) { return "CFR" + cityCode(originCity) + suffix(); }

    /** Digital GC prefix: GC + origin city code + financial year (FY 2026-27 -> 2627), e.g. GCKOL2627. */
    public String digitalGcPrefix(String originCity) {
        LocalDate today = LocalDate.now(clock);
        int fyStart = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        return "GC" + cityCode(originCity) + String.format("%02d%02d", fyStart % 100, (fyStart + 1) % 100);
    }

    public String receiptNo() { return "ARVWB" + suffix(); }

    private String suffix() {
        int yy = LocalDate.now(clock).getYear() % 100;
        return String.format("%02d%07d", yy, ThreadLocalRandom.current().nextInt(10_000_000));
    }

    private static String cityCode(String city) {
        String letters = city == null ? "" : city.replaceAll("[^A-Za-z]", "").toUpperCase();
        return (letters + "XXX").substring(0, 3);
    }
}
