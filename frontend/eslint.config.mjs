import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    rules: {
      // This app has no data-fetching library (React Query/SWR) per CLAUDE.md's "don't pull in
      // new frameworks or libraries silently" — components fetch via plain useEffect + useState
      // (EventList, event detail page, SessionProvider's localStorage hydration). That is exactly
      // the pattern this React-Compiler-oriented rule flags. Revisit if/when a fetching library is
      // deliberately introduced (e.g. for Phase 12's polling screens).
      "react-hooks/set-state-in-effect": "off",
    },
  },
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
]);

export default eslintConfig;
