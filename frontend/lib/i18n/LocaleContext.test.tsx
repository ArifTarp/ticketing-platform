import { act } from "react";
import { createRoot, type Root } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import { DEFAULT_LOCALE, LocaleProvider, useLocale, type LocaleContextValue } from "./LocaleContext";

declare global {
  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined;
}

// Required for React 19's `act()` to run synchronously outside a testing-library setup — same
// rationale as test/renderHook.tsx (no @testing-library/react in this repo).
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

const LOCALE_STORAGE_KEY = "ticketing_locale";

/** Mounts useLocale() inside a real LocaleProvider — useLocale throws outside one, so this can't
 *  reuse the bare test/renderHook.tsx helper (same rationale as that file: no @testing-library). */
function renderLocaleHook() {
  const container = document.createElement("div");
  document.body.appendChild(container);
  let root: Root;
  const result: { current: LocaleContextValue | undefined } = { current: undefined };

  function HookHost() {
    result.current = useLocale();
    return null;
  }

  act(() => {
    root = createRoot(container);
    root.render(
      <LocaleProvider>
        <HookHost />
      </LocaleProvider>,
    );
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

describe("LocaleContext", () => {
  afterEach(() => {
    window.localStorage.removeItem(LOCALE_STORAGE_KEY);
  });

  it("defaults to tr", () => {
    const { result, unmount } = renderLocaleHook();
    expect(result.current!.locale).toBe(DEFAULT_LOCALE);
    expect(result.current!.locale).toBe("tr");
    unmount();
  });

  it("translates a known key in both locales", () => {
    const { result, unmount } = renderLocaleHook();
    expect(result.current!.t("nav.events")).toBe("Etkinlikler");

    act(() => {
      result.current!.setLocale("en");
    });
    expect(result.current!.t("nav.events")).toBe("Events");
    unmount();
  });

  it("interpolates {param} tokens", () => {
    const { result, unmount } = renderLocaleHook();
    act(() => {
      result.current!.setLocale("en");
    });
    expect(result.current!.t("seats.section", { name: "A" })).toBe("Section A");
    unmount();
  });

  it("falls back to the raw key when it doesn't exist in either dictionary", () => {
    const { result, unmount } = renderLocaleHook();
    expect(result.current!.t("nonexistent.key")).toBe("nonexistent.key");
    unmount();
  });

  it("persists the chosen locale to localStorage and a fresh mount picks it up", () => {
    const first = renderLocaleHook();
    act(() => {
      first.result.current!.setLocale("en");
    });
    expect(window.localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("en");
    first.unmount();

    const second = renderLocaleHook();
    expect(second.result.current!.locale).toBe("en");
    second.unmount();
  });
});
