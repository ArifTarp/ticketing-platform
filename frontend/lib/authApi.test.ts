import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { loginUser, registerUser } from "./authApi";

describe("authApi", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("registerUser POSTs to /api/v1/auth/register with the request body", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ token: "jwt-token" }), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const result = await registerUser({ email: "user@example.com", password: "password123" });

    expect(result).toEqual({ token: "jwt-token" });
    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/auth/register");
    expect(init?.method).toBe("POST");
    expect(JSON.parse(init?.body as string)).toEqual({
      email: "user@example.com",
      password: "password123",
    });
  });

  it("loginUser POSTs to /api/v1/auth/login with the request body", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify({ token: "jwt-token" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    const result = await loginUser({ email: "user@example.com", password: "password123" });

    expect(result).toEqual({ token: "jwt-token" });
    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/auth/login");
    expect(init?.method).toBe("POST");
  });

  it("rejects with the parsed 401 problem+json on invalid credentials", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({ status: 401, title: "Unauthorized", detail: "Invalid email or password." }),
        { status: 401, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(loginUser({ email: "user@example.com", password: "wrong" })).rejects.toMatchObject({
      status: 401,
    });
  });

  it("rejects with the parsed 409 problem+json on duplicate email", async () => {
    vi.mocked(fetch).mockResolvedValue(
      new Response(
        JSON.stringify({
          status: 409,
          title: "Conflict",
          detail: "An account with this email already exists",
        }),
        { status: 409, headers: { "Content-Type": "application/problem+json" } },
      ),
    );

    await expect(
      registerUser({ email: "taken@example.com", password: "password123" }),
    ).rejects.toMatchObject({ status: 409 });
  });
});
