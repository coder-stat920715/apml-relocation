package com.apml.relocation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.enums.ConsignmentStatus;
import com.apml.relocation.domain.enums.ItemCategory;
import com.apml.relocation.domain.enums.MovementType;
import com.apml.relocation.domain.enums.PackingMaterial;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static com.apml.relocation.domain.enums.ConsignmentStatus.BOOKING_CONFIRMED;
import static com.apml.relocation.domain.enums.ConsignmentStatus.CANCELLED;
import static com.apml.relocation.domain.enums.ConsignmentStatus.CLOSED;
import static com.apml.relocation.domain.enums.ConsignmentStatus.DELIVERED;
import static com.apml.relocation.domain.enums.ConsignmentStatus.DOCUMENTS_VERIFIED;
import static com.apml.relocation.domain.enums.ConsignmentStatus.IN_TRANSIT;
import static com.apml.relocation.domain.enums.ConsignmentStatus.QUOTED;
import static com.apml.relocation.domain.enums.ConsignmentStatus.READY_FOR_LOADING;

class EnumsTest {

    @Test
    void transitionTableMatchesTheLifecycle() {
        Map<ConsignmentStatus, Set<ConsignmentStatus>> expected = Map.of(
                QUOTED, Set.of(BOOKING_CONFIRMED, CANCELLED),
                BOOKING_CONFIRMED, Set.of(DOCUMENTS_VERIFIED, CANCELLED),
                DOCUMENTS_VERIFIED, Set.of(READY_FOR_LOADING, CANCELLED),
                READY_FOR_LOADING, Set.of(IN_TRANSIT, CANCELLED),
                IN_TRANSIT, Set.of(DELIVERED),
                DELIVERED, Set.of(CLOSED),
                CLOSED, Set.of(),
                CANCELLED, Set.of());
        for (ConsignmentStatus from : ConsignmentStatus.values()) {
            for (ConsignmentStatus to : ConsignmentStatus.values()) {
                assertThat(from.canTransitionTo(to)).as("%s -> %s", from, to).isEqualTo(expected.get(from).contains(to));
            }
        }
    }

    @Test
    void cancellableAndEditableStatuses() {
        Set<ConsignmentStatus> cancellable = Set.of(QUOTED, BOOKING_CONFIRMED, DOCUMENTS_VERIFIED, READY_FOR_LOADING);
        Set<ConsignmentStatus> editable = Set.of(QUOTED, BOOKING_CONFIRMED, DOCUMENTS_VERIFIED);
        for (ConsignmentStatus s : ConsignmentStatus.values()) {
            assertThat(s.isCancellable()).as("cancellable %s", s).isEqualTo(cancellable.contains(s));
            assertThat(s.isDocumentEditable()).as("editable %s", s).isEqualTo(editable.contains(s));
        }
    }

    @Test
    void itemCategoryCarriesFovRateClaimWindowAndVehicleFlag() {
        assertThat(ItemCategory.TWO_WHEELER.getFovRatePercent()).isEqualByComparingTo("1.5");
        assertThat(ItemCategory.CARTON_BOX.getFovRatePercent()).isEqualByComparingTo("3.0");
        assertThat(ItemCategory.FOUR_WHEELER.getClaimWindow()).isEqualTo(Duration.ofHours(12));
        assertThat(ItemCategory.HOUSEHOLD_GOODS.getClaimWindow()).isEqualTo(Duration.ofHours(72));
        assertThat(ItemCategory.TWO_WHEELER.isVehicle()).isTrue();
        assertThat(ItemCategory.FOUR_WHEELER.isVehicle()).isTrue();
        assertThat(ItemCategory.CARTON_BOX.isVehicle()).isFalse();
        assertThat(ItemCategory.HOUSEHOLD_GOODS.isVehicle()).isFalse();
    }

    @Test
    void movementTypeEstimatesTransitDays() {
        assertThat(MovementType.FULL_TRUCK_LOAD.getKmPerDay()).isEqualTo(370);
        assertThat(MovementType.CUBES.getBufferDays()).isEqualTo(2);
        assertThat(MovementType.FULL_TRUCK_LOAD.estimateDays(1500)).isEqualTo(5);
        assertThat(MovementType.CUBES.estimateDays(1500)).isEqualTo(8);
        assertThat(MovementType.PART_LOAD_SHARED.estimateDays(1500)).isEqualTo(11);
        assertThat(MovementType.HOUSEHOLD_TPT.estimateDays(200)).isEqualTo(4);
        assertThat(MovementType.VEHICLE_ONLY.estimateDays(201)).isEqualTo(5);
    }

    @Test
    void onlyApmlOwnedMaterialIsReturnable() {
        assertThat(PackingMaterial.HYGIENE_PAD.isReturnable()).isTrue();
        assertThat(PackingMaterial.LED_BOX.isReturnable()).isTrue();
        assertThat(PackingMaterial.SOLID_BOX.isReturnable()).isTrue();
        assertThat(PackingMaterial.CARTON_BOX.isReturnable()).isFalse();
        assertThat(PackingMaterial.STRETCH_FILM.isReturnable()).isFalse();
        assertThat(PackingMaterial.FOAM.isReturnable()).isFalse();
        assertThat(PackingMaterial.CORRUGATED_ROLL.isReturnable()).isFalse();
        assertThat(PackingMaterial.AIR_BUBBLE.isReturnable()).isFalse();
    }
}
