import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { apiFetch, ApiRequestError } from "./apiClient";
import { TOKEN_STORAGE_KEY } from "./constants";

describe("apiFetch", () => {
  beforeEach(() => {
    window.localStorage.clear();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns the parsed JSON body on a 2xx response", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ token: "abc" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const result = await apiFetch<{ token: string }>("/api/v1/auth/login");

    expect(result).toEqual({ token: "abc" });
  });

  it("resolves the request against NEXT_PUBLIC_API_BASE_URL's default (http://localhost:8080)", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("[]", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await apiFetch("/api/v1/events");

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/events");
  });

  it("attaches an Authorization header when a token is stored", async () => {
    window.localStorage.setItem(TOKEN_STORAGE_KEY, "jwt-token");
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await apiFetch("/api/v1/events");

    const [, init] = vi.mocked(fetch).mock.calls[0];
    const headers = init?.headers as Headers;
    expect(headers.get("Authorization")).toBe("Bearer jwt-token");
  });

  it("does not attach an Authorization header when no session exists", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("{}", { status: 200, headers: { "Content-Type": "application/json" } }),
    );

    await apiFetch("/api/v1/events");

    const [, init] = vi.mocked(fetch).mock.calls[0];
    const headers = init?.headers as Headers;
    expect(headers.has("Authorization")).toBe(false);
  });

  it("throws an ApiRequestError parsed from an application/problem+json body", async () => {
    function problemResponse() {
      return new Response(
        JSON.stringify({ status: 401, title: "Unauthorized", detail: "Invalid email or password." }),
        { status: 401, headers: { "Content-Type": "application/problem+json" } },
      );
    }
    vi.mocked(fetch).mockResolvedValueOnce(problemResponse());
    await expect(apiFetch("/api/v1/auth/login")).rejects.toBeInstanceOf(ApiRequestError);

    vi.mocked(fetch).mockResolvedValueOnce(problemResponse());
    await expect(apiFetch("/api/v1/auth/login")).rejects.toMatchObject({
      status: 401,
      title: "Unauthorized",
      detail: "Invalid email or password.",
    });
  });

  it("falls back to a generic message when the error body isn't JSON", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response("Internal Server Error", { status: 500, statusText: "Internal Server Error" }),
    );

    await expect(apiFetch("/api/v1/events")).rejects.toMatchObject({ status: 500 });
  });
});
