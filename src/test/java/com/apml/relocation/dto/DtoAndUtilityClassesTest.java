package com.apml.relocation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.apml.relocation.domain.model.Address;
import com.apml.relocation.domain.model.CancellationPolicy;
import com.apml.relocation.domain.model.DemurrageCalculator;
import com.apml.relocation.mapper.ApmlMapper;
import com.apml.relocation.support.TestData;
import com.apml.relocation.util.MaskingUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DtoAndUtilityClassesTest {

    private static final List<Class<?>> HOLDERS = List.of(EnquiryDto.class, ConsignmentDto.class, PaymentDto.class,
            ComplianceDto.class, TransitDto.class, GcnDto.class, MediaDto.class);

    private static Object sample(Class<?> type) {
        Object value;
        if (type == String.class) {
            value = "x";
        } else if (type == boolean.class || type == Boolean.class) {
            value = Boolean.TRUE;
        } else if (type == int.class || type == Integer.class) {
            value = 1;
        } else if (type == long.class || type == Long.class) {
            value = 1L;
        } else if (type == BigDecimal.class) {
            value = BigDecimal.ONE;
        } else if (type == LocalDate.class) {
            value = TestData.NOW.toLocalDate();
        } else if (type == LocalDateTime.class) {
            value = TestData.NOW;
        } else if (type == List.class) {
            value = List.of();
        } else if (type == Map.class) {
            value = Map.of();
        } else if (type == Set.class) {
            value = Set.of();
        } else if (type.isEnum()) {
            value = type.getEnumConstants()[0];
        } else {
            value = build(type);
        }
        return value;
    }

    private static Object build(Class<?> recordType) {
        try {
            RecordComponent[] components = recordType.getRecordComponents();
            Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
            Object[] args = Arrays.stream(types).map(DtoAndUtilityClassesTest::sample).toArray();
            return recordType.getDeclaredConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void everyDtoRecordIsConstructibleAndValueBased() throws ReflectiveOperationException {
        List<Class<?>> records = Stream.concat(
                        HOLDERS.stream().flatMap(h -> Arrays.stream(h.getDeclaredClasses())).filter(Class::isRecord),
                        Stream.of(AddressDto.class, CancellationPolicy.Quote.class, DemurrageCalculator.Result.class))
                .toList();
        assertThat(records).hasSizeGreaterThan(25);
        for (Class<?> type : records) {
            Object a = build(type);
            Object b = build(type);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a.toString()).isNotEmpty();
            for (RecordComponent component : type.getRecordComponents()) {
                assertThat(component.getAccessor().invoke(a)).isNotNull();
            }
        }
    }

    @Test
    void addressDtoRoundTripsWithTheDomainAddress() {
        AddressDto dto = new AddressDto("Room 1", "PG", "Plot 2", "Locality", "Hyderabad", "Telangana", "500032");
        Address domain = dto.toDomain();
        assertThat(domain.getCity()).isEqualTo("Hyderabad");
        assertThat(AddressDto.from(domain)).isEqualTo(dto);
        assertThat(dto.fullAddress()).isEqualTo("PG, Room 1, Plot 2, Locality, Hyderabad, Telangana - 500032");
        assertThat(AddressDto.from(null)).isNull();
    }

    @Test
    void utilityAndHolderClassesCannotBeMisused() throws ReflectiveOperationException {
        List<Class<?>> utilities = Stream.concat(HOLDERS.stream(),
                Stream.of(MaskingUtil.class, CancellationPolicy.class, DemurrageCalculator.class, ApmlMapper.class)).toList();
        for (Class<?> type : utilities) {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            assertThat(constructor.newInstance()).isNotNull();
        }
    }
}
