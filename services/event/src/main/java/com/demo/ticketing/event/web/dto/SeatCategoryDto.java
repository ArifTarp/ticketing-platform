package com.demo.ticketing.event.web.dto;

import java.math.BigDecimal;

/** A price tier as exposed to clients: name + price only (no section/seat internals). */
public record SeatCategoryDto(Long id, String name, BigDecimal price) {
}
