import { describe, it, expect } from "vitest";
import { createRoot } from "react-dom/client";
import { act } from "react";
import { SeatMap } from "./SeatMap";
import { Seat } from "./Seat";
import type { SeatDto } from "@/types/event";
import { LocaleProvider } from "@/lib/i18n/LocaleContext";

// `Seat` is a `memo(...)` component object shaped `{ type: InnerFn, compare, $$typeof }`.
// Monkey-patching `.type` lets us count how many times the *inner* render function actually runs
// (i.e. React did NOT bail out of the memo comparison), without touching Seat.tsx's own code.
let renderCalls = 0;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const seatAsAny = Seat as any;
const originalSeatRender = seatAsAny.type;
seatAsAny.type = (...args: unknown[]) => {
  renderCalls += 1;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return (originalSeatRender as any)(...args);
};

function makeSeats(count: number): SeatDto[] {
  return Array.from({ length: count }, (_, i) => ({
    seatId: i + 1,
    section: "A",
    row: "1",
    number: i + 1,
    seatCategory: { id: 1, name: "Standard", price: 100 },
  }));
}

describe("SeatMap render cost (scratch measurement backing the perf-review finding)", () => {
  it("only re-renders the Seat(s) whose visual status actually changed, not the whole grid", () => {
    const seats = makeSeats(50);
    const container = document.createElement("div");
    document.body.appendChild(container);
    const root = createRoot(container);

    // Stable across renders, same as `useSeatSelection().toggleSeat` (useCallback([])) in the real
    // page -- an inline arrow here would itself defeat memo and invalidate the measurement.
    const stableOnToggleSeat = () => {};
    const stableAvailabilityMap = new Map();

    function Harness({ selectedSeatIds }: { selectedSeatIds: Set<number> }) {
      return (
        <LocaleProvider>
          <SeatMap
            seats={seats}
            availabilityBySeatId={stableAvailabilityMap}
            selectedSeatIds={selectedSeatIds}
            onToggleSeat={stableOnToggleSeat}
          />
        </LocaleProvider>
      );
    }

    act(() => {
      root.render(<Harness selectedSeatIds={new Set()} />);
    });
    expect(renderCalls).toBe(50); // initial mount: every seat renders once, as expected.

    renderCalls = 0;
    act(() => {
      // Simulate the page re-rendering because of unrelated state (e.g. checkoutError,
      // isCheckingOut) PLUS one seat's selection actually changing -- same object references for
      // `seats`/`onToggleSeat` as before, only `selectedSeatIds` differs.
      root.render(<Harness selectedSeatIds={new Set([1])} />);
    });

    console.log("Seat inner-render calls after toggling ONE seat out of 50:", renderCalls);
    expect(renderCalls).toBeLessThanOrEqual(2); // seat #1 (now SELECTED); memo bails on the rest.

    act(() => {
      root.unmount();
    });
    container.remove();
    seatAsAny.type = originalSeatRender;
  });
});
