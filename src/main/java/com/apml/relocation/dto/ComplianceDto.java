package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.KycType;
import com.apml.relocation.domain.enums.RestrictedItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class ComplianceDto {
    private ComplianceDto() {}

    public record KycRequest(@NotNull KycType type, String documentNumber, Integer photoCount) {}

    /** Document numbers are always masked in responses. */
    public record KycResponse(KycType type, String maskedDocumentNumber, Integer photoCount, boolean verified) {}

    public record VehicleChecklistRequest(boolean rcBook, boolean validInsurance, boolean pucCertificate,
                                          boolean roadTaxReceipt, Boolean fuelTankEmpty,
                                          @DecimalMin("0.0") BigDecimal fuelLiters) {}

    public record VehicleChecklistResponse(Long surveyItemId, String description, ItemCategory vehicleType,
                                           boolean rcBook, boolean validInsurance, boolean pucCertificate,
                                           boolean roadTaxReceipt, Boolean fuelTankEmpty, BigDecimal fuelLiters) {}

    public record ArticleLineDto(@NotNull Long surveyItemId,
                                 @NotNull @DecimalMin("0.00") BigDecimal declaredValue,
                                 @NotNull @DecimalMin("0.00") BigDecimal riskCoverageAvailed) {}

    public record MasterArticleRequest(@NotBlank String signedBy, Set<RestrictedItem> restrictedItemsFound,
                                       @NotEmpty @Valid List<ArticleLineDto> lines) {}

    public record MasterArticleResponse(String signedBy, LocalDateTime signedAt, List<ArticleLineDto> lines) {}

    public record ComplianceStatusResponse(String cfrNo, boolean compliant, List<String> violations) {}
}
