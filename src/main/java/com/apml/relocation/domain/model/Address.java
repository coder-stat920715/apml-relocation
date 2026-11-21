package com.apml.relocation.domain.model;

import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Structured address for consignor (origin) and consignee (destination). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    private String premiseOrRoomNo;
    private String buildingOrPgName;
    private String surveyOrPlotNo;
    private String locality;
    private String city;
    private String state;
    private String pincode;

    /** e.g. "Zolo Lakeview Paying Guest (PG), Room No 512, ..., Hyderabad, Telangana - 500032". */
    public String fullAddress() {
        List<String> parts = new ArrayList<>();
        for (String p : new String[]{buildingOrPgName, premiseOrRoomNo, surveyOrPlotNo, locality, city, state}) {
            if (p != null && !p.isBlank()) parts.add(p.trim());
        }
        String joined = String.join(", ", parts);
        return pincode == null || pincode.isBlank() ? joined : joined + " - " + pincode.trim();
    }
}
