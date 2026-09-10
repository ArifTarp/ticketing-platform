import { describe, expect, it } from "vitest";
import { formatPrice } from "./formatPrice";

describe("formatPrice", () => {
  it("formats a decimal amount as Turkish lira (tr-TR, TRY)", () => {
    const formatted = formatPrice(120);
    // Intl output varies by ICU data (₺ vs "TRY", NBSP vs regular space), so assert on content
    // rather than an exact byte-for-byte string.
    expect(formatted).toMatch(/120,00/);
    expect(formatted).toMatch(/₺|TRY/);
  });

  it("formats zero", () => {
    expect(formatPrice(0)).toMatch(/0,00/);
  });
});
