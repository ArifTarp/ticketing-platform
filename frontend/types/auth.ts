/** Mirrors services/auth's RegisterRequest DTO. */
export interface RegisterRequest {
  email: string;
  password: string;
}

/** Mirrors services/auth's LoginRequest DTO. */
export interface LoginRequest {
  email: string;
  password: string;
}

/** Mirrors services/auth's AuthResponse DTO — register/login return only a JWT, no refresh token. */
export interface AuthResponse {
  token: string;
}

/** Decoded JWT payload claims, per the gateway's JWT contract (sub/email/roles + iat/exp). */
export interface JwtClaims {
  sub: string;
  email: string;
  roles: string[];
  iat: number;
  exp: number;
}
