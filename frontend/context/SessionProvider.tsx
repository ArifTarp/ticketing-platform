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
import { TOKEN_STORAGE_KEY } from "@/lib/constants";
import { decodeJwt } from "@/lib/jwt";

interface SessionUser {
  userId: string;
  email: string;
  roles: string[];
}

interface SessionContextValue {
  /** The raw JWT, or null when logged out. */
  token: string | null;
  /** Claims decoded from the token, or null when logged out. */
  user: SessionUser | null;
  isAuthenticated: boolean;
  /** True until the initial localStorage read (on mount) has completed. */
  isLoading: boolean;
  login: (token: string) => void;
  logout: () => void;
}

const SessionContext = createContext<SessionContextValue | undefined>(undefined);

/**
 * Persists the JWT to localStorage and mirrors it into React Context, per docs/user-flow.md's own
 * guidance to keep auth simple in the demo (no refresh-token flow, no httpOnly cookie).
 */
export function SessionProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const storedToken = window.localStorage.getItem(TOKEN_STORAGE_KEY);
    if (storedToken) {
      const claims = decodeJwt(storedToken);
      if (claims && claims.exp * 1000 > Date.now()) {
        setToken(storedToken);
      } else {
        window.localStorage.removeItem(TOKEN_STORAGE_KEY);
      }
    }
    setIsLoading(false);
  }, []);

  const login = useCallback((newToken: string) => {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, newToken);
    setToken(newToken);
  }, []);

  const logout = useCallback(() => {
    window.localStorage.removeItem(TOKEN_STORAGE_KEY);
    setToken(null);
  }, []);

  const user = useMemo<SessionUser | null>(() => {
    if (!token) {
      return null;
    }
    const claims = decodeJwt(token);
    return claims
      ? { userId: claims.sub, email: claims.email, roles: claims.roles ?? [] }
      : null;
  }, [token]);

  const value = useMemo<SessionContextValue>(
    () => ({
      token,
      user,
      isAuthenticated: Boolean(token),
      isLoading,
      login,
      logout,
    }),
    [token, user, isLoading, login, logout],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

/** Reads the current session — must be used within a SessionProvider (mounted in app/layout.tsx). */
export function useAuth(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useAuth must be used within a SessionProvider");
  }
  return context;
}
