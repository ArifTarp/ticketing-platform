import { apiFetch } from "./apiClient";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/types/auth";

/** POST /api/v1/auth/register — auto-logs in on success (backend issues a JWT on register too). */
export function registerUser(request: RegisterRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/v1/auth/register", {
    method: "POST",
    body: request,
  });
}

/** POST /api/v1/auth/login */
export function loginUser(request: LoginRequest): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/v1/auth/login", {
    method: "POST",
    body: request,
  });
}
