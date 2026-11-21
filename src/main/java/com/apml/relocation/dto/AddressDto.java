package com.apml.relocation.dto;

import com.apml.relocation.domain.model.Address;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Structured address; each part has room for long Indian addresses (PGs, survey/plot numbers) without truncation. */
public record AddressDto(
        @Size(max = 255) String premiseOrRoomNo,
        @Size(max = 255) String buildingOrPgName,
        @Size(max = 255) String surveyOrPlotNo,
        @Size(max = 255) String locality,
        @NotBlank @Size(max = 255) String city,
        @NotBlank @Size(max = 255) String state,
        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit PIN code") String pincode) {

    public Address toDomain() {
        return new Address(premiseOrRoomNo, buildingOrPgName, surveyOrPlotNo, locality, city, state, pincode);
    }

    public static AddressDto from(Address a) {
        return a == null ? null : new AddressDto(a.getPremiseOrRoomNo(), a.getBuildingOrPgName(), a.getSurveyOrPlotNo(),
                a.getLocality(), a.getCity(), a.getState(), a.getPincode());
    }

    public String fullAddress() { return toDomain().fullAddress(); }
}
