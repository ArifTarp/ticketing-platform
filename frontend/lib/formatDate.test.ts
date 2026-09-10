import { describe, expect, it } from "vitest";
import { formatEventDate } from "./formatDate";

describe("formatEventDate", () => {
  it("defaults to the tr-TR locale", () => {
    const formatted = formatEventDate("2026-10-08T03:41:05.870182Z");
    // tr-TR medium date style renders the month name in Turkish (e.g. "Eki" for October).
    expect(formatted).toMatch(/Eki/);
  });

  it("renders in en-US conventions when locale is 'en'", () => {
    const formatted = formatEventDate("2026-10-08T03:41:05.870182Z", "en");
    expect(formatted).toMatch(/Oct/);
  });

  it("always renders in the Europe/Istanbul timezone regardless of the host timezone", () => {
    // 2026-10-08T03:41:05Z is 06:41 in Europe/Istanbul (UTC+3 in October, no DST by then).
    const formatted = formatEventDate("2026-10-08T03:41:05.870182Z", "en");
    expect(formatted).toMatch(/6:41/);
  });
});
