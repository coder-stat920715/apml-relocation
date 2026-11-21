package com.apml.relocation.domain.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "master_article_declaration")
@Getter
@Setter
@NoArgsConstructor
public class MasterArticleDeclaration extends AuditableEntity {

    @OneToOne(optional = false)
    @JoinColumn(name = "consignment_id", unique = true)
    private Consignment consignment;

    private String signedBy;
    private LocalDateTime signedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "master_article_line", joinColumns = @JoinColumn(name = "declaration_id"))
    private List<ArticleLine> lines = new ArrayList<>();

    /** Cover declared for the item; items omitted from the final inventory have zero cover. */
    public BigDecimal coverageFor(Long surveyItemId) {
        return lines.stream()
                .filter(l -> l.getSurveyItemId().equals(surveyItemId))
                .map(ArticleLine::getRiskCoverageAvailed)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
