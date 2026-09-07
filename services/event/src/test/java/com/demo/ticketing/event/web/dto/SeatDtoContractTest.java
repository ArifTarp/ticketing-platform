package com.demo.ticketing.event.web.dto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-0001 guard, enforced at the type level so it fails fast without Docker: the event service's
 * seat-map DTOs must never grow an availability / sold / held field. Live seat state is owned by
 * the booking service and merged client-side by {@code seatId}.
 */
class SeatDtoContractTest {

    private static final List<String> FORBIDDEN_FIELD_NAMES = List.of(
            "status", "available", "availability", "sold", "held", "state", "reserved");

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }

    @Test
    void seatDtoExposesLayoutAndPricingOnly() {
        assertThat(componentNames(SeatDto.class))
                .containsExactly("seatId", "section", "row", "number", "seatCategory");
    }

    @Test
    void seatMapResponseCarriesNoAvailabilityConcept() {
        assertThat(componentNames(SeatMapResponse.class))
                .doesNotContainAnyElementsOf(FORBIDDEN_FIELD_NAMES);
        assertThat(componentNames(SeatCategoryDto.class))
                .doesNotContainAnyElementsOf(FORBIDDEN_FIELD_NAMES);
        assertThat(componentNames(SeatDto.class))
                .doesNotContainAnyElementsOf(FORBIDDEN_FIELD_NAMES);
    }
}
