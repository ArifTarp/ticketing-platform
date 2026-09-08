import { describe, expect, it } from "vitest";
import { decodeJwt, isJwtExpired } from "./jwt";

function makeToken(claims: Record<string, unknown>): string {
  const header = Buffer.from(JSON.stringify({ alg: "HS512" })).toString("base64url");
  const payload = Buffer.from(JSON.stringify(claims)).toString("base64url");
  return `${header}.${payload}.signature`;
}

describe("decodeJwt", () => {
  it("decodes the payload claims", () => {
    const token = makeToken({
      sub: "1",
      email: "user@example.com",
      roles: ["USER"],
      iat: 1000,
      exp: 2000,
    });

    expect(decodeJwt(token)).toEqual({
      sub: "1",
      email: "user@example.com",
      roles: ["USER"],
      iat: 1000,
      exp: 2000,
    });
  });

  it("returns null for a malformed token", () => {
    expect(decodeJwt("not-a-jwt")).toBeNull();
  });
});

describe("isJwtExpired", () => {
  it("is false for a token whose exp is in the future", () => {
    const token = makeToken({ sub: "1", email: "a@b.com", roles: [], exp: Math.floor(Date.now() / 1000) + 3600 });
    expect(isJwtExpired(token)).toBe(false);
  });

  it("is true for a token whose exp is in the past", () => {
    const token = makeToken({ sub: "1", email: "a@b.com", roles: [], exp: Math.floor(Date.now() / 1000) - 3600 });
    expect(isJwtExpired(token)).toBe(true);
  });

  it("is true for an undecodable token", () => {
    expect(isJwtExpired("garbage")).toBe(true);
  });
});
