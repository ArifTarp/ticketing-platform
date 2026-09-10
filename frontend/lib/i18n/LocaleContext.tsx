"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import en from "./en.json";
import tr from "./tr.json";

/** Supported UI locales — Turkish first, since this is a Turkish-market demo (root CLAUDE.md). */
export type Locale = "tr" | "en";

export const DEFAULT_LOCALE: Locale = "tr";

/** localStorage key the chosen locale is persisted under, mirroring SessionProvider's pattern. */
const LOCALE_STORAGE_KEY = "ticketing_locale";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Dictionary = Record<string, any>;

const DICTIONARIES: Record<Locale, Dictionary> = { tr, en };

/** Resolves a dot-path key ("nav.events") against a nested dictionary. */
function resolveKey(dictionary: Dictionary, key: string): string | undefined {
  const value = key
    .split(".")
    .reduce<unknown>(
      (node, segment) =>
        node && typeof node === "object" ? (node as Dictionary)[segment] : undefined,
      dictionary,
    );
  return typeof value === "string" ? value : undefined;
}

/** Replaces `{param}` placeholders in a translated string with values from `params`. */
function interpolate(template: string, params?: Record<string, string | number>): string {
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, token: string) =>
    token in params ? String(params[token]) : match,
  );
}

export interface LocaleContextValue {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  /** Translates a dot-path key (e.g. "nav.events"), optionally interpolating `{param}` tokens.
   *  Falls back to the other locale's string, then to the raw key, if a key is missing. */
  t: (key: string, params?: Record<string, string | number>) => string;
}

const LocaleContext = createContext<LocaleContextValue | undefined>(undefined);

/**
 * Lightweight TR/EN i18n (no next-intl, no URL locale prefix — deliberately scoped small).
 * Persists the chosen locale to localStorage; defaults to "tr".
 */
export function LocaleProvider({ children }: { children: ReactNode }) {
  const [locale, setLocaleState] = useState<Locale>(DEFAULT_LOCALE);

  useEffect(() => {
    const stored = window.localStorage.getItem(LOCALE_STORAGE_KEY);
    if (stored === "tr" || stored === "en") {
      setLocaleState(stored);
    }
  }, []);

  const setLocale = useCallback((next: Locale) => {
    window.localStorage.setItem(LOCALE_STORAGE_KEY, next);
    setLocaleState(next);
  }, []);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      const primary = resolveKey(DICTIONARIES[locale], key);
      const fallback = primary ?? resolveKey(DICTIONARIES[DEFAULT_LOCALE], key);
      return interpolate(fallback ?? key, params);
    },
    [locale],
  );

  const value = useMemo<LocaleContextValue>(() => ({ locale, setLocale, t }), [locale, setLocale, t]);

  return <LocaleContext.Provider value={value}>{children}</LocaleContext.Provider>;
}

/** Reads the current locale + translator — must be used within a LocaleProvider (app/layout.tsx). */
export function useLocale(): LocaleContextValue {
  const context = useContext(LocaleContext);
  if (!context) {
    throw new Error("useLocale must be used within a LocaleProvider");
  }
  return context;
}
