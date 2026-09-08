import { describe, expect, it } from "vitest";
import { resolveSeatStatus, toAvailabilityMap } from "./seatMap";

describe("toAvailabilityMap", () => {
  it("indexes availability rows by seatId", () => {
    const map = toAvailabilityMap([
      { seatId: 1, status: "HELD" },
      { seatId: 2, status: "SOLD" },
    ]);

    expect(map.get(1)).toBe("HELD");
    expect(map.get(2)).toBe("SOLD");
    expect(map.get(3)).toBeUndefined();
  });
});

describe("resolveSeatStatus", () => {
  it("is AVAILABLE when the seat is absent from the availability map and not selected", () => {
    expect(resolveSeatStatus(1, new Map(), new Set())).toBe("AVAILABLE");
  });

  it("is HELD when the availability map says HELD", () => {
    const availability = new Map([[1, "HELD" as const]]);
    expect(resolveSeatStatus(1, availability, new Set())).toBe("HELD");
  });

  it("is SOLD when the availability map says SOLD", () => {
    const availability = new Map([[1, "SOLD" as const]]);
    expect(resolveSeatStatus(1, availability, new Set())).toBe("SOLD");
  });

  it("is SELECTED when the seat is in the current session's selection, overriding availability", () => {
    const availability = new Map([[1, "AVAILABLE" as const]]);
    expect(resolveSeatStatus(1, availability, new Set([1]))).toBe("SELECTED");
  });
});
