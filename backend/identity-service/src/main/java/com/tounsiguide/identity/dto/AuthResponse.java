package com.tounsiguide.identity.dto;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                           UserResponse user) {
}
