package com.demo.ticketing.auth.web.dto;

/**
 * Response returned on successful register/login. Never carries the password or password hash.
 */
public record AuthResponse(String token) {
}
