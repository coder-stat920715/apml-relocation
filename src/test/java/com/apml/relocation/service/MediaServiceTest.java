package com.apml.relocation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MediaStage;
import com.apml.relocation.domain.model.Consignment;
import com.apml.relocation.domain.model.InspectionMedia;
import com.apml.relocation.dto.MediaDto;
import com.apml.relocation.exception.BusinessRuleException;
import com.apml.relocation.exception.ResourceNotFoundException;
import com.apml.relocation.repository.ConsignmentRepository;
import com.apml.relocation.repository.InspectionMediaRepository;
import com.apml.relocation.support.TestData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MediaServiceTest {

    private static final String CFR = TestData.CFR;

    @Mock ConsignmentRepository consignments;
    @Mock InspectionMediaRepository media;

    private MediaService service;
    private Consignment consignment;

    @BeforeEach
    void setUp() {
        service = new MediaService(consignments, media, TestData.clock());
        consignment = TestData.consignment(ConsignmentStatus.QUOTED,
                TestData.enquiry(TestData.item(1, ItemCategory.TWO_WHEELER, "5000", "5000")));
        when(consignments.findByCfrNo(CFR)).thenReturn(Optional.of(consignment));
        when(media.save(any(InspectionMedia.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static MediaDto.Request request(MediaStage stage, Long itemId, java.time.LocalDateTime capturedAt) {
        return new MediaDto.Request(stage, "file://photo.jpg", itemId, capturedAt, "remark");
    }

    @Test
    void packingPhotosAreAcceptedBeforeDispatch() {
        InspectionMedia m = service.add(CFR, request(MediaStage.PRE_PACKING, null, null));
        assertThat(m.getStage()).isEqualTo(MediaStage.PRE_PACKING);
        assertThat(m.getSurveyItem()).isNull();
        assertThat(m.getCapturedAt()).isEqualTo(TestData.NOW);
        assertThat(m.getRemarks()).isEqualTo("remark");
    }

    @Test
    void packingPhotosAreRejectedAfterDispatch() {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        MediaDto.Request r = request(MediaStage.PACKED_AT_GODOWN, null, null);
        assertThrows(BusinessRuleException.class, () -> service.add(CFR, r));
    }

    @Test
    void deliveryPhotosAreAcceptedOnceInTransit() {
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        assertThat(service.add(CFR, request(MediaStage.UNPACKED_AT_DELIVERY, null, null)).getStage())
                .isEqualTo(MediaStage.UNPACKED_AT_DELIVERY);
    }

    @Test
    void deliveryPhotosAreRejectedBeforeDispatch() {
        MediaDto.Request r = request(MediaStage.UNPACKED_AT_DELIVERY, null, null);
        assertThrows(BusinessRuleException.class, () -> service.add(CFR, r));
    }

    @Test
    void photoCanBeLinkedToAnItemOfTheConsignment() {
        assertThat(service.add(CFR, request(MediaStage.PRE_PACKING, 1L, null)).getSurveyItem().getId()).isEqualTo(1L);
    }

    @Test
    void photoOfUnknownItemIsRejected() {
        MediaDto.Request r = request(MediaStage.PRE_PACKING, 99L, null);
        assertThrows(ResourceNotFoundException.class, () -> service.add(CFR, r));
    }

    @Test
    void captureTimeMustNotBeInTheFuture() {
        MediaDto.Request future = request(MediaStage.PRE_PACKING, null, TestData.NOW.plusMinutes(1));
        assertThrows(BusinessRuleException.class, () -> service.add(CFR, future));
        assertThat(service.add(CFR, request(MediaStage.PRE_PACKING, null, TestData.NOW.minusDays(1))).getCapturedAt())
                .isEqualTo(TestData.NOW.minusDays(1));
    }

    @Test
    void listReturnsEvidenceAndFailsForUnknownConsignment() {
        InspectionMedia m = new InspectionMedia();
        when(media.findByConsignmentOrderByCapturedAtAsc(consignment)).thenReturn(List.of(m));
        assertThat(service.list(CFR)).containsExactly(m);
        assertThrows(ResourceNotFoundException.class, () -> service.list("NOPE"));
    }
}
