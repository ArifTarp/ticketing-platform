import { API_BASE_URL, TOKEN_STORAGE_KEY } from "./constants";

/** Shape of an RFC 7807 application/problem+json error body. */
export interface ApiError {
  status: number;
  title: string;
  detail: string;
  type?: string;
  instance?: string;
}

/** Thrown by apiFetch on any non-2xx response, carrying the parsed problem+json fields. */
export class ApiRequestError extends Error implements ApiError {
  status: number;
  title: string;
  detail: string;
  type?: string;
  instance?: string;

  constructor(error: ApiError) {
    super(error.detail || error.title);
    this.name = "ApiRequestError";
    this.status = error.status;
    this.title = error.title;
    this.detail = error.detail;
    this.type = error.type;
    this.instance = error.instance;
  }
}

function getStoredToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  return window.localStorage.getItem(TOKEN_STORAGE_KEY);
}

export interface ApiFetchOptions extends Omit<RequestInit, "body"> {
  /** Plain object body — JSON-stringified automatically. Use RequestInit's body directly if not JSON. */
  body?: unknown;
}

const JSON_CONTENT_TYPES = ["application/json", "application/problem+json"];

function isJsonResponse(response: Response): boolean {
  const contentType = response.headers.get("content-type") ?? "";
  return JSON_CONTENT_TYPES.some((type) => contentType.includes(type));
}

/**
 * Shared fetch wrapper for every call to the gateway (http://localhost:8080 by default,
 * NEXT_PUBLIC_API_BASE_URL to override). Attaches `Authorization: Bearer <token>` when a
 * session exists (per SessionProvider's localStorage-backed token) and parses RFC 7807
 * application/problem+json error bodies into a typed ApiRequestError.
 */
export async function apiFetch<T>(
  path: string,
  options: ApiFetchOptions = {},
): Promise<T> {
  const { body, headers, ...rest } = options;
  const token = getStoredToken();

  const finalHeaders = new Headers(headers);
  finalHeaders.set("Accept", "application/json");
  if (body !== undefined) {
    finalHeaders.set("Content-Type", "application/json");
  }
  if (token) {
    finalHeaders.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...rest,
    headers: finalHeaders,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const payload = isJsonResponse(response)
    ? await response.json().catch(() => null)
    : null;

  if (!response.ok) {
    if (payload && typeof payload === "object") {
      const problem = payload as Partial<ApiError>;
      throw new ApiRequestError({
        status: problem.status ?? response.status,
        title: problem.title ?? response.statusText,
        detail: problem.detail ?? "Something went wrong. Please try again.",
        type: problem.type,
        instance: problem.instance,
      });
    }
    throw new ApiRequestError({
      status: response.status,
      title: response.statusText,
      detail: "Something went wrong. Please try again.",
    });
  }

  return payload as T;
}
