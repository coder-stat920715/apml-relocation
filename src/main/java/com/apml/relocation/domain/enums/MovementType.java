package com.apml.relocation.domain.enums;

public enum MovementType {
    HOUSEHOLD_TPT(200, 3), VEHICLE_ONLY(200, 3), PART_LOAD_SHARED(200, 3), FULL_TRUCK_LOAD(370, 0), CUBES(250, 2);

    private final int kmPerDay;
    private final int bufferDays;

    MovementType(int kmPerDay, int bufferDays) { this.kmPerDay = kmPerDay; this.bufferDays = bufferDays; }

    public int getKmPerDay() { return kmPerDay; }
    public int getBufferDays() { return bufferDays; }

    /** FTL: km/370; CUBES: km/250 + 2; PART_LOAD (and HHG/vehicle default): km/200 + 3. */
    public int estimateDays(int distanceKm) { return Math.ceilDiv(distanceKm, kmPerDay) + bufferDays; }
}
