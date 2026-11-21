package com.apml.relocation.domain.model;

import com.apml.relocation.domain.enums.MediaStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Photo evidence per stage; baseline for damage-claim adjudication. surveyItem is null for whole-consignment shots. */
@Entity
@Table(name = "inspection_media")
@Getter
@Setter
@NoArgsConstructor
public class InspectionMedia extends AuditableEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "consignment_id")
    private Consignment consignment;

    @ManyToOne
    @JoinColumn(name = "survey_item_id")
    private SurveyItem surveyItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private MediaStage stage;

    @Column(nullable = false, length = 1000)
    private String fileReference;

    @Column(nullable = false)
    private LocalDateTime capturedAt;

    @Column(length = 500)
    private String remarks;
}
