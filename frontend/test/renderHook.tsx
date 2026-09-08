import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

declare global {
  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined;
}

// Required for React 19's `act()` to run synchronously outside a testing-library setup.
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

/**
 * Minimal `renderHook` substitute — this repo has no `@testing-library/react` dependency
 * (per CLAUDE.md's "don't pull in new frameworks or libraries silently"), so hook tests mount a
 * tiny host component directly with `react-dom/client` + `act` instead of adding a new test lib.
 */
export function renderHook<TResult>(useHook: () => TResult) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  let root: Root;
  const result: { current: TResult | undefined } = { current: undefined };

  function HookHost() {
    result.current = useHook();
    return null;
  }

  act(() => {
    root = createRoot(container);
    root.render(<HookHost />);
  });

  return {
    result,
    unmount: () => {
      act(() => {
        root.unmount();
      });
      container.remove();
    },
  };
}
