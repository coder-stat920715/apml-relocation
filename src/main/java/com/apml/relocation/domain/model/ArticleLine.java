package com.apml.relocation.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArticleLine {
    @Column(nullable = false)
    private Long surveyItemId;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal declaredValue;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal riskCoverageAvailed;
}
