package com.apml.relocation.dto;

import com.apml.relocation.domain.enums.MediaStage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class MediaDto {
    private MediaDto() {}

    public record Request(@NotNull MediaStage stage, @NotBlank @Size(max = 1000) String fileReference,
                          Long surveyItemId, LocalDateTime capturedAt, @Size(max = 500) String remarks) {}

    public record Response(Long mediaId, MediaStage stage, Long surveyItemId, String itemDescription,
                           String fileReference, LocalDateTime capturedAt, String remarks) {}
}
